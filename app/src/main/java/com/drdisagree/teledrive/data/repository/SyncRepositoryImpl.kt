package com.drdisagree.teledrive.data.repository

import androidx.room.withTransaction
import com.drdisagree.teledrive.core.common.AppError
import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.core.common.SafeLog
import com.drdisagree.teledrive.core.crypto.KeyBackupCodec
import com.drdisagree.teledrive.core.files.MimeTypes
import com.drdisagree.teledrive.core.telegram.RemoteDocument
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramException
import com.drdisagree.teledrive.data.local.dao.FileDao
import com.drdisagree.teledrive.data.local.dao.FolderDao
import com.drdisagree.teledrive.data.local.database.TeleDriveDatabase
import com.drdisagree.teledrive.data.local.entity.FileEntity
import com.drdisagree.teledrive.data.remote.telegram.ManifestCodec
import com.drdisagree.teledrive.data.remote.telegram.RemoteFileManifest
import com.drdisagree.teledrive.data.remote.telegram.RemoteFolderState
import com.drdisagree.teledrive.domain.model.BackupState
import com.drdisagree.teledrive.domain.model.FileCategory
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import com.drdisagree.teledrive.domain.repository.SyncRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * Reconciles local metadata with the storage chat. The chat is the source of
 * truth for remote-backed files: caption manifests are decoded and upserted,
 * and local rows pointing at deleted messages are detached. After a complete
 * local wipe this rebuilds the whole drive, including the folder tree.
 */
@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val telegramClient: TelegramClient,
    private val fileDao: FileDao,
    private val manifestCodec: ManifestCodec,
    private val folderDao: FolderDao,
    private val folderPathResolver: FolderPathResolver,
    private val activeChannel: ActiveChannel,
    private val channelOwnership: ChannelOwnership,
    private val folderStateSynchronizer: FolderStateSynchronizer,
    private val settingsRepository: SettingsRepository,
    private val database: TeleDriveDatabase
) : SyncRepository {

    private val _syncing = MutableStateFlow(false)
    override val syncing: Flow<Boolean> = _syncing

    private val _indexedSoFar = MutableStateFlow(0)
    override val indexedSoFar: Flow<Int> = _indexedSoFar
    private val syncMutex = Mutex()

    override suspend fun fullResync(): AppResult<SyncRepository.SyncStats> =
        runSync(incremental = false)

    override suspend fun incrementalSync(): AppResult<SyncRepository.SyncStats> =
        runSync(incremental = true)

    override suspend fun syncOnStart(): AppResult<SyncRepository.SyncStats> {
        fileDao.repairBackedUpStates()
        return runSync(incremental = fileDao.fileCount(activeChannel.id()) > 0)
    }

    private suspend fun runSync(incremental: Boolean): AppResult<SyncRepository.SyncStats> =
        syncMutex.withLock {
            _syncing.value = true
            _indexedSoFar.value = 0
            try {
                doSync(incremental)
            } catch (e: TelegramException) {
                AppResult.Failure(
                    if (e.isRateLimit) AppError.RateLimited(e.retryAfterSeconds ?: 0)
                    else AppError.TelegramError(e.code, e.message)
                )
            } finally {
                _syncing.value = false
            }
        }

    private suspend fun forgetChat(chatId: Long) {
        val removed = fileDao.deleteRemoteOnlyInChat(chatId)
        val detached = fileDao.detachChat(chatId)
        SafeLog.d(TAG, "Storage chat changed: dropped $removed, detached $detached")
    }

    private suspend fun doSync(incremental: Boolean): AppResult<SyncRepository.SyncStats> {
        val prefs = settingsRepository.preferences.first()
        val chatId = telegramClient.ensureStorageChat(prefs.storageChatId)
        channelOwnership.claimUnowned(chatId)
        if (chatId != prefs.storageChatId) {
            prefs.storageChatId?.let { previous -> forgetChat(previous) }
            settingsRepository.update { it.copy(storageChatId = chatId) }
        }

        if (!incremental) {
            runCatching { folderStateSynchronizer.pull() }
                .onFailure { SafeLog.w(TAG, "Folder state pull failed", it) }
        }

        val folderCache = mutableMapOf<String, String?>()
        SafeLog.d(TAG, "Sync start chat=$chatId incremental=$incremental")
        var inserted = 0
        var updated = 0
        var locked = 0
        val seenMessageIds = mutableSetOf<Long>()
        var fromMessageId = 0L
        var pages = 0

        while (pages++ < MAX_PAGES) {
            val page = try {
                telegramClient.fetchDocuments(chatId, fromMessageId, PAGE_SIZE)
            } catch (e: TelegramException) {
                if (e.isRateLimit) {
                    delay((((e.retryAfterSeconds ?: 5) + 1) * 1000L).milliseconds)
                    continue
                }
                throw e
            }
            SafeLog.d(TAG, "Sync page ${page.documents.size} documents")
            if (page.documents.isEmpty() && page.nextFromMessageId == 0L) break

            var reachedKnown = false
            val known = existingForPage(page.documents)
            database.withTransaction {
                for (document in page.documents) {
                    seenMessageIds.add(document.messageId)
                    if (isInternalDocument(document)) continue
                    if (manifestCodec.isLocked(document.caption)) {
                        locked++
                        continue
                    }
                    if (incremental && !isNewToLocal(document, known)) {
                        reachedKnown = true
                        break
                    }
                    when (reconcile(document, folderCache, known)) {
                        ReconcileResult.INSERTED -> {
                            inserted++
                            _indexedSoFar.value = inserted
                        }

                        ReconcileResult.UPDATED -> updated++
                        ReconcileResult.UNCHANGED -> Unit
                    }
                }
            }
            if (reachedKnown) {
                return finishSync(
                    chatId, seenMessageIds, inserted, updated, locked, partial = true
                )
            }
            if (page.nextFromMessageId == 0L) break
            fromMessageId = page.nextFromMessageId
        }

        return finishSync(
            chatId, seenMessageIds, inserted, updated, locked, partial = incremental
        )
    }

    private suspend fun finishSync(
        chatId: Long,
        seenMessageIds: Set<Long>,
        inserted: Int,
        updated: Int,
        locked: Int,
        partial: Boolean
    ): AppResult<SyncRepository.SyncStats> {
        var detached = 0
        if (!partial && locked == 0) {
            val stale = fileDao.filesWithRemote().filter { entity ->
                val messageId = entity.messageId
                messageId != null && entity.chatId == chatId && messageId !in seenMessageIds
            }
            if (stale.isNotEmpty()) {
                database.withTransaction {
                    val (orphaned, localOnly) = stale.partition { it.localPath == null }
                    if (orphaned.isNotEmpty()) {
                        fileDao.deleteByIds(orphaned.map { it.id })
                    }
                    localOnly.forEach { entity -> fileDao.detachRemote(entity.id) }
                }
                detached = stale.size
            }
        }
        SafeLog.d(TAG, "Sync done: +$inserted ~$updated -$detached, $locked locked")
        return AppResult.Success(
            SyncRepository.SyncStats(inserted, updated, detached, locked)
        )
    }

    /** App-managed bookkeeping documents must never surface as user files. */
    private fun isInternalDocument(document: RemoteDocument): Boolean =
        document.fileName == RemoteFolderState.FILE_NAME ||
                document.fileName == KeyBackupCodec.BACKUP_FILE_NAME ||
                document.caption.startsWith(RemoteFolderState.MARKER) ||
                document.caption.startsWith(KEY_BACKUP_MARKER)

    private fun isNewToLocal(document: RemoteDocument, known: KnownRows): Boolean {
        val existing = known.byUniqueId[document.uniqueFileId] ?: return true
        return existing.messageId != document.messageId
    }

    private suspend fun existingForPage(documents: List<RemoteDocument>): KnownRows {
        val manifests = documents.associateWith { manifestCodec.decode(it.caption) }
        val fileIds = manifests.values.mapNotNull { it?.fileId }.distinct()
        val uniqueIds = documents.map { it.uniqueFileId }.distinct()
        val byId = if (fileIds.isEmpty()) {
            emptyMap()
        } else {
            fileDao.byIds(fileIds).associateBy { it.id }
        }
        val byUniqueId = if (uniqueIds.isEmpty()) {
            emptyMap()
        } else {
            fileDao.byRemoteUniqueIds(uniqueIds).mapNotNull { entity ->
                entity.remoteUniqueId?.let { it to entity }
            }.toMap()
        }
        return KnownRows(manifests, byId, byUniqueId)
    }

    private data class KnownRows(
        val manifests: Map<RemoteDocument, RemoteFileManifest?>,
        val byId: Map<String, FileEntity>,
        val byUniqueId: Map<String, FileEntity>
    )

    /**
     * Folder identity comes from the manifest id when it is known locally.
     * Paths are only a fallback, because two folders can share a name, and a
     * trashed file must never recreate the folder it was deleted from.
     */
    private suspend fun resolveFolder(
        manifest: RemoteFileManifest,
        cache: MutableMap<String, String?>
    ): String? {
        manifest.folderId?.let { id ->
            if (cache.containsValue(id)) return id
            if (folderPathResolver.exists(id)) {
                cache[manifest.folderPath] = id
                return id
            }
        }
        if (cache.containsKey(manifest.folderPath)) return cache[manifest.folderPath]
        val resolved = if (manifest.trashedAt != null) {
            folderPathResolver.resolveExisting(manifest.folderPath)
        } else {
            folderPathResolver.resolveOrCreate(manifest.folderPath)
        }
        cache[manifest.folderPath] = resolved
        return resolved
    }

    private suspend fun reconcile(
        document: RemoteDocument,
        folderCache: MutableMap<String, String?>,
        known: KnownRows
    ): ReconcileResult {
        val manifest = known.manifests[document]
        val existing = manifest?.let { known.byId[it.fileId] }
            ?: known.byUniqueId[document.uniqueFileId]

        val folderId = manifest?.let { resolveFolder(it, folderCache) } ?: existing?.folderId
        val folderTrashedAt = folderId?.let { folderDao.byId(it)?.trashedAt }

        val name = manifest?.name ?: existing?.name ?: document.fileName.ifBlank {
            "file-${document.messageId}"
        }
        val mime = manifest?.mimeType ?: document.mimeType.ifBlank {
            MimeTypes.fromFileName(name)
        }

        val now = System.currentTimeMillis()
        val entity = FileEntity(
            id = manifest?.fileId ?: existing?.id ?: UUID.randomUUID().toString(),
            folderId = if (manifest?.trashedAt != null || folderTrashedAt != null) {
                null
            } else {
                folderId
            },
            name = name,
            sizeBytes = manifest?.sizeBytes ?: document.sizeBytes,
            mimeType = mime,
            category = FileCategory.fromMimeType(mime),
            localPath = existing?.localPath,
            contentHash = manifest?.contentHash ?: existing?.contentHash,
            chatId = document.chatId,
            messageId = document.messageId,
            remoteFileId = document.remoteFileId,
            remoteUniqueId = document.uniqueFileId,
            backupState = BackupState.BACKED_UP,
            isHidden = manifest?.hidden ?: existing?.isHidden ?: false,
            isArchived = manifest?.archived ?: existing?.isArchived ?: false,
            isFavorite = manifest?.favorite ?: existing?.isFavorite ?: false,
            isEncrypted = manifest?.encrypted
                ?: manifestCodec.isEncryptedManifest(document.caption),
            width = manifest?.width ?: existing?.width,
            height = manifest?.height ?: existing?.height,
            durationMs = manifest?.durationMs ?: existing?.durationMs,
            trashedAt = manifest?.trashedAt ?: folderTrashedAt ?: existing?.trashedAt,
            preTrashFolderId = if (manifest?.trashedAt != null || folderTrashedAt != null) {
                folderId ?: existing?.preTrashFolderId
            } else {
                existing?.preTrashFolderId
            },
            createdAt = manifest?.createdAt ?: existing?.createdAt
            ?: (document.dateSeconds * 1000L),
            modifiedAt = manifest?.modifiedAt ?: existing?.modifiedAt
            ?: (document.dateSeconds * 1000L),
            addedAt = existing?.addedAt ?: now
        )

        return when {
            existing == null -> {
                fileDao.upsert(entity)
                ReconcileResult.INSERTED
            }

            existing.id != entity.id -> {
                fileDao.deleteByIds(listOf(existing.id))
                fileDao.upsert(entity)
                ReconcileResult.UPDATED
            }

            existing != entity -> {
                fileDao.upsert(entity)
                ReconcileResult.UPDATED
            }

            else -> ReconcileResult.UNCHANGED
        }
    }

    private enum class ReconcileResult { INSERTED, UPDATED, UNCHANGED }

    companion object {
        private const val TAG = "SyncRepository"
        private const val KEY_BACKUP_MARKER = "#teledrive-keybackup"
        private const val PAGE_SIZE = 100
        private const val MAX_PAGES = 2000
    }
}
