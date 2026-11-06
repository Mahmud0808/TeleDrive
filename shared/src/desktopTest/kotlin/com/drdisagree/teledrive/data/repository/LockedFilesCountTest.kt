package com.drdisagree.teledrive.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class LockedFilesCountTest {

    private val harness = SyncHarness()
    private val telegram = harness.telegram

    @After
    fun tearDown() = harness.close()

    @Test
    fun `a sync reports how many files the device cannot decrypt`() = runBlocking {
        telegram.documents = listOf(
            document(20, harness.caption(manifest("f1", "a.jpg"))),
            document(21, "tde1:sealed"),
            document(22, "tde1:sealed")
        )
        val sync = harness.newSync()

        sync.fullResync()

        assertEquals(2, sync.lockedFileCounts.first()[TEST_CHAT])
    }

    @Test
    fun `a full sync after the key is back clears the count`() = runBlocking {
        telegram.documents = listOf(document(21, "tde1:sealed"))
        val sync = harness.newSync()
        sync.fullResync()

        telegram.documents = listOf(document(20, harness.caption(manifest("f1", "a.jpg"))))
        sync.fullResync()

        assertEquals(0, sync.lockedFileCounts.first()[TEST_CHAT])
    }
}
