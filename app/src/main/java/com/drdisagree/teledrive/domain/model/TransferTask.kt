package com.drdisagree.teledrive.domain.model

data class TransferTask(
    val id: String,
    val type: TransferType,
    val fileId: String?,
    val displayName: String,
    val sizeBytes: Long,
    val transferredBytes: Long,
    val state: TransferState,
    val priority: Int,
    val errorMessage: String?,
    val speedBytesPerSecond: Long,
    val stage: TransferStage? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?
) {
    val progress: Float
        get() = if (sizeBytes > 0) {
            (transferredBytes.toFloat() / sizeBytes).coerceIn(0f, 1f)
        } else 0f

    /** Estimated seconds remaining, null when speed is unknown. */
    val etaSeconds: Long?
        get() = speedBytesPerSecond.takeIf { it > 0 && sizeBytes > transferredBytes }
            ?.let { (sizeBytes - transferredBytes) / it }
}
