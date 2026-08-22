package com.drdisagree.teledrive.core.transfer

import android.content.Context
import com.drdisagree.teledrive.R
import com.drdisagree.teledrive.core.crypto.CryptoKeys
import com.drdisagree.teledrive.core.crypto.StreamCrypto
import com.drdisagree.teledrive.core.crypto.WrappedKeyRepository
import com.drdisagree.teledrive.core.files.DownloadWriter
import com.drdisagree.teledrive.core.files.Hashing
import com.drdisagree.teledrive.core.media.ThumbnailStore
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramDownloadEvent
import com.drdisagree.teledrive.core.telegram.TelegramException
import com.drdisagree.teledrive.core.telegram.TelegramUploadEvent
import com.drdisagree.teledrive.data.local.dao.BackupDao
import com.drdisagree.teledrive.data.local.dao.FileDao
import com.drdisagree.teledrive.data.local.dao.TransferDao
import com.drdisagree.teledrive.data.local.entity.BackupRecordEntity
import com.drdisagree.teledrive.data.local.entity.TransferEntity
import com.drdisagree.teledrive.data.remote.telegram.ManifestCodec
import com.drdisagree.teledrive.data.remote.telegram.RemoteFileManifest
import com.drdisagree.teledrive.data.repository.FolderPathResolver
import com.drdisagree.teledrive.domain.model.BackupState
import com.drdisagree.teledrive.domain.model.TransferState
import com.drdisagree.teledrive.domain.model.TransferType
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * Executes one transfer end to end: staging (optional encryption), the
 * Telegram operation, progress persistence, and remote-mapping bookkeeping.
 * Pause and cancel are cooperative: the DB row's state is checked on every
 * progress event and the underlying TDLib operation is cancelled by aborting
 * flow collection.
 */
@Singleton
class TransferExecutor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val telegramClient: TelegramClient,
    private val transferDao: TransferDao,
    private val fileDao: FileDao,
    private val backupDao: BackupDao,
    private val manifestCodec: ManifestCodec,
    private val folderPathResolver: FolderPathResolver,
    private val thumbnailStore: ThumbnailStore,
    private val streamCrypto: StreamCrypto,
    private val wrappedKeyRepository: WrappedKeyRepository,
    private val downloadWriter: DownloadWriter,
    private val settingsRepository: SettingsRepository
) {

    /**
     * Telegram reports transfers through updates, so a connection that dies
     * without an error simply stops emitting. Without this the collector waits
     * forever and the row stays RUNNING with no way back.
     */
    private fun <T> Flow<T>.failWhenIdle(messageRes: Int): Flow<T> = channelFlow {
        val relay = Channel<T>(Channel.BUFFERED)
        launch {
            try {
                collect { relay.send(it) }
                relay.close()
            } catch (e: Throwable) {
                relay.close(e)
            }
        }
        while (true) {
            val received = withTimeoutOrNull(STALL_TIMEOUT_MS.milliseconds) { relay.receiveCatching() }
                ?: throw TelegramException(STALL_CODE, context.getString(messageRes))
            received.exceptionOrNull()?.let { throw it }
            if (received.isClosed) break
            send(received.getOrThrow())
        }
    }

    sealed interface Outcome {
        data object Completed : Outcome
        data object Paused : Outcome
        data object Cancelled : Outcome
        data class Failed(val message: String, val retryAfterSeconds: Int? = null) : Outcome
    }

    suspend fun execute(transfer: TransferEntity): Outcome = when (transfer.type) {
        TransferType.UPLOAD, TransferType.BACKUP -> executeUpload(transfer)
        TransferType.DOWNLOAD, TransferType.RESTORE -> executeDownload(transfer)
    }

    private suspend fun executeUpload(transfer: TransferEntity): Outcome {
        val fileId = transfer.fileId
            ?: return Outcome.Failed(context.getString(R.string.transfer_error_no_file_reference))
        val entity = fileDao.byId(fileId)
            ?: return Outcome.Failed(context.getString(R.string.transfer_error_file_record_missing))
        val localPath = entity.localPath
            ?: return Outcome.Failed(context.getString(R.string.transfer_error_no_local_copy))
        val sourceFile = File(localPath)
        if (!sourceFile.exists()) return Outcome.Failed(context.getString(R.string.transfer_error_local_file_gone))

        val prefs = settingsRepository.preferences.first()
        val chatId = transfer.chatId
            ?: telegramClient.ensureStorageChat(prefs.storageChatId).also { resolved ->
                if (resolved != prefs.storageChatId) {
                    settingsRepository.update { it.copy(storageChatId = resolved) }
                }
            }

        val encrypt = prefs.encryptFiles && prefs.keyBackupCreated
        val contentHash = entity.contentHash ?: Hashing.sha256(sourceFile)
        if (contentHash != null && contentHash != entity.contentHash) {
            fileDao.upsert(entity.copy(contentHash = contentHash))
        }

        val manifest = RemoteFileManifest(
            fileId = entity.id,
            name = entity.name,
            folderPath = folderPathResolver.pathOf(entity.folderId),
            folderId = entity.folderId,
            mimeType = entity.mimeType,
            sizeBytes = entity.sizeBytes,
            contentHash = contentHash,
            hidden = entity.isHidden,
            archived = entity.isArchived,
            encrypted = encrypt,
            createdAt = entity.createdAt,
            modifiedAt = entity.modifiedAt,
            width = entity.width,
            height = entity.height,
            durationMs = entity.durationMs
        )
        val caption = manifestCodec.encode(manifest, encrypt)

        var stagingFile: File? = null
        val (uploadPath, uploadName) = if (encrypt) {
            val staging = File(stagingDir(), "${entity.id}.tde")
            val key = wrappedKeyRepository.getOrCreate(CryptoKeys.CONTENT)
            sourceFile.inputStream().use { input ->
                staging.outputStream().buffered().use { output ->
                    streamCrypto.encryptStream(key, input, output)
                }
            }
            stagingFile = staging
            staging.absolutePath to "${entity.id}.tde"
        } else {
            sourceFile.absolutePath to entity.name
        }

        fileDao.setBackupState(entity.id, BackupState.UPLOADING)

        val startedAt = System.currentTimeMillis()
        var lastBytes = 0L
        var lastTick = startedAt

        return try {
            var outcome: Outcome =
                Outcome.Failed(context.getString(R.string.transfer_error_upload_ended))
            val previewPath = if (encrypt) {
                null
            } else {
                thumbnailStore.uploadThumbnailFile(entity.id)?.absolutePath
            }
            telegramClient.uploadDocument(
                chatId = chatId,
                localPath = uploadPath,
                fileName = uploadName,
                mimeType = if (encrypt) "application/octet-stream" else entity.mimeType,
                caption = caption,
                thumbnailPath = previewPath
            ).failWhenIdle(R.string.transfer_error_upload_stalled).collect { event ->
                currentCoroutineContext().ensureActive()
                when (event) {
                    is TelegramUploadEvent.Started -> Unit
                    is TelegramUploadEvent.Progress -> {
                        val now = System.currentTimeMillis()
                        val speed = speedOf(event.transferredBytes, lastBytes, lastTick, now)
                            ?: transfer.speedBytesPerSecond
                        if (now - lastTick >= PROGRESS_INTERVAL_MS) {
                            lastBytes = event.transferredBytes
                            lastTick = now
                            transferDao.updateProgress(
                                transfer.id, event.transferredBytes, speed, now
                            )
                        }
                        checkControl(transfer.id)
                    }

                    is TelegramUploadEvent.Completed -> {
                        val document = event.document
                        fileDao.setRemoteMapping(
                            id = entity.id,
                            chatId = document.chatId,
                            messageId = document.messageId,
                            remoteFileId = document.remoteFileId,
                            remoteUniqueId = document.uniqueFileId,
                            state = BackupState.BACKED_UP
                        )
                        if (transfer.type == TransferType.BACKUP) {
                            backupDao.upsertRecord(
                                BackupRecordEntity(
                                    id = UUID.randomUUID().toString(),
                                    sourcePath = localPath,
                                    fileId = entity.id,
                                    sizeBytes = entity.sizeBytes,
                                    modifiedAt = sourceFile.lastModified(),
                                    contentHash = contentHash,
                                    backedUpAt = System.currentTimeMillis()
                                )
                            )
                        }
                        transferDao.setCompleted(transfer.id, System.currentTimeMillis())
                        outcome = Outcome.Completed
                    }
                }
            }
            outcome
        } catch (e: TransferControlException) {
            fileDao.setBackupStateIfLocalOnly(
                entity.id,
                if (e.paused) BackupState.QUEUED else BackupState.NONE
            )
            if (e.paused) Outcome.Paused else Outcome.Cancelled
        } catch (e: TelegramException) {
            fileDao.setBackupStateIfLocalOnly(entity.id, BackupState.FAILED)
            Outcome.Failed(e.message, e.retryAfterSeconds)
        } finally {
            stagingFile?.delete()
        }
    }

    private suspend fun executeDownload(transfer: TransferEntity): Outcome {
        val fileId = transfer.fileId
            ?: return Outcome.Failed(context.getString(R.string.transfer_error_no_file_reference))
        val entity = fileDao.byId(fileId)
            ?: return Outcome.Failed(context.getString(R.string.transfer_error_file_record_missing))
        val remoteFileId = entity.remoteFileId
            ?: return Outcome.Failed(context.getString(R.string.transfer_error_no_remote_copy))

        val startedAt = System.currentTimeMillis()
        var lastBytes = 0L
        var lastTick = startedAt

        return try {
            var outcome: Outcome =
                Outcome.Failed(context.getString(R.string.transfer_error_download_ended))
            telegramClient.downloadDocument(remoteFileId)
                .failWhenIdle(R.string.transfer_error_download_stalled)
                .collect { event ->
                currentCoroutineContext().ensureActive()
                when (event) {
                    is TelegramDownloadEvent.Progress -> {
                        val now = System.currentTimeMillis()
                        if (now - lastTick >= PROGRESS_INTERVAL_MS) {
                            val speed = speedOf(event.transferredBytes, lastBytes, lastTick, now)
                                ?: transfer.speedBytesPerSecond
                            lastBytes = event.transferredBytes
                            lastTick = now
                            transferDao.updateProgress(
                                transfer.id, event.transferredBytes, speed, now
                            )
                        }
                        checkControl(transfer.id)
                    }

                    is TelegramDownloadEvent.Completed -> {
                        outcome = finalizeDownload(transfer, entity.id, event.localPath)
                    }
                }
            }
            outcome
        } catch (e: TransferControlException) {
            if (e.paused) Outcome.Paused else Outcome.Cancelled
        } catch (e: TelegramException) {
            Outcome.Failed(e.message, e.retryAfterSeconds)
        }
    }

    private suspend fun finalizeDownload(
        transfer: TransferEntity,
        fileId: String,
        tdlibPath: String
    ): Outcome {
        val entity = fileDao.byId(fileId)
            ?: return Outcome.Failed(context.getString(R.string.transfer_error_file_record_missing))
        val source = File(tdlibPath)
        if (!source.exists()) return Outcome.Failed(context.getString(R.string.transfer_error_downloaded_missing))

        val key = if (entity.isEncrypted) {
            wrappedKeyRepository.get(CryptoKeys.CONTENT)
                ?: return Outcome.Failed(context.getString(R.string.transfer_error_key_missing))
        } else {
            null
        }

        val folderPath = folderPathResolver.pathOf(entity.folderId)
        val savedPath = downloadWriter.write(
            fileName = entity.name,
            mimeType = entity.mimeType,
            folderPath = folderPath
        ) { output ->
            source.inputStream().buffered().use { input ->
                if (key != null) {
                    streamCrypto.decryptStream(key, input, output)
                } else {
                    input.copyTo(output)
                }
            }
        } ?: return Outcome.Failed(
            if (entity.isEncrypted) {
                context.getString(R.string.transfer_error_decryption_failed)
            } else {
                context.getString(R.string.transfer_error_save_failed)
            }
        )

        fileDao.setLocalPath(fileId, savedPath, System.currentTimeMillis())
        transferDao.setCompleted(transfer.id, System.currentTimeMillis())
        return Outcome.Completed
    }

    private suspend fun checkControl(transferId: String) {
        when (transferDao.byId(transferId)?.state) {
            TransferState.PAUSED -> throw TransferControlException(paused = true)
            TransferState.CANCELLED -> throw TransferControlException(paused = false)
            else -> Unit
        }
    }

    private fun speedOf(current: Long, last: Long, lastTick: Long, now: Long): Long? {
        val elapsed = now - lastTick
        if (elapsed < PROGRESS_INTERVAL_MS || current < last) return null
        return (current - last) * 1000 / elapsed
    }

    private fun stagingDir(): File =
        File(context.cacheDir, "staging").apply { mkdirs() }

    private class TransferControlException(val paused: Boolean) : Exception()

    companion object {
        private const val PROGRESS_INTERVAL_MS = 400L
        private const val STALL_TIMEOUT_MS = 180_000L
        private const val STALL_CODE = 408
    }
}
