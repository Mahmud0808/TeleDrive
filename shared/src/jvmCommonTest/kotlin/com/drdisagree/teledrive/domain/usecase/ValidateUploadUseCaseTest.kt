package com.drdisagree.teledrive.domain.usecase

import com.drdisagree.teledrive.core.common.AppError
import com.drdisagree.teledrive.core.telegram.TelegramLimits
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
