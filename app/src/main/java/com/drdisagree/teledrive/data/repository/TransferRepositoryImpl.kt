package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.core.common.AppError
import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.core.files.StorageInspector
import com.drdisagree.teledrive.core.telegram.TelegramClient
import com.drdisagree.teledrive.core.telegram.TelegramException
import com.drdisagree.teledrive.core.telegram.TelegramLimits
import com.drdisagree.teledrive.core.transfer.BackupSessionTracker
import com.drdisagree.teledrive.core.transfer.FileParts
import com.drdisagree.teledrive.core.transfer.TransferScheduler
import com.drdisagree.teledrive.data.local.dao.FileDao
import com.drdisagree.teledrive.data.local.dao.TransferDao
import com.drdisagree.teledrive.data.local.entity.TransferEntity
import com.drdisagree.teledrive.data.mapper.toDomain
import com.drdisagree.teledrive.domain.model.BackupState
import com.drdisagree.teledrive.domain.model.TransferState
import com.drdisagree.teledrive.domain.model.TransferSection
import com.drdisagree.teledrive.domain.model.TransferTask
import com.drdisagree.teledrive.domain.model.TransferType
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import com.drdisagree.teledrive.domain.repository.TransferRepository
import com.drdisagree.teledrive.domain.usecase.ValidateUploadUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferRepositoryImpl @Inject constructor(
    private val transferDao: TransferDao,
    private val fileDao: FileDao,
    private val telegramClient: TelegramClient,
    private val transferScheduler: TransferScheduler,
    private val settingsRepository: SettingsRepository,
    private val validateUpload: ValidateUploadUseCase,
    private val storageInspector: StorageInspector,
    private val backupSessionTracker: BackupSessionTracker
) : TransferRepository {

    override fun observeSection(
        section: TransferSection,
        limit: Int
    ): Flow<List<TransferTask>> = when (section) {
        TransferSection.ACTIVE -> transferDao.observeActive(limit)
        TransferSection.PAUSED -> transferDao.observePaused(limit)
        TransferSection.FAILED -> transferDao.observeFailed(limit)
        TransferSection.COMPLETED -> transferDao.observeCompleted(limit)
    }.map { list -> list.map { it.toDomain() } }

    override fun observeSectionCount(section: TransferSection): Flow<Int> = when (section) {
        TransferSection.ACTIVE -> transferDao.observeActiveCount()
        TransferSection.PAUSED -> transferDao.observeCountByState(TransferState.PAUSED)
        TransferSection.FAILED -> transferDao.observeCountByState(TransferState.FAILED)
        TransferSection.COMPLETED -> transferDao.observeCountByState(TransferState.COMPLETED)
    }

    override fun observeActiveCount(): Flow<Int> = transferDao.observeActiveCount()

    override fun observeActiveForFile(fileId: String): Flow<TransferTask?> =
        transferDao.observeActiveForFile(fileId).map { it?.toDomain() }

    override suspend fun enqueueUpload(fileId: String, priority: Int): AppResult<String> =
        enqueue(fileId, TransferType.UPLOAD, priority)

    override suspend fun enqueuePendingUploads(): AppResult<Int> {
        val chatId = settingsRepository.preferences.first().storageChatId
        val pending = fileDao.localOnlyFileIds(chatId)
        return AppResult.Success(enqueueBatch(pending, TransferType.UPLOAD, sessionId = null))
    }

    override suspend fun enqueueDownload(fileId: String, priority: Int): AppResult<String> {
        val entity = fileDao.byId(fileId) ?: return AppResult.Failure(AppError.NotFound)
        transferDao.unfinishedIdsForFiles(listOf(fileId)).firstOrNull()
            ?.let { return AppResult.Success(it) }

        if (entity.remoteFileId == null) {
            return AppResult.Failure(AppError.NoRemoteCopy)
        }
        val available = storageInspector.availableBytes()
        if (available < entity.sizeBytes + STORAGE_MARGIN) {
            return AppResult.Failure(
                AppError.InsufficientStorage(entity.sizeBytes + STORAGE_MARGIN, available)
            )
        }
        return insertTransfer(
            entity.id,
            entity.name,
            entity.sizeBytes,
            TransferType.DOWNLOAD,
            priority,
            null
        )
    }

    suspend fun enqueueBackup(
        fileId: String,
        sessionId: String,
        priority: Int = 0
    ): AppResult<String> = enqueue(fileId, TransferType.BACKUP, priority, sessionId)

    override suspend fun enqueueBackupBatch(
        fileIds: List<String>,
        sessionId: String
    ): AppResult<Int> = AppResult.Success(
        enqueueBatch(fileIds, TransferType.BACKUP, sessionId)
    )

    private suspend fun enqueueBatch(
        fileIds: List<String>,
        type: TransferType,
        sessionId: String?
    ): Int {
        if (fileIds.isEmpty()) return 0

        val limits = currentLimits()
        val prefs = settingsRepository.preferences.first()
        val availableBytes = storageInspector.availableBytes()
        var queued = 0

        for (chunk in fileIds.chunked(SQL_BATCH)) {
            val alreadyQueued = transferDao.unfinishedFileIds(chunk).toSet()
            val now = System.currentTimeMillis()
            val transfers = mutableListOf<TransferEntity>()
            for (fileId in chunk) {
                if (fileId in alreadyQueued) continue
                val entity = fileDao.byId(fileId) ?: continue
                if (entity.localPath == null) continue
                val scratch = if (prefs.encryptFiles) {
                    minOf(entity.sizeBytes, FileParts.PART_SIZE)
                } else {
                    0
                }
                val rejected = validateUpload(
                    fileSizeBytes = entity.sizeBytes,
                    limits = limits,
                    availableLocalBytes = availableBytes,
                    requiredScratchBytes = scratch,
                    splitsIfTooLarge = true
                )
                if (rejected != null) continue
                fileDao.setBackupStateIfLocalOnly(entity.id, BackupState.QUEUED)
                transfers += TransferEntity(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    fileId = entity.id,
                    displayName = entity.name,
                    localPath = null,
                    chatId = null,
                    messageId = null,
                    remoteFileId = null,
                    sizeBytes = entity.sizeBytes,
                    state = TransferState.QUEUED,
                    priority = 0,
                    backupSessionId = sessionId,
                    createdAt = now,
                    updatedAt = now
                )
            }
            if (transfers.isNotEmpty()) {
                transferDao.upsertAll(transfers)
                queued += transfers.size
            }
        }
        if (queued > 0) kickWorker()
        return queued
    }

    private suspend fun enqueue(
        fileId: String,
        type: TransferType,
        priority: Int,
        sessionId: String? = null
    ): AppResult<String> {
        val entity = fileDao.byId(fileId) ?: return AppResult.Failure(AppError.NotFound)
        if (entity.localPath == null) {
            return AppResult.Failure(AppError.NoLocalCopy)
        }
        transferDao.unfinishedIdsForFiles(listOf(fileId)).firstOrNull()
            ?.let { return AppResult.Success(it) }


        val limits = currentLimits()
        val prefs = settingsRepository.preferences.first()
        val scratch = if (prefs.encryptFiles) {
            minOf(entity.sizeBytes, FileParts.PART_SIZE)
        } else {
            0
        }
        validateUpload(
            fileSizeBytes = entity.sizeBytes,
            limits = limits,
            availableLocalBytes = storageInspector.availableBytes(),
            requiredScratchBytes = scratch,
            splitsIfTooLarge = true
        )?.let { return AppResult.Failure(it) }

        fileDao.setBackupStateIfLocalOnly(entity.id, BackupState.QUEUED)
        return insertTransfer(entity.id, entity.name, entity.sizeBytes, type, priority, sessionId)
    }

    private suspend fun insertTransfer(
        fileId: String,
        name: String,
        sizeBytes: Long,
        type: TransferType,
        priority: Int,
        sessionId: String?
    ): AppResult<String> {
        val now = System.currentTimeMillis()
        val transfer = TransferEntity(
            id = UUID.randomUUID().toString(),
            type = type,
            fileId = fileId,
            displayName = name,
            localPath = null,
            chatId = null,
            messageId = null,
            remoteFileId = null,
            sizeBytes = sizeBytes,
            state = TransferState.QUEUED,
            priority = priority,
            backupSessionId = sessionId,
            createdAt = now,
            updatedAt = now
        )
        transferDao.upsert(transfer)
        kickWorker()
        return AppResult.Success(transfer.id)
    }

    override suspend fun pause(id: String) {
        val transfer = transferDao.byId(id) ?: return
        if (transfer.state == TransferState.QUEUED || transfer.state == TransferState.RUNNING) {
            transferDao.setState(id, TransferState.PAUSED, System.currentTimeMillis())
            backupSessionTracker.refresh(transfer.backupSessionId)
        }
    }

    override suspend fun resume(id: String) {
        val transfer = transferDao.byId(id) ?: return
        if (transfer.state == TransferState.PAUSED || transfer.state == TransferState.FAILED) {
            transferDao.setState(id, TransferState.QUEUED, System.currentTimeMillis())
            backupSessionTracker.refresh(transfer.backupSessionId)
            kickWorker()
        }
    }

    override suspend fun cancel(id: String) {
        val transfer = transferDao.byId(id) ?: return
        if (transfer.state.isTerminal) {
            transferDao.deleteByIds(listOf(id))
        } else {
            transferDao.setState(id, TransferState.CANCELLED, System.currentTimeMillis())
            transfer.fileId?.let { fileDao.setBackupStateIfLocalOnly(it, BackupState.NONE) }
        }
        syncFileStates()
        backupSessionTracker.refresh(transfer.backupSessionId)
    }

    override suspend fun cancelForFiles(fileIds: List<String>) {
        if (fileIds.isEmpty()) return
        fileIds.chunked(SQL_BATCH)
            .flatMap { transferDao.unfinishedIdsForFiles(it) }
            .forEach { cancel(it) }
    }

    override suspend fun pauseAll() {
        transferDao.pauseAll(System.currentTimeMillis())
        backupSessionTracker.refreshActive()
    }

    override suspend fun resumeAll() {
        transferDao.resumeAll(System.currentTimeMillis())
        backupSessionTracker.refreshActive()
        kickWorker()
    }

    override suspend fun cancelAll() {
        transferDao.cancelAll(System.currentTimeMillis())
        syncFileStates()
        backupSessionTracker.refreshActive()
    }

    override suspend fun retry(id: String) {
        val transfer = transferDao.byId(id) ?: return
        if (transfer.state == TransferState.FAILED || transfer.state == TransferState.CANCELLED) {
            transferDao.upsert(
                transfer.copy(
                    state = TransferState.QUEUED,
                    transferredBytes = 0,
                    errorMessage = null,
                    updatedAt = System.currentTimeMillis()
                )
            )
            kickWorker()
        }
    }

    override suspend fun clearFinished() {
        transferDao.clearFinished()
        syncFileStates()
    }

    override suspend fun recoverOrphanedTransfers() {
        transferDao.requeueRunning()
        syncFileStates()
        kickWorker()
    }

    /**
     * A file's backup state only means something while a transfer backs it up.
     * Once those rows are gone the file is simply not backed up.
     */
    private suspend fun syncFileStates() {
        fileDao.repairBackedUpStates()
        fileDao.clearStaleQueuedStates()
        fileDao.clearStaleFailedStates()
        fileDao.deleteCancelledBackupEntries()
    }

    private suspend fun kickWorker() {
        val prefs = settingsRepository.preferences.first()
        transferScheduler.kick(prefs.allowMeteredTransfers)
    }

    private suspend fun currentLimits(): TelegramLimits = try {
        telegramClient.getLimits()
    } catch (_: TelegramException) {
        TelegramLimits.REGULAR
    }

    companion object {
        private const val STORAGE_MARGIN = 100L * 1024 * 1024
        private const val SQL_BATCH = 500
    }
}
