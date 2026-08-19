package com.drdisagree.teledrive.core.transfer

import com.drdisagree.teledrive.data.local.dao.BackupDao
import com.drdisagree.teledrive.data.local.dao.TransferDao
import com.drdisagree.teledrive.data.local.entity.TransferEntity
import com.drdisagree.teledrive.domain.model.BackupSessionStatus
import com.drdisagree.teledrive.domain.model.TransferState
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context
import com.drdisagree.teledrive.R
import com.drdisagree.teledrive.core.common.AppNotifications
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first

/**
 * Recomputes a backup session from the transfers that belong to it. Counting
 * live rows instead of incrementing counters keeps the session honest when
 * transfers are cancelled, for example after a folder is unselected mid-run.
 */
@Singleton
class BackupSessionTracker @Inject constructor(
    private val transferDao: TransferDao,
    private val backupDao: BackupDao,
    private val settingsRepository: SettingsRepository,
    private val appNotifications: AppNotifications,
    @param:ApplicationContext private val context: Context
) {

    /** Recomputes whichever session is still running or paused. */
    suspend fun refreshActive() {
        refresh(backupDao.activeSession()?.id)
    }

    suspend fun refresh(sessionId: String?) {
        val id = sessionId ?: return
        val session = backupDao.sessionById(id) ?: return
        if (session.status == BackupSessionStatus.CANCELLED) return

        val counts = countSession(transferDao.bySession(id))
        val wasRunning = session.status != BackupSessionStatus.COMPLETED
        backupDao.updateSession(
            session.copy(
                totalFiles = counts.totalFiles,
                completedFiles = counts.completedFiles,
                failedFiles = counts.failedFiles,
                totalBytes = counts.totalBytes,
                transferredBytes = counts.transferredBytes,
                status = when {
                    counts.settled && counts.totalFiles == 0 -> BackupSessionStatus.CANCELLED
                    counts.settled -> BackupSessionStatus.COMPLETED
                    counts.allPaused -> BackupSessionStatus.PAUSED
                    else -> BackupSessionStatus.RUNNING
                },
                completedAt = if (counts.settled) {
                    System.currentTimeMillis()
                } else {
                    session.completedAt
                }
            )
        )

        if (counts.settled && wasRunning && counts.totalFiles > 0 &&
            settingsRepository.preferences.first().backupNotifications
        ) {
            appNotifications.notifyBackupResult(
                title = context.getString(R.string.notification_backup_complete),
                message = context.getString(
                    R.string.notification_backup_summary,
                    counts.completedFiles,
                    counts.totalFiles
                )
            )
        }
    }
}

data class BackupSessionCounts(
    val totalFiles: Int,
    val completedFiles: Int,
    val failedFiles: Int,
    val totalBytes: Long,
    val transferredBytes: Long,
    val settled: Boolean,
    val allPaused: Boolean
)

fun countSession(transfers: List<TransferEntity>): BackupSessionCounts {
    val remaining = transfers.filter { it.state != TransferState.CANCELLED }
    val completed = remaining.filter { it.state == TransferState.COMPLETED }
    return BackupSessionCounts(
        totalFiles = remaining.size,
        completedFiles = completed.size,
        failedFiles = remaining.count { it.state == TransferState.FAILED },
        totalBytes = remaining.sumOf { it.sizeBytes },
        transferredBytes = completed.sumOf { it.sizeBytes },
        settled = remaining.none { !it.state.isTerminal },
        allPaused = remaining.any { it.state == TransferState.PAUSED } &&
            remaining.none { it.state == TransferState.QUEUED || it.state == TransferState.RUNNING }
    )
}
