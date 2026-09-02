package com.drdisagree.teledrive.core.transfer

import com.drdisagree.teledrive.core.files.AppStoragePaths
import com.drdisagree.teledrive.core.crypto.CryptoKeys
import com.drdisagree.teledrive.core.crypto.KeyUnavailableException
import com.drdisagree.teledrive.core.crypto.StreamCrypto
import com.drdisagree.teledrive.core.crypto.WrappedKeyRepository
import com.drdisagree.teledrive.core.files.MimeTypes
import com.drdisagree.teledrive.core.files.DownloadWriter
import com.drdisagree.teledrive.core.files.Hashing
import com.drdisagree.teledrive.core.media.ThumbnailStore
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramDownloadEvent
import com.drdisagree.teledrive.core.telegram.TelegramException
import com.drdisagree.teledrive.core.telegram.TelegramLimits
import com.drdisagree.teledrive.core.telegram.TelegramUploadEvent
import com.drdisagree.teledrive.data.local.dao.BackupDao
import com.drdisagree.teledrive.data.local.dao.FileDao
import com.drdisagree.teledrive.data.local.dao.FilePartDao
import com.drdisagree.teledrive.data.local.dao.TransferDao
import com.drdisagree.teledrive.data.local.entity.BackupRecordEntity
import com.drdisagree.teledrive.data.local.entity.FileEntity
import com.drdisagree.teledrive.data.local.entity.TransferEntity
import com.drdisagree.teledrive.data.remote.telegram.ManifestCodec
import com.drdisagree.teledrive.data.remote.telegram.RemoteFileManifest
import com.drdisagree.teledrive.data.repository.FolderPathResolver
import com.drdisagree.teledrive.domain.model.BackupState
import com.drdisagree.teledrive.domain.model.TransferStage
import com.drdisagree.teledrive.domain.model.TransferState
import com.drdisagree.teledrive.domain.model.TransferType
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

/**
 * Executes one transfer end to end: staging (optional encryption), the
 * Telegram operation, progress persistence, and remote-mapping bookkeeping.
 * Pause and cancel are cooperative: the DB row's state is checked on every
 * progress event and the underlying TDLib operation is canceled by aborting
 * flow collection.
 */
class TransferExecutor(
    private val messages: TransferErrorMessages,
    private val storagePaths: AppStoragePaths,
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
    private val settingsRepository: SettingsRepository,
    private val filePartDao: FilePartDao,
    private val partUploader: PartUploader,
    private val partDownloader: PartDownloader,
    private val apkIconUploader: ApkIconUploader
) {

    /**
     * Telegram reports transfers through updates, so a connection that dies
     * without an error simply stops emitting. Without this the collector waits
     * forever and the row stays RUNNING with no way back.
     */
    private fun <T> Flow<T>.failWhenIdle(message: String): Flow<T> = channelFlow {
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
            val received =
                withTimeoutOrNull(STALL_TIMEOUT_MS.milliseconds) { relay.receiveCatching() }
                    ?: throw TelegramException(STALL_CODE, message)
            received.exceptionOrNull()?.let { throw it }
            if (received.isClosed) break
            send(received.getOrThrow())
        }
    }

    sealed interface Outcome {
        data object Completed : Outcome
        data object Paused : Outcome
        data object Canceled : Outcome
        data class Failed(val message: String, val retryAfterSeconds: Int? = null) : Outcome
    }

    suspend fun execute(transfer: TransferEntity): Outcome = try {
        when (transfer.type) {
            TransferType.UPLOAD, TransferType.BACKUP -> executeUpload(transfer)
            TransferType.DOWNLOAD, TransferType.RESTORE -> executeDownload(transfer)
        }
    } catch (_: KeyUnavailableException) {
        Outcome.Failed(messages.keyMissing)
    }

    private suspend fun executeUpload(transfer: TransferEntity): Outcome {
        val fileId = transfer.fileId
            ?: return Outcome.Failed(messages.noFileReference)
        val entity = fileDao.byId(fileId)
            ?: return Outcome.Failed(messages.fileRecordMissing)
        val localPath = entity.localPath
            ?: return Outcome.Failed(messages.noLocalCopy)
        val sourceFile = File(localPath)
        if (!sourceFile.exists()) return Outcome.Failed(messages.localFileGone)

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

        val rawIconPath = thumbnailStore.uploadThumbnailFile(entity.id)?.absolutePath
        val iconFileId = apkIconUploader.uploadIconIfApk(entity, chatId, encrypt)
        val previewPath = if (encrypt) null else rawIconPath

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
            durationMs = entity.durationMs,
            iconFileId = iconFileId
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

        if (splitsIntoParts(entity, sourceFile)) {
            stagingFile?.delete()
            return uploadInParts(
                transfer = transfer,
                entity = entity,
                sourceFile = sourceFile,
                localPath = localPath,
                chatId = chatId,
                manifest = manifest,
                encrypt = encrypt,
                contentHash = contentHash
            )
        }

        val startedAt = System.currentTimeMillis()
        val ticker = ProgressTicker().apply { start(0, startedAt) }

        return try {
            var outcome: Outcome =
                Outcome.Failed(messages.uploadEnded)
            telegramClient.uploadDocument(
                chatId = chatId,
                localPath = uploadPath,
                fileName = uploadName,
                mimeType = if (encrypt) "application/octet-stream" else entity.mimeType,
                caption = caption,
                thumbnailPath = previewPath
            ).failWhenIdle(messages.uploadStalled).collect { event ->
                currentCoroutineContext().ensureActive()
                when (event) {
                    is TelegramUploadEvent.Started -> Unit
                    is TelegramUploadEvent.Progress -> {
                        val now = System.currentTimeMillis()
                        ticker.tick(event.transferredBytes, now)?.let { speed ->
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
                            state = BackupState.BACKED_UP,
                            iconFileId = iconFileId
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
            if (e.paused) Outcome.Paused else Outcome.Canceled
        } catch (e: TelegramException) {
            fileDao.setBackupStateIfLocalOnly(entity.id, BackupState.FAILED)
            Outcome.Failed(e.message, e.retryAfterSeconds)
        } finally {
            stagingFile?.delete()
        }
    }

    /**
     * Splitting is decided by the account's current limit, but a file that was
     * already split stays split: the parts on record are what the file is made
     * of, whatever the limit happens to be today.
     */
    private suspend fun splitsIntoParts(entity: FileEntity, source: File): Boolean {
        if (filePartDao.countOf(entity.id) > 0) return true
        val limit = runCatching { telegramClient.getLimits() }
            .getOrDefault(TelegramLimits.REGULAR)
            .maxFileBytes
        return FileParts.splits(source.length(), limit)
    }

    private suspend fun uploadInParts(
        transfer: TransferEntity,
        entity: FileEntity,
        sourceFile: File,
        localPath: String,
        chatId: Long,
        manifest: RemoteFileManifest,
        encrypt: Boolean,
        contentHash: String?
    ): Outcome {
        val ticker = ProgressTicker().apply { start(0, System.currentTimeMillis()) }
        var outcome: Outcome =
            Outcome.Failed(messages.uploadEnded)

        return try {
            partUploader.upload(entity, sourceFile, chatId, manifest, encrypt)
                .collect { event ->
                    currentCoroutineContext().ensureActive()
                    when (event) {
                        is PartUploader.Event.Progress -> {
                            val now = System.currentTimeMillis()
                            ticker.tick(event.transferredBytes, now)?.let { speed ->
                                transferDao.updateProgress(
                                    transfer.id, event.transferredBytes, speed, now
                                )
                            }
                            checkControl(transfer.id)
                        }

                        is PartUploader.Event.Sealing -> {
                            transferDao.setStage(
                                transfer.id,
                                TransferStage.SEALING,
                                System.currentTimeMillis()
                            )
                            checkControl(transfer.id)
                        }

                        is PartUploader.Event.PartDone -> {
                            transferDao.setStage(transfer.id, null, System.currentTimeMillis())
                            checkControl(transfer.id)
                        }

                        is PartUploader.Event.Completed -> {
                            val first = event.parts.firstOrNull()
                                ?: error("Upload finished with no parts")
                            fileDao.setPartCount(entity.id, event.parts.size)
                            fileDao.setRemoteMapping(
                                id = entity.id,
                                chatId = first.chatId,
                                messageId = first.messageId,
                                remoteFileId = first.remoteFileId,
                                remoteUniqueId = first.remoteUniqueId,
                                state = BackupState.BACKED_UP,
                                iconFileId = manifest.iconFileId
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
            if (!e.paused) partUploader.discardParts(entity.id)
            if (e.paused) Outcome.Paused else Outcome.Canceled
        } catch (e: TelegramException) {
            fileDao.setBackupStateIfLocalOnly(entity.id, BackupState.FAILED)
            Outcome.Failed(e.message, e.retryAfterSeconds)
        }
    }

    private suspend fun executeDownload(transfer: TransferEntity): Outcome {
        val fileId = transfer.fileId
            ?: return Outcome.Failed(messages.noFileReference)
        val entity = fileDao.byId(fileId)
            ?: return Outcome.Failed(messages.fileRecordMissing)

        if (filePartDao.countOf(entity.id) > 1) return downloadInParts(transfer, entity)

        val remoteFileId = entity.remoteFileId
            ?: return Outcome.Failed(messages.noRemoteCopy)

        val startedAt = System.currentTimeMillis()
        val ticker = ProgressTicker().apply { start(0, startedAt) }

        return try {
            var outcome: Outcome =
                Outcome.Failed(messages.downloadEnded)
            telegramClient.downloadDocument(remoteFileId)
                .failWhenIdle(messages.downloadStalled)
                .collect { event ->
                    currentCoroutineContext().ensureActive()
                    when (event) {
                        is TelegramDownloadEvent.Progress -> {
                            val now = System.currentTimeMillis()
                            ticker.tick(event.transferredBytes, now)?.let { speed ->
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
            if (e.paused) Outcome.Paused else Outcome.Canceled
        } catch (e: TelegramException) {
            Outcome.Failed(e.message, e.retryAfterSeconds)
        }
    }

    private suspend fun downloadInParts(
        transfer: TransferEntity,
        entity: FileEntity
    ): Outcome {
        val ticker = ProgressTicker().apply { start(0, System.currentTimeMillis()) }
        var outcome: Outcome =
            Outcome.Failed(messages.downloadEnded)

        return try {
            partDownloader.download(entity.id, entity.isEncrypted)
                .failWhenIdle(messages.downloadStalled)
                .collect { event ->
                    currentCoroutineContext().ensureActive()
                    when (event) {
                        is PartDownloader.Event.Progress -> {
                            val now = System.currentTimeMillis()
                            transferDao.setStage(transfer.id, null, now)
                            ticker.tick(event.transferredBytes, now)?.let { speed ->
                                transferDao.updateProgress(
                                    transfer.id, event.transferredBytes, speed, now
                                )
                            }
                            checkControl(transfer.id)
                        }

                        is PartDownloader.Event.Joining -> {
                            transferDao.setStage(
                                transfer.id,
                                TransferStage.JOINING,
                                System.currentTimeMillis()
                            )
                            checkControl(transfer.id)
                        }

                        is PartDownloader.Event.Completed -> {
                            transferDao.setStage(transfer.id, null, System.currentTimeMillis())
                            outcome = finalizeDownload(
                                transfer = transfer,
                                fileId = entity.id,
                                tdlibPath = event.localPath,
                                alreadyPlain = true
                            )
                            partDownloader.discardAssembly(entity.id)
                        }
                    }
                }
            outcome
        } catch (e: TransferControlException) {
            if (!e.paused) partDownloader.discardAssembly(entity.id)
            if (e.paused) Outcome.Paused else Outcome.Canceled
        } catch (e: TelegramException) {
            Outcome.Failed(e.message, e.retryAfterSeconds)
        }
    }

    private suspend fun finalizeDownload(
        transfer: TransferEntity,
        fileId: String,
        tdlibPath: String,
        alreadyPlain: Boolean = false
    ): Outcome {
        val entity = fileDao.byId(fileId)
            ?: return Outcome.Failed(messages.fileRecordMissing)
        val source = File(tdlibPath)
        if (!source.exists()) return Outcome.Failed(messages.downloadedMissing)

        val key = if (entity.isEncrypted && !alreadyPlain) {
            wrappedKeyRepository.get(CryptoKeys.CONTENT)
                ?: return Outcome.Failed(messages.keyMissing)
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
                messages.decryptionFailed
            } else {
                messages.saveFailed
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

    private fun stagingDir(): File =
        File(storagePaths.cacheDir, "staging").apply { mkdirs() }

    private class TransferControlException(val paused: Boolean) : Exception()

    companion object {
        private const val STALL_TIMEOUT_MS = 180_000L
        private const val STALL_CODE = 408
    }
}
