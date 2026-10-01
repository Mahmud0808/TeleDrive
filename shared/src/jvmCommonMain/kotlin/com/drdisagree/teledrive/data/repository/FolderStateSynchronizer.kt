package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.core.files.AppStoragePaths
import com.drdisagree.teledrive.core.common.SafeLog
import com.drdisagree.teledrive.core.crypto.CryptoKeys
import com.drdisagree.teledrive.core.crypto.StreamCrypto
import com.drdisagree.teledrive.core.crypto.WrappedKeyRepository
import com.drdisagree.teledrive.core.telegram.RemoteDocument
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramDownloadEvent
import com.drdisagree.teledrive.core.telegram.TelegramException
import com.drdisagree.teledrive.core.telegram.TelegramUploadEvent
import com.drdisagree.teledrive.data.local.dao.FolderDao
import com.drdisagree.teledrive.data.local.dao.FolderTombstoneDao
import com.drdisagree.teledrive.data.local.entity.FolderEntity
import com.drdisagree.teledrive.data.local.entity.FolderTombstoneEntity
import com.drdisagree.teledrive.data.remote.telegram.RemoteFolderState
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Mirrors the folder tree into a single document in the storage chat. File
 * captions only carry a folder path, so empty folders, folder ids, and folder
 * flags would otherwise be lost when local data is wiped.
 *
 * Pushes are debounced because a bulk operation can touch many folders.
 */
class FolderStateSynchronizer(
    private val storagePaths: AppStoragePaths,
    private val telegramClient: TelegramClient,
    private val folderDao: FolderDao,
    private val tombstoneDao: FolderTombstoneDao,
    private val settingsRepository: SettingsRepository,
    private val streamCrypto: StreamCrypto,
    private val wrappedKeyRepository: WrappedKeyRepository
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val mutex = Mutex()

    /**
     * Uploads this device's tree merged with the one already in the channel,
     * and keeps the merge locally too. A document that exists but cannot be
     * read without the content key is replaced, as it always was, because
     * waiting for a key that may never come would block every folder change.
     */
    suspend fun push() = mutex.withLock {
        val chatId = storageChatId()
        val existing = findStateDocument(chatId)
        val remote = existing?.let { document ->
            when (val read = read(document)) {
                is RemoteRead.Ok -> read.state
                RemoteRead.Unreadable -> {
                    SafeLog.w(TAG, "Folder state unreadable, replacing it")
                    null
                }
                RemoteRead.Unavailable -> error("Folder state download failed")
            }
        }
        val state = merged(chatId, remote)
        applyLocally(chatId, state)

        val staging = File(storagePaths.cacheDir, RemoteFolderState.FILE_NAME)
        val payload = json.encodeToString(RemoteFolderState.serializer(), state)
            .toByteArray(Charsets.UTF_8)
        val prefs = settingsRepository.preferences.first()
        staging.writeBytes(
            if (prefs.encryptFiles && prefs.keyBackupCreated) seal(payload) else payload
        )
        try {
            telegramClient.uploadDocument(
                chatId = chatId,
                localPath = staging.absolutePath,
                fileName = RemoteFolderState.FILE_NAME,
                mimeType = "application/json",
                caption = RemoteFolderState.MARKER
            ).collect { event ->
                if (event is TelegramUploadEvent.Completed) Unit
            }
            existing?.let { telegramClient.deleteMessages(chatId, listOf(it.messageId)) }
        } finally {
            staging.delete()
        }
        tombstoneDao.deleteOlderThan(deletionCutoff())
    }

    /**
     * The tree names folders the owner chose, so it is sealed with the same
     * content key as the files whenever encryption is on. Older plaintext
     * documents stay readable because the sealed form carries a magic header.
     */
    private fun seal(payload: ByteArray): ByteArray {
        val key = wrappedKeyRepository.get(CryptoKeys.CONTENT) ?: return payload
        return MAGIC + streamCrypto.encryptBytes(key, payload)
    }

    private fun unseal(blob: ByteArray): String? {
        if (!blob.copyOfRange(0, minOf(MAGIC.size, blob.size)).contentEquals(MAGIC)) {
            return String(blob, Charsets.UTF_8)
        }
        val key = wrappedKeyRepository.get(CryptoKeys.CONTENT) ?: return null
        return runCatching {
            String(
                streamCrypto.decryptBytes(key, blob.copyOfRange(MAGIC.size, blob.size)),
                Charsets.UTF_8
            )
        }.getOrNull()
    }

    /**
     * Brings other devices' folder changes into the local tree without ever
     * publishing anything, so applying them cannot echo back. Skipped while
     * local changes wait to publish, since [push] merges those itself.
     */
    suspend fun pull(): Int = mutex.withLock {
        if (folderDao.pendingPublishCount() > 0) return@withLock 0
        val chatId = storageChatId()
        val document = findStateDocument(chatId) ?: return@withLock 0
        val remote = (read(document) as? RemoteRead.Ok)?.state ?: return@withLock 0
        applyLocally(chatId, merged(chatId, remote))
    }

    private suspend fun read(document: RemoteDocument): RemoteRead {
        var localPath: String? = null
        telegramClient.downloadDocument(document.remoteFileId).collect { event ->
            if (event is TelegramDownloadEvent.Completed) localPath = event.localPath
        }
        val blob = localPath?.let(::File)?.takeIf { it.exists() }?.readBytes()
            ?: return RemoteRead.Unavailable
        val payload = unseal(blob) ?: return RemoteRead.Unreadable
        return runCatching { json.decodeFromString(RemoteFolderState.serializer(), payload) }
            .fold({ RemoteRead.Ok(it) }, { RemoteRead.Unreadable })
    }

    private suspend fun merged(chatId: Long, remote: RemoteFolderState?): RemoteFolderState =
        FolderStateMerge.merge(
            local = folderDao.allFolders(chatId).map { it.toEntry() },
            localDeleted = tombstoneDao.inChat(chatId).map {
                RemoteFolderState.Tombstone(it.id, it.deletedAt)
            },
            remote = remote,
            keepDeletionsSince = deletionCutoff()
        )

    /**
     * Existing rows are updated rather than replaced: a REPLACE deletes the
     * row first, and the folder foreign keys would take its children and
     * the folder of every file inside with it.
     */
    private suspend fun applyLocally(chatId: Long, state: RemoteFolderState): Int {
        var changed = 0
        for (entry in state.folders.sortedBy { depthOf(it, state.folders) }) {
            val existing = folderDao.byId(entry.id)
            if (existing != null && existing.changedAt >= entry.clock &&
                existing.parentId == entry.parentId
            ) continue
            if (existing != null && existing.changedAt >= entry.clock) {
                folderDao.update(existing.copy(parentId = entry.parentId))
                changed++
                continue
            }
            val row = FolderEntity(
                id = entry.id,
                chatId = chatId,
                parentId = entry.parentId,
                name = entry.name,
                isHidden = entry.hidden,
                isArchived = entry.archived,
                isFavorite = entry.favorite,
                isPinned = existing?.isPinned == true,
                trashedAt = entry.trashedAt,
                preTrashParentId = entry.preTrashParentId,
                pendingPublish = existing?.pendingPublish == true,
                createdAt = entry.createdAt,
                modifiedAt = entry.modifiedAt,
                changedAt = entry.clock
            )
            if (existing == null) folderDao.upsert(row) else folderDao.update(row)
            changed++
        }

        tombstoneDao.deleteExcept(chatId, state.deleted.map { it.id })
        tombstoneDao.upsert(
            state.deleted.map { FolderTombstoneEntity(it.id, chatId, it.deletedAt) }
        )
        val all = folderDao.allFolders(chatId)
        for (deletion in state.deleted) {
            val folder = all.firstOrNull { it.id == deletion.id } ?: continue
            if (folder.changedAt > deletion.deletedAt) continue
            if (hasNewerDescendant(folder.id, deletion.deletedAt, all)) continue
            folderDao.delete(folder.id)
            changed++
        }
        return changed
    }

    private fun hasNewerDescendant(
        folderId: String,
        deletedAt: Long,
        all: List<FolderEntity>
    ): Boolean {
        var frontier = listOf(folderId)
        var guard = 0
        while (frontier.isNotEmpty() && guard++ < MAX_DEPTH) {
            val children = all.filter { it.parentId in frontier || it.preTrashParentId in frontier }
            if (children.any { it.changedAt > deletedAt }) return true
            frontier = children.map { it.id }
        }
        return false
    }

    private fun deletionCutoff(): Long = System.currentTimeMillis() - DELETION_RETENTION_MS

    private fun FolderEntity.toEntry() = RemoteFolderState.Entry(
        id = id,
        parentId = parentId,
        name = name,
        hidden = isHidden,
        archived = isArchived,
        favorite = isFavorite,
        trashedAt = trashedAt,
        preTrashParentId = preTrashParentId,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        changedAt = changedAt
    )

    private sealed interface RemoteRead {
        data class Ok(val state: RemoteFolderState) : RemoteRead
        data object Unreadable : RemoteRead
        data object Unavailable : RemoteRead
    }

    private fun depthOf(
        entry: RemoteFolderState.Entry,
        all: List<RemoteFolderState.Entry>
    ): Int {
        var depth = 0
        var parentId = entry.parentId
        var guard = 0
        while (parentId != null && guard++ < MAX_DEPTH) {
            depth++
            parentId = all.firstOrNull { it.id == parentId }?.parentId
        }
        return depth
    }

    private suspend fun findStateDocument(chatId: Long): RemoteDocument? {
        var fromMessageId = 0L
        var pages = 0
        while (pages++ < MAX_PAGES) {
            val page = try {
                telegramClient.fetchDocuments(chatId, fromMessageId, PAGE_SIZE)
            } catch (e: TelegramException) {
                SafeLog.w(TAG, "Folder state lookup failed: ${e.code}")
                return null
            }
            page.documents.firstOrNull {
                it.fileName == RemoteFolderState.FILE_NAME ||
                        it.caption.startsWith(RemoteFolderState.MARKER)
            }?.let { return it }
            if (page.nextFromMessageId == 0L) return null
            fromMessageId = page.nextFromMessageId
        }
        return null
    }

    private suspend fun storageChatId(): Long {
        val prefs = settingsRepository.preferences.first()
        val chatId = telegramClient.ensureStorageChat(prefs.storageChatId)
        if (chatId != prefs.storageChatId) {
            settingsRepository.update { it.copy(storageChatId = chatId) }
        }
        return chatId
    }

    companion object {
        private const val TAG = "FolderStateSync"
        private val MAGIC = byteArrayOf(0x54, 0x44, 0x46, 0x53) // "TDFS"
        private const val PAGE_SIZE = 100
        private const val MAX_PAGES = 200
        private const val MAX_DEPTH = 64
        private const val DELETION_RETENTION_MS = 180L * 24 * 60 * 60 * 1000
    }
}
