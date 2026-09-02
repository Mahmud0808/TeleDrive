package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.core.common.AppError
import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.core.common.SafeLog
import com.drdisagree.teledrive.core.files.AppStoragePaths
import com.drdisagree.teledrive.core.files.Hashing
import com.drdisagree.teledrive.core.files.MimeTypes
import com.drdisagree.teledrive.core.media.MediaMetadataExtractor
import com.drdisagree.teledrive.core.transfer.BackupSessionTracker
import com.drdisagree.teledrive.data.local.dao.BackupDao
import com.drdisagree.teledrive.data.local.dao.FileDao
import com.drdisagree.teledrive.data.local.dao.TransferDao
import com.drdisagree.teledrive.data.local.entity.BackupRecordEntity
import com.drdisagree.teledrive.data.local.entity.BackupSessionEntity
import com.drdisagree.teledrive.data.local.entity.FileEntity
import com.drdisagree.teledrive.data.local.entity.TransferEntity
import com.drdisagree.teledrive.data.mapper.toDomain
import com.drdisagree.teledrive.domain.model.BackupDecision
import com.drdisagree.teledrive.domain.model.ExclusionType
import com.drdisagree.teledrive.domain.model.BackupSession
import com.drdisagree.teledrive.domain.model.BackupSessionStatus
import com.drdisagree.teledrive.domain.model.BackupState
import com.drdisagree.teledrive.domain.model.BackupTrigger
import com.drdisagree.teledrive.domain.model.FileCategory
import com.drdisagree.teledrive.domain.model.TransferState
import com.drdisagree.teledrive.domain.repository.BackupRepository
import com.drdisagree.teledrive.domain.repository.ChannelRepository
import com.drdisagree.teledrive.domain.repository.ExclusionRepository
import com.drdisagree.teledrive.domain.repository.FileRepository
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import com.drdisagree.teledrive.domain.usecase.DecideBackupActionUseCase
import com.drdisagree.teledrive.domain.usecase.EvaluateExclusionsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID

class BackupRepositoryImpl(
    private val backupDao: BackupDao,
    private val fileDao: FileDao,
    private val transferDao: TransferDao,
    private val transferRepository: TransferRepositoryImpl,
    private val backupSessionTracker: BackupSessionTracker,
    private val exclusionRepository: ExclusionRepository,
    private val settingsRepository: SettingsRepository,
    private val decideBackupAction: DecideBackupActionUseCase,
    private val mediaMetadataExtractor: MediaMetadataExtractor,
    private val folderPathResolver: FolderPathResolver,
    private val fileRepository: FileRepository,
    private val channelRepository: ChannelRepository,
    private val storagePaths: AppStoragePaths
) : BackupRepository {

    override fun observeActiveSession(): Flow<BackupSession?> =
        backupDao.observeActiveSession().map { it?.toDomain() }

    override fun observeLastBackupAt(): Flow<Long?> = backupDao.observeLastBackupAt()

    override suspend fun startBackup(trigger: BackupTrigger): AppResult<String?> {
        if (backupDao.activeSession() != null) {
            return AppResult.Failure(AppError.BackupAlreadyRunning)
        }

        val prefs = settingsRepository.preferences.first()
        val activeChatId = prefs.storageChatId
        val folders = if (activeChatId != null) {
            channelRepository.backupFolders(activeChatId)
        } else {
            emptySet()
        }
        if (folders.isEmpty()) {
            return AppResult.Failure(
                AppError.UnsupportedOperation(
                    "No backup folders selected. Pick folders in Settings, " +
                            "or upload files from Files."
                )
            )
        }

        backupDao.deleteOrphanedRecords()
        val exclusions = exclusionRepository.getEnabled()
        val maxSizeBytes = prefs.backupMaxFileSizeMb.toLong() * 1024 * 1024
        val skipHidden = exclusions.any { it.type == ExclusionType.HIDDEN }
        val candidates = mutableListOf<File>()
        for (folderPath in folders) {
            val root = File(folderPath)
            if (!root.exists()) continue
            root.walkTopDown()
                .onEnter { dir -> dir == root || !skipHidden || !isHiddenName(dir) }
                .filter { it.isFile && it.length() > 0 }
                .forEach { candidates.add(it) }
        }

        var totalBytes = 0L
        var skipped = 0
        val reasons = mutableMapOf<BackupDecision, Int>()
        val toBackup = mutableListOf<File>()
        for (candidate in candidates) {
            if (backupDao.recordByPath(candidate.absolutePath) == null &&
                (adoptLinkedUpload(candidate) ||
                        reviveTrashedUpload(candidate) ||
                        adoptUploadedCopy(candidate))
            ) {
                skipped++
                continue
            }
            val record = backupDao.recordByPath(candidate.absolutePath)
            val mime = MimeTypes.fromFileName(candidate.name)
            val decision = decideBackupAction(
                candidate = EvaluateExclusionsUseCase.Candidate(
                    absolutePath = candidate.absolutePath,
                    sizeBytes = candidate.length(),
                    mimeType = mime,
                    isHidden = candidate.name.startsWith('.')
                ),
                modifiedAt = candidate.lastModified(),
                existingRecord = record?.let {
                    DecideBackupActionUseCase.ExistingRecord(
                        it.sizeBytes, it.modifiedAt, it.contentHash
                    )
                },
                exclusions = exclusions,
                maxFileSizeBytes = maxSizeBytes,
                contentHashProvider = { Hashing.sha256(candidate) }
            )
            if (decision == BackupDecision.BACKUP) {
                toBackup.add(candidate)
                totalBytes += candidate.length()
            } else {
                skipped++
                reasons[decision] = (reasons[decision] ?: 0) + 1
            }
        }

        if (toBackup.isEmpty()) {
            SafeLog.d(
                TAG,
                "Backup scan: nothing to do, $skipped of ${candidates.size} skipped, " +
                        "reasons=$reasons"
            )
            return AppResult.Success(null)
        }

        val session = BackupSessionEntity(
            id = UUID.randomUUID().toString(),
            trigger = trigger,
            status = BackupSessionStatus.RUNNING,
            totalFiles = toBackup.size,
            skippedFiles = skipped,
            totalBytes = totalBytes,
            startedAt = System.currentTimeMillis()
        )
        backupDao.upsertSession(session)

        for (batch in toBackup.chunked(ENQUEUE_BATCH)) {
            val fileIds = batch.map { registerFile(it, backupFolderIdFor(it), activeChatId) }
            transferRepository.enqueueBackupBatch(fileIds, session.id)
        }
        return AppResult.Success(session.id)
    }

    /**
     * A file uploaded by hand already carries its remote mapping, so a folder
     * added to backup later must claim it instead of uploading a second copy.
     */
    private suspend fun adoptLinkedUpload(candidate: File): Boolean {
        val existing = fileDao.byLocalPath(candidate.absolutePath) ?: return false
        if (existing.messageId == null || existing.backupState != BackupState.BACKED_UP) {
            return false
        }
        recordBackedUp(candidate, existing.id, existing.contentHash)
        SafeLog.d(TAG, "Claimed a manual upload already stored at this path")
        return true
    }

    /**
     * A file deleted inside the app but still on disk is restored rather than
     * uploaded again, so the drive keeps one copy with its original history.
     */
    private suspend fun reviveTrashedUpload(candidate: File): Boolean {
        val revived = fileRepository.reviveTrashedCopy(
            localPath = candidate.absolutePath,
            folderId = backupFolderIdFor(candidate)
        ) ?: return false
        if (!revived.hasRemoteCopy) return false
        recordBackedUp(candidate, revived.id, revived.contentHash)
        SafeLog.d(TAG, "Restored a trashed file instead of uploading a copy")
        return true
    }

    /**
     * After a reinstall the drive is rebuilt from Telegram, so uploaded files
     * have no path on this device. Matching them back to the identical local
     * file avoids uploading a second copy and keeps the local copy reclaimable.
     */
    private suspend fun adoptUploadedCopy(candidate: File): Boolean {
        val matches = fileDao.unlinkedRemoteMatches(candidate.name, candidate.length())
        if (matches.isEmpty()) return false

        val localHash = Hashing.sha256(candidate)
        val match = matches.firstOrNull { it.contentHash != null && it.contentHash == localHash }
            ?: matches.singleOrNull()?.takeIf { it.contentHash == null }
            ?: return false

        fileDao.setLocalPath(match.id, candidate.absolutePath)
        recordBackedUp(candidate, match.id, localHash)
        SafeLog.d(TAG, "Linked an existing upload to its local copy")
        return true
    }

    private suspend fun recordBackedUp(candidate: File, fileId: String, contentHash: String?) {
        backupDao.upsertRecord(
            BackupRecordEntity(
                id = UUID.randomUUID().toString(),
                sourcePath = candidate.absolutePath,
                fileId = fileId,
                sizeBytes = candidate.length(),
                modifiedAt = candidate.lastModified(),
                contentHash = contentHash,
                backedUpAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Mirrors the device folder structure at the drive root, so backed-up
     * files share the same tree as manual uploads instead of a separate silo.
     */
    private suspend fun backupFolderIdFor(source: File): String? {
        val parent = source.parentFile?.absolutePath ?: return null
        val relative = relativeToStorageRoot(parent)
        return if (relative.isEmpty()) null else folderPathResolver.resolveOrCreate(relative)
    }

    /** Strips the primary or removable volume mount point from [absolutePath]. */
    private fun relativeToStorageRoot(absolutePath: String): String {
        val normalized = absolutePath.trimEnd('/')
        val primary = storagePaths.externalStorageRoot?.absolutePath?.trimEnd('/')
        if (primary != null) {
            if (normalized == primary) return ""
            if (normalized.startsWith("$primary/")) return normalized.removePrefix("$primary/")
        }
        if (normalized.startsWith(VOLUME_MOUNT_ROOT)) {
            val rest = normalized.removePrefix(VOLUME_MOUNT_ROOT)
            val separator = rest.indexOf('/')
            return if (separator >= 0) rest.substring(separator + 1) else ""
        }
        return normalized.trimStart('/')
    }

    private suspend fun registerFile(
        source: File,
        folderId: String?,
        chatId: Long?
    ): String {
        fileDao.byLocalPath(source.absolutePath)?.let { return it.id }

        val mime = MimeTypes.fromFileName(source.name)
        val media = mediaMetadataExtractor.extract(source, mime)
        val now = System.currentTimeMillis()
        val entity = FileEntity(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            folderId = folderId,
            name = source.name,
            sizeBytes = source.length(),
            mimeType = mime,
            category = FileCategory.fromMimeType(mime),
            localPath = source.absolutePath,
            contentHash = null,
            messageId = null,
            remoteFileId = null,
            remoteUniqueId = null,
            backupState = BackupState.QUEUED,
            width = media.width,
            height = media.height,
            durationMs = media.durationMs,
            createdAt = source.lastModified().takeIf { it > 0 } ?: now,
            modifiedAt = source.lastModified().takeIf { it > 0 } ?: now,
            addedAt = now
        )
        fileDao.upsert(entity)
        return entity.id
    }

    override suspend fun pauseBackup(sessionId: String) {
        setTransfers(sessionId, from = TransferState.QUEUED, to = TransferState.PAUSED)
        setTransfers(sessionId, from = TransferState.RUNNING, to = TransferState.PAUSED)
        backupDao.setSessionStatus(sessionId, BackupSessionStatus.PAUSED, null)
    }

    override suspend fun resumeBackup(sessionId: String) {
        setTransfers(sessionId, from = TransferState.PAUSED, to = TransferState.QUEUED)
        backupDao.setSessionStatus(sessionId, BackupSessionStatus.RUNNING, null)
        transferRepository.recoverOrphanedTransfers()
    }

    override suspend fun cancelBackup(sessionId: String) {
        for (transfer in transferDao.bySession(sessionId)) {
            if (!transfer.state.isTerminal) {
                cancelTransfer(transfer)
            }
        }
        fileDao.deleteCancelledBackupEntries()
        backupDao.setSessionStatus(
            sessionId,
            BackupSessionStatus.CANCELLED,
            System.currentTimeMillis()
        )
    }

    override suspend fun syncActiveSessionWithSelection() {
        val session = backupDao.activeSession() ?: return
        val folders = settingsRepository.preferences.first().storageChatId
            ?.let { channelRepository.backupFolders(it) }
            .orEmpty()
        var dropped = 0
        for (transfer in transferDao.bySession(session.id)) {
            if (transfer.state.isTerminal) continue
            val sourcePath = transfer.fileId?.let { fileDao.byId(it)?.localPath } ?: continue
            if (folders.none { isInsideFolder(sourcePath, it) }) {
                cancelTransfer(transfer)
                dropped++
            }
        }
        if (dropped > 0) {
            fileDao.deleteCancelledBackupEntries()
            backupSessionTracker.refresh(session.id)
        }
    }

    private suspend fun cancelTransfer(transfer: TransferEntity) {
        transferDao.setState(transfer.id, TransferState.CANCELLED, System.currentTimeMillis())
        transfer.fileId?.let { fileDao.setBackupStateIfLocalOnly(it, BackupState.NONE) }
    }

    private fun isInsideFolder(path: String, folder: String): Boolean {
        val root = folder.trimEnd('/')
        return path == root || path.startsWith("$root/")
    }

    override suspend fun refreshActiveSession() {
        backupDao.activeSession()?.let { backupSessionTracker.refresh(it.id) }
    }

    private suspend fun setTransfers(sessionId: String, from: TransferState, to: TransferState) {
        for (transfer in transferDao.bySession(sessionId)) {
            if (transfer.state == from) {
                transferDao.setState(transfer.id, to, System.currentTimeMillis())
            }
        }
    }

    /**
     * Only the item itself is hidden by its name. A marker file such as
     * .nomedia says how a gallery should index a folder, never whether its
     * contents are worth keeping, so it excludes nothing but itself.
     */
    private fun isHiddenName(file: File): Boolean = file.name.startsWith('.')

    companion object {
        private const val TAG = "BackupRepository"
        private const val ENQUEUE_BATCH = 500
        private const val VOLUME_MOUNT_ROOT = "/storage/"
    }
}
