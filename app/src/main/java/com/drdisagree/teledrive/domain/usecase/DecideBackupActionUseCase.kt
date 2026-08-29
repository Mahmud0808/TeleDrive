package com.drdisagree.teledrive.domain.usecase

import com.drdisagree.teledrive.domain.model.BackupDecision
import com.drdisagree.teledrive.domain.model.Exclusion

/**
 * Incremental backup decision for a single candidate file. Change detection
 * uses size + mtime first and falls back to the content hash when only the
 * mtime changed, so touched-but-identical files are not re-uploaded.
 */
class DecideBackupActionUseCase(
    private val evaluateExclusions: EvaluateExclusionsUseCase
) {

    data class ExistingRecord(
        val sizeBytes: Long,
        val modifiedAt: Long,
        val contentHash: String?
    )

    operator fun invoke(
        candidate: EvaluateExclusionsUseCase.Candidate,
        modifiedAt: Long,
        existingRecord: ExistingRecord?,
        exclusions: List<Exclusion>,
        maxFileSizeBytes: Long,
        contentHashProvider: () -> String?
    ): BackupDecision {
        if (evaluateExclusions(candidate, exclusions)) return BackupDecision.SKIP_EXCLUDED
        if (maxFileSizeBytes in 1..<candidate.sizeBytes) return BackupDecision.SKIP_TOO_LARGE

        if (existingRecord != null) {
            val sameSizeAndTime = existingRecord.sizeBytes == candidate.sizeBytes &&
                    existingRecord.modifiedAt == modifiedAt
            if (sameSizeAndTime) return BackupDecision.SKIP_UNCHANGED

            if (existingRecord.sizeBytes == candidate.sizeBytes &&
                existingRecord.contentHash != null
            ) {
                val currentHash = contentHashProvider()
                if (currentHash != null && currentHash == existingRecord.contentHash) {
                    return BackupDecision.SKIP_UNCHANGED
                }
            }
        }
        return BackupDecision.BACKUP
    }
}
