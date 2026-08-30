package com.drdisagree.teledrive.core.transfer

import com.drdisagree.teledrive.data.local.entity.TransferEntity
import com.drdisagree.teledrive.domain.model.TransferState

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
