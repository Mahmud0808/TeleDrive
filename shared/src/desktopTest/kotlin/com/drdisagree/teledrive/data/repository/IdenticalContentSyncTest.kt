package com.drdisagree.teledrive.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class IdenticalContentSyncTest {

    private val harness = SyncHarness()
    private val database = harness.database
    private val telegram = harness.telegram

    @After
    fun tearDown() = harness.close()

    @Test
    fun `two uploads with identical content stay separate files`() = runBlocking {
        val first = document(20, harness.caption(manifest("f1", "a.pdf", folderPath = "Docs")))
            .copy(uniqueFileId = "same")
        telegram.documents = listOf(first)
        harness.newSync().syncOnStart()

        val second = document(21, harness.caption(manifest("f2", "b.pdf", folderPath = "Other")))
            .copy(uniqueFileId = "same")
        telegram.documents = listOf(first, second)
        harness.newSync().fullResync()

        assertEquals(20L, database.fileDao().byId("f1")?.messageId)
        assertEquals(21L, database.fileDao().byId("f2")?.messageId)
    }
}
