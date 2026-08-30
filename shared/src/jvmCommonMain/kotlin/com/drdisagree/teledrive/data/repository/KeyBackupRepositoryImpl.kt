package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.core.common.AppError
import com.drdisagree.teledrive.core.files.AppStoragePaths
import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.core.crypto.CryptoKeys
import com.drdisagree.teledrive.core.crypto.KeyBackupCodec
import com.drdisagree.teledrive.core.crypto.KeyUnavailableException
import com.drdisagree.teledrive.core.crypto.WrappedKeyRepository
import com.drdisagree.teledrive.core.telegram.RemoteDocument
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramDownloadEvent
import com.drdisagree.teledrive.core.telegram.TelegramException
import com.drdisagree.teledrive.core.telegram.TelegramUploadEvent
import com.drdisagree.teledrive.domain.repository.KeyBackupRepository
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.io.File

class KeyBackupRepositoryImpl(
    private val storagePaths: AppStoragePaths,
    private val telegramClient: TelegramClient,
    private val keyBackupCodec: KeyBackupCodec,
    private val wrappedKeyRepository: WrappedKeyRepository,
    private val settingsRepository: SettingsRepository
) : KeyBackupRepository {

    override suspend fun createBackup(
        passphrase: CharArray,
        hint: String?
    ): AppResult<Unit> = runTelegram {
        val chatId = storageChatId()
        val previous = existingBackup(chatId)
        if (previous != null && !wrappedKeyRepository.exists(CryptoKeys.CONTENT)) {
            return@runTelegram AppResult.Failure(
                AppError.UnsupportedOperation(
                    "This channel already holds a key backup, and this device has no " +
                            "key yet. Restore the existing backup with its passphrase first, " +
                            "or files encrypted with the old key can never be decrypted."
                )
            )
        }
        val contentKey = wrappedKeyRepository.getOrCreate(CryptoKeys.CONTENT)
        val blob = keyBackupCodec.encode(contentKey, passphrase, hint)

        previous?.let {
            telegramClient.deleteMessages(chatId, listOf(it.messageId))
        }

        val staging = File(storagePaths.cacheDir, KeyBackupCodec.BACKUP_FILE_NAME)
        staging.writeBytes(blob)
        try {
            telegramClient.uploadDocument(
                chatId = chatId,
                localPath = staging.absolutePath,
                fileName = KeyBackupCodec.BACKUP_FILE_NAME,
                mimeType = "application/octet-stream",
                caption = BACKUP_MARKER
            ).collect { event ->
                if (event is TelegramUploadEvent.Completed) Unit
            }
        } finally {
            staging.delete()
        }
        AppResult.Success(Unit)
    }

    override suspend fun restore(passphrase: CharArray): AppResult<Boolean> = runTelegram {
        val blob = downloadBackupBlob()
            ?: return@runTelegram AppResult.Failure(AppError.NotFound)
        val key = keyBackupCodec.decode(blob, passphrase)
            ?: return@runTelegram AppResult.Success(false)
        wrappedKeyRepository.store(CryptoKeys.CONTENT, key)
        AppResult.Success(true)
    }

    override suspend fun backupHint(): AppResult<String?> = runTelegram {
        val blob = downloadBackupBlob()
            ?: return@runTelegram AppResult.Failure(AppError.NotFound)
        AppResult.Success(keyBackupCodec.readInfo(blob)?.hint)
    }

    private suspend fun downloadBackupBlob(): ByteArray? {
        val chatId = storageChatId()
        val document = existingBackup(chatId) ?: return null
        var localPath: String? = null
        telegramClient.downloadDocument(document.remoteFileId).collect { event ->
            if (event is TelegramDownloadEvent.Completed) localPath = event.localPath
        }
        return localPath?.let(::File)?.takeIf { it.exists() }?.readBytes()
    }

    private suspend fun existingBackup(chatId: Long): RemoteDocument? {
        var fromMessageId = 0L
        var pages = 0
        while (pages++ < MAX_PAGES) {
            val page = telegramClient.fetchDocuments(chatId, fromMessageId, PAGE_SIZE)
            page.documents.firstOrNull {
                it.fileName == KeyBackupCodec.BACKUP_FILE_NAME ||
                        it.caption.startsWith(BACKUP_MARKER)
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

    private suspend fun <T> runTelegram(block: suspend () -> AppResult<T>): AppResult<T> = try {
        block()
    } catch (e: TelegramException) {
        AppResult.Failure(
            if (e.isRateLimit) AppError.RateLimited(e.retryAfterSeconds ?: 0)
            else AppError.TelegramError(e.code, e.message)
        )
    } catch (_: KeyUnavailableException) {
        AppResult.Failure(AppError.KeyUnreadable)
    }

    companion object {
        private const val BACKUP_MARKER = "#teledrive-keybackup"
        private const val PAGE_SIZE = 100
        private const val MAX_PAGES = 200
    }
}
