package com.drdisagree.teledrive.domain.usecase

import com.drdisagree.teledrive.domain.model.BackupDecision
import com.drdisagree.teledrive.domain.model.Exclusion
import com.drdisagree.teledrive.domain.model.ExclusionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecideBackupActionUseCaseTest {

    private val useCase = DecideBackupActionUseCase(EvaluateExclusionsUseCase())

    private fun candidate(size: Long = 1000) = EvaluateExclusionsUseCase.Candidate(
        absolutePath = "/dcim/photo.jpg",
        sizeBytes = size,
        mimeType = "image/jpeg",
        isHidden = false
    )

    @Test
    fun `new file is backed up`() {
        val decision = useCase(
            candidate = candidate(),
            modifiedAt = 100,
            existingRecord = null,
            exclusions = emptyList(),
            maxFileSizeBytes = 0,
            contentHashProvider = { error("hash must not be computed for new files") }
        )
        assertEquals(BackupDecision.BACKUP, decision)
    }

    @Test
    fun `unchanged size and mtime skips without hashing`() {
        var hashed = false
        val decision = useCase(
            candidate = candidate(size = 1000),
            modifiedAt = 100,
            existingRecord = DecideBackupActionUseCase.ExistingRecord(1000, 100, "abc"),
            exclusions = emptyList(),
            maxFileSizeBytes = 0,
            contentHashProvider = { hashed = true; "abc" }
        )
        assertEquals(BackupDecision.SKIP_UNCHANGED, decision)
        assertFalse(hashed)
    }

    @Test
    fun `same size different mtime falls back to hash comparison`() {
        var hashed = false
        val decision = useCase(
            candidate = candidate(size = 1000),
            modifiedAt = 200,
            existingRecord = DecideBackupActionUseCase.ExistingRecord(1000, 100, "abc"),
            exclusions = emptyList(),
            maxFileSizeBytes = 0,
            contentHashProvider = { hashed = true; "abc" }
        )
        assertEquals(BackupDecision.SKIP_UNCHANGED, decision)
        assertTrue(hashed)
    }

    @Test
    fun `changed content is re-uploaded`() {
        val decision = useCase(
            candidate = candidate(size = 1000),
            modifiedAt = 200,
            existingRecord = DecideBackupActionUseCase.ExistingRecord(1000, 100, "abc"),
            exclusions = emptyList(),
            maxFileSizeBytes = 0,
            contentHashProvider = { "different" }
        )
        assertEquals(BackupDecision.BACKUP, decision)
    }

    @Test
    fun `changed size is re-uploaded without hashing`() {
        val decision = useCase(
            candidate = candidate(size = 2000),
            modifiedAt = 200,
            existingRecord = DecideBackupActionUseCase.ExistingRecord(1000, 100, "abc"),
            exclusions = emptyList(),
            maxFileSizeBytes = 0,
            contentHashProvider = { error("hash not needed when size changed") }
        )
        assertEquals(BackupDecision.BACKUP, decision)
    }

    @Test
    fun `excluded file is skipped`() {
        val decision = useCase(
            candidate = candidate(),
            modifiedAt = 100,
            existingRecord = null,
            exclusions = listOf(
                Exclusion("id", ExclusionType.EXTENSION, "jpg", enabled = true, createdAt = 0)
            ),
            maxFileSizeBytes = 0,
            contentHashProvider = { null }
        )
        assertEquals(BackupDecision.SKIP_EXCLUDED, decision)
    }

    @Test
    fun `file above size limit is skipped`() {
        val decision = useCase(
            candidate = candidate(size = 5000),
            modifiedAt = 100,
            existingRecord = null,
            exclusions = emptyList(),
            maxFileSizeBytes = 4000,
            contentHashProvider = { null }
        )
        assertEquals(BackupDecision.SKIP_TOO_LARGE, decision)
    }

    @Test
    fun `zero size limit means unlimited`() {
        val decision = useCase(
            candidate = candidate(size = Long.MAX_VALUE / 2),
            modifiedAt = 100,
            existingRecord = null,
            exclusions = emptyList(),
            maxFileSizeBytes = 0,
            contentHashProvider = { null }
        )
        assertEquals(BackupDecision.BACKUP, decision)
    }
}
