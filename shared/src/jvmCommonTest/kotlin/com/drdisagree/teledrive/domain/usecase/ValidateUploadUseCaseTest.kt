package com.drdisagree.teledrive.domain.usecase

import com.drdisagree.teledrive.core.common.AppError
import com.drdisagree.teledrive.core.telegram.TelegramLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidateUploadUseCaseTest {

    private val useCase = ValidateUploadUseCase()
    private val gib = 1024L * 1024 * 1024

    @Test
    fun `file within limits passes`() {
        assertNull(
            useCase(
                fileSizeBytes = 100 * 1024 * 1024,
                limits = TelegramLimits.REGULAR,
                availableLocalBytes = 10 * gib
            )
        )
    }

    @Test
    fun `file above regular limit fails`() {
        val error = useCase(
            fileSizeBytes = 3 * gib,
            limits = TelegramLimits.REGULAR,
            availableLocalBytes = 10 * gib
        )
        assertTrue(error is AppError.FileTooLarge)
    }

    @Test
    fun `premium limit allows larger files`() {
        assertNull(
            useCase(
                fileSizeBytes = 3 * gib,
                limits = TelegramLimits.PREMIUM,
                availableLocalBytes = 10 * gib
            )
        )
    }

    @Test
    fun `insufficient scratch space fails when encryption staging is needed`() {
        val error = useCase(
            fileSizeBytes = 1 * gib,
            limits = TelegramLimits.PREMIUM,
            availableLocalBytes = 500 * 1024 * 1024,
            requiredScratchBytes = 1 * gib
        )
        assertTrue(error is AppError.InsufficientStorage)
    }

    @Test
    fun `limits follow the part count Telegram allows`() {
        val maxPart = 512L * 1024
        assertEquals(4000 * maxPart, TelegramLimits.REGULAR.maxFileBytes)
        assertEquals(8000 * maxPart, TelegramLimits.PREMIUM.maxFileBytes)
    }

    @Test
    fun `file between the real cap and two gibibytes is too large`() {
        val error = useCase(
            fileSizeBytes = TelegramLimits.REGULAR.maxFileBytes + 1,
            limits = TelegramLimits.REGULAR,
            availableLocalBytes = 10 * gib
        )
        assertTrue(error is AppError.FileTooLarge)
        assertTrue(TelegramLimits.REGULAR.maxFileBytes < 2 * gib)
    }

    @Test
    fun `file between the real cap and four gibibytes is too large`() {
        val error = useCase(
            fileSizeBytes = TelegramLimits.PREMIUM.maxFileBytes + 1,
            limits = TelegramLimits.PREMIUM,
            availableLocalBytes = 10 * gib
        )
        assertTrue(error is AppError.FileTooLarge)
        assertTrue(TelegramLimits.PREMIUM.maxFileBytes < 4 * gib)
    }

    @Test
    fun `oversized file is accepted when it will be split`() {
        assertNull(
            useCase(
                fileSizeBytes = TelegramLimits.REGULAR.maxFileBytes + 1,
                limits = TelegramLimits.REGULAR,
                availableLocalBytes = 10 * gib,
                splitsIfTooLarge = true
            )
        )
    }

    @Test
    fun `empty file fails`() {
        assertTrue(
            useCase(
                fileSizeBytes = 0,
                limits = TelegramLimits.REGULAR,
                availableLocalBytes = gib
            ) is AppError.NotFound
        )
    }
}
