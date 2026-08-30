package com.drdisagree.teledrive.core.transfer

import com.drdisagree.teledrive.data.local.dao.BackupDao
import com.drdisagree.teledrive.data.local.dao.TransferDao
import com.drdisagree.teledrive.domain.model.BackupSessionStatus

/**
 * Recomputes a backup session from the transfers that belong to it. Counting
 * live rows instead of incrementing counters keeps the session honest when
 * transfers are canceled, for example after a folder is unselected mid-run.
 */
open class CountingBackupSessionTracker(
    private val transferDao: TransferDao,
    private val backupDao: BackupDao
) : BackupSessionTracker {

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

        if (counts.settled && wasRunning && counts.totalFiles > 0) {
            onSessionSettled(counts)
        }
    }

    protected open suspend fun onSessionSettled(counts: BackupSessionCounts) {
    }
}
