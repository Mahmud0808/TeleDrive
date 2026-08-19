package com.drdisagree.teledrive.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferTaskTest {

    private fun task(
        size: Long,
        transferred: Long,
        speed: Long = 0,
        state: TransferState = TransferState.RUNNING
    ) = TransferTask(
        id = "t",
        type = TransferType.UPLOAD,
        fileId = "f",
        displayName = "file",
        sizeBytes = size,
        transferredBytes = transferred,
        state = state,
        priority = 0,
        errorMessage = null,
        speedBytesPerSecond = speed,
        createdAt = 0,
        updatedAt = 0,
        completedAt = null
    )

    @Test
    fun `progress is fraction of size`() {
        assertEquals(0.5f, task(100, 50).progress)
    }

    @Test
    fun `progress clamps and handles zero size`() {
        assertEquals(1f, task(100, 150).progress)
        assertEquals(0f, task(0, 0).progress)
    }

    @Test
    fun `eta computed from speed`() {
        assertEquals(50L, task(1000, 500, speed = 10).etaSeconds)
    }

    @Test
    fun `eta null without speed`() {
        assertNull(task(1000, 500, speed = 0).etaSeconds)
    }

    @Test
    fun `state helpers`() {
        assertTrue(TransferState.COMPLETED.isTerminal)
        assertTrue(TransferState.QUEUED.isActive)
        assertTrue(!TransferState.PAUSED.isActive && !TransferState.PAUSED.isTerminal)
    }
}
