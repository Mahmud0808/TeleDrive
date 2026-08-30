package com.drdisagree.teledrive.domain.model

data class BackupSession(
    val id: String,
    val trigger: BackupTrigger,
    val status: BackupSessionStatus,
    val totalFiles: Int,
    val completedFiles: Int,
    val failedFiles: Int,
    val skippedFiles: Int,
    val totalBytes: Long,
    val transferredBytes: Long,
    val startedAt: Long,
    val completedAt: Long?
) {
    val progress: Float
        get() = if (totalFiles > 0) {
            ((completedFiles + failedFiles).toFloat() / totalFiles).coerceIn(0f, 1f)
        } else 0f
}
