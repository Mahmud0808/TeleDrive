package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.core.common.SafeLog
import com.drdisagree.teledrive.core.crypto.CryptoKeys
import com.drdisagree.teledrive.core.crypto.StreamCrypto
import com.drdisagree.teledrive.core.crypto.WrappedKeyRepository
import com.drdisagree.teledrive.core.files.AppStoragePaths
import com.drdisagree.teledrive.core.telegram.RemoteDocument
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramDownloadEvent
import com.drdisagree.teledrive.core.telegram.TelegramException
import com.drdisagree.teledrive.core.telegram.TelegramUploadEvent
import com.drdisagree.teledrive.data.local.dao.FolderDao
import com.drdisagree.teledrive.data.local.dao.FolderTombstoneDao
import com.drdisagree.teledrive.data.local.entity.FolderEntity
import com.drdisagree.teledrive.data.local.entity.FolderTombstoneEntity
import com.drdisagree.teledrive.data.remote.telegram.RemoteFolderEntry
import com.drdisagree.teledrive.data.remote.telegram.RemoteFolderState
import com.drdisagree.teledrive.data.remote.telegram.RemoteFolderTombstone
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * File captions only carry a folder path, so this document keeps empty folders, ids and flags
 * across a wipe.
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
     * A document unreadable without the content key is replaced, since waiting for that key would
     * block every folder change.
     */
    suspend fun push() = mutex.withLock {
        val chatId = storageChatId()
        val existing = findStateDocument(chatId)
        val remote = existing?.let { document ->
            when (val read = read(document)) {
                is RemoteFolderRead.Ok -> read.state
                RemoteFolderRead.Unreadable -> {
                    SafeLog.w(TAG, "Folder state unreadable, replacing it")
                    null
                }
                RemoteFolderRead.Unavailable -> error("Folder state download failed")
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
     * Sealed with the content key when encryption is on; older plaintext documents stay readable by
     * the magic header.
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

    /** Never publishes, so applying other devices' changes cannot echo back. */
    suspend fun pull(): Int = mutex.withLock {
        if (folderDao.pendingPublishCount() > 0) return@withLock 0
        val chatId = storageChatId()
        val document = findStateDocument(chatId) ?: return@withLock 0
        val remote = (read(document) as? RemoteFolderRead.Ok)?.state ?: return@withLock 0
        applyLocally(chatId, merged(chatId, remote))
    }

    private suspend fun read(document: RemoteDocument): RemoteFolderRead {
        var localPath: String? = null
        telegramClient.downloadDocument(document.remoteFileId).collect { event ->
            if (event is TelegramDownloadEvent.Completed) localPath = event.localPath
        }
        val blob = localPath?.let(::File)?.takeIf { it.exists() }?.readBytes()
            ?: return RemoteFolderRead.Unavailable
        val payload = unseal(blob) ?: return RemoteFolderRead.Unreadable
        return runCatching { json.decodeFromString(RemoteFolderState.serializer(), payload) }
            .fold({ RemoteFolderRead.Ok(it) }, { RemoteFolderRead.Unreadable })
    }

    private suspend fun merged(chatId: Long, remote: RemoteFolderState?): RemoteFolderState =
        FolderStateMerge.merge(
            local = folderDao.allFolders(chatId).map { it.toEntry() },
            localDeleted = tombstoneDao.inChat(chatId).map {
                RemoteFolderTombstone(it.id, it.deletedAt)
            },
            remote = remote,
            keepDeletionsSince = deletionCutoff()
        )

    /**
     * Updates instead of REPLACE: REPLACE deletes the row first, and the foreign keys would take
     * its subfolders with it.
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
                isAvailableOffline = existing?.isAvailableOffline == true,
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

    private fun FolderEntity.toEntry() = RemoteFolderEntry(
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

    private fun depthOf(
        entry: RemoteFolderEntry,
        all: List<RemoteFolderEntry>
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
