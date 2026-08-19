package com.drdisagree.teledrive.core.transfer

import com.drdisagree.teledrive.data.local.entity.TransferEntity
import com.drdisagree.teledrive.domain.model.TransferState
import com.drdisagree.teledrive.domain.model.TransferType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSessionCountsTest {

    private fun transfer(
        id: String,
        state: TransferState,
        sizeBytes: Long = 100
    ) = TransferEntity(
        id = id,
        type = TransferType.BACKUP,
        fileId = id,
        displayName = id,
        localPath = "/sdcard/$id",
        chatId = null,
        messageId = null,
        remoteFileId = null,
        sizeBytes = sizeBytes,
        state = state,
        backupSessionId = "session",
        createdAt = 1,
        updatedAt = 1
    )

    @Test
    fun cancelledTransfersLeaveTheTotals() {
        val counts = countSession(
            listOf(
                transfer("a", TransferState.COMPLETED, sizeBytes = 50),
                transfer("b", TransferState.CANCELLED, sizeBytes = 900),
                transfer("c", TransferState.QUEUED, sizeBytes = 70)
            )
        )
        assertEquals(2, counts.totalFiles)
        assertEquals(1, counts.completedFiles)
        assertEquals(120L, counts.totalBytes)
        assertEquals(50L, counts.transferredBytes)
        assertFalse(counts.settled)
    }

    @Test
    fun sessionSettlesWhenNothingIsPending() {
        val counts = countSession(
            listOf(
                transfer("a", TransferState.COMPLETED),
                transfer("b", TransferState.FAILED)
            )
        )
        assertEquals(2, counts.totalFiles)
        assertEquals(1, counts.failedFiles)
        assertTrue(counts.settled)
    }

    @Test
    fun cancellingEverythingEmptiesTheSession() {
        val counts = countSession(
            listOf(
                transfer("a", TransferState.CANCELLED),
                transfer("b", TransferState.CANCELLED)
            )
        )
        assertEquals(0, counts.totalFiles)
        assertEquals(0L, counts.totalBytes)
        assertTrue(counts.settled)
    }
}
