package com.drdisagree.teledrive.domain.usecase

import com.drdisagree.teledrive.core.common.AppError
import com.drdisagree.teledrive.core.telegram.TelegramLimits
import javax.inject.Inject

/**
 * Pre-flight checks before a transfer is queued, so obvious failures surface
 * immediately instead of near the end of a long upload.
 */
class ValidateUploadUseCase @Inject constructor() {

    /**
     * @param requiredScratchBytes extra local space needed, e.g. for an
     * encrypted staging copy of the file.
     */
    operator fun invoke(
        fileSizeBytes: Long,
        limits: TelegramLimits,
        availableLocalBytes: Long,
        requiredScratchBytes: Long = 0
    ): AppError? = when {
        fileSizeBytes <= 0 ->
            AppError.NotFound
        fileSizeBytes > limits.maxFileBytes ->
            AppError.FileTooLarge(fileSizeBytes, limits.maxFileBytes)
        requiredScratchBytes > 0 && availableLocalBytes < requiredScratchBytes + STORAGE_MARGIN_BYTES ->
            AppError.InsufficientStorage(
                requiredScratchBytes + STORAGE_MARGIN_BYTES,
                availableLocalBytes
            )
        else -> null
    }

    companion object {
        private const val STORAGE_MARGIN_BYTES = 100L * 1024 * 1024
    }
}
