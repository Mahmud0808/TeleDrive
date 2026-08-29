package com.drdisagree.teledrive.core.transfer

import android.content.Context
import com.drdisagree.teledrive.R
import com.drdisagree.teledrive.core.common.AppNotifications
import com.drdisagree.teledrive.data.local.dao.BackupDao
import com.drdisagree.teledrive.data.local.dao.TransferDao
import com.drdisagree.teledrive.data.local.entity.TransferEntity
import com.drdisagree.teledrive.domain.model.BackupSessionStatus
import com.drdisagree.teledrive.domain.model.TransferState
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Recomputes a backup session from the transfers that belong to it. Counting
 * live rows instead of incrementing counters keeps the session honest when
 * transfers are canceled, for example after a folder is unselected mid-run.
 */
class NotifyingBackupSessionTracker(
    private val transferDao: TransferDao,
    private val backupDao: BackupDao,
    private val settingsRepository: SettingsRepository,
    private val appNotifications: AppNotifications,
    private val context: Context
) : BackupSessionTracker {

    /** Recomputes whichever session is still running or paused. */
    override suspend fun refreshActive() {
        refresh(backupDao.activeSession()?.id)
    }

    override suspend fun refresh(sessionId: String?) {
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
