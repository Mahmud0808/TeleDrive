package com.drdisagree.teledrive.data.repository

import android.content.Context
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
import com.drdisagree.teledrive.data.local.entity.FolderEntity
import com.drdisagree.teledrive.data.remote.telegram.RemoteFolderState
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * Mirrors the folder tree into a single document in the storage chat. File
 * captions only carry a folder path, so empty folders, folder ids, and folder
 * flags would otherwise be lost when local data is wiped.
 *
 * Pushes are debounced because a bulk operation can touch many folders.
 */
@Singleton
class FolderStateSynchronizer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val telegramClient: TelegramClient,
    private val folderDao: FolderDao,
    private val settingsRepository: SettingsRepository,
    private val streamCrypto: StreamCrypto,
    private val wrappedKeyRepository: WrappedKeyRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val pushMutex = Mutex()

    @Volatile
    private var pendingPush: Job? = null

    fun schedulePush() {
        pendingPush?.cancel()
        pendingPush = scope.launch {
            delay(PUSH_DEBOUNCE_MS.milliseconds)
            runCatching { push() }
                .onFailure { SafeLog.w(TAG, "Folder state push failed", it) }
        }
    }

    suspend fun push() = pushMutex.withLock {
        val chatId = storageChatId()
        val state = RemoteFolderState(
            folders = folderDao.allFolders(chatId).map { folder ->
                RemoteFolderState.Entry(
                    id = folder.id,
                    parentId = folder.parentId,
                    name = folder.name,
                    hidden = folder.isHidden,
                    archived = folder.isArchived,
                    favorite = folder.isFavorite,
                    trashedAt = folder.trashedAt,
                    preTrashParentId = folder.preTrashParentId,
                    createdAt = folder.createdAt,
                    modifiedAt = folder.modifiedAt
                )
            }
        )

        val existing = findStateDocument(chatId)
        val staging = File(context.cacheDir, RemoteFolderState.FILE_NAME)
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

    /** Restores folder rows from Telegram. Local rows win only when newer. */
    suspend fun pull(): Int {
        val chatId = storageChatId()
        val document = findStateDocument(chatId) ?: return 0
        var localPath: String? = null
        telegramClient.downloadDocument(document.remoteFileId).collect { event ->
            if (event is TelegramDownloadEvent.Completed) localPath = event.localPath
        }
        val blob = localPath?.let(::File)?.takeIf { it.exists() }?.readBytes() ?: return 0
        val payload = unseal(blob) ?: return 0
        val state = runCatching {
            json.decodeFromString(RemoteFolderState.serializer(), payload)
        }.getOrNull() ?: return 0

        var restored = 0
        for (entry in state.folders.sortedBy { depthOf(it, state.folders) }) {
            val existing = folderDao.byId(entry.id)
            if (existing != null && existing.modifiedAt >= entry.modifiedAt) continue
            folderDao.upsert(
                FolderEntity(
                    id = entry.id,
                    chatId = chatId,
                    parentId = entry.parentId,
                    name = entry.name,
                    isHidden = entry.hidden,
                    isArchived = entry.archived,
                    isFavorite = entry.favorite,
                    trashedAt = entry.trashedAt,
                    preTrashParentId = entry.preTrashParentId,
                    createdAt = entry.createdAt,
                    modifiedAt = entry.modifiedAt
                )
            )
            restored++
        }
        return restored
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
        private const val PUSH_DEBOUNCE_MS = 3_000L
        private const val PAGE_SIZE = 100
        private const val MAX_PAGES = 200
        private const val MAX_DEPTH = 64
    }
}
