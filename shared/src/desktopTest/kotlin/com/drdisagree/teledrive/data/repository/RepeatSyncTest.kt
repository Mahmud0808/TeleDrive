package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.core.transfer.FileParts
import com.drdisagree.teledrive.data.remote.telegram.RemoteFileManifest
import com.drdisagree.teledrive.data.remote.telegram.RemoteFolderEntry
import com.drdisagree.teledrive.data.remote.telegram.RemoteFolderState
import com.drdisagree.teledrive.domain.repository.SyncStats
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class RepeatSyncTest {

    private val harness = SyncHarness()
    private val telegram = harness.telegram

    @After
    fun tearDown() = harness.close()

    @Test
    fun `syncing an unchanged channel again rewrites nothing`() = runBlocking {
        telegram.publishFolderState(
            RemoteFolderState(folders = listOf(RemoteFolderEntry(id = "trips", name = "Trips", createdAt = 1, modifiedAt = 1)))
        )
        val split = manifest("f3", "big.mkv", size = 9_000_000_000)
        telegram.documents = listOf(
            document(20, harness.caption(manifest("f1", "a.jpg"))),
            document(21, harness.caption(manifest("f2", "b.jpg", folderPath = "Trips", folderId = "trips"))),
            document(22, harness.caption(FileParts.asFirstPart(split, 2))),
            document(
                23,
                harness.caption(
                    split.copy(
                        version = RemoteFileManifest.PART_VERSION,
                        partCount = 2,
                        partIndex = 1,
                        partOffset = FileParts.offsetOf(1),
                        partSize = FileParts.sizeOf(1, split.sizeBytes)
                    )
                )
            ),
            document(24, "")
        )

        stats(harness.newSync().fullResync())
        telegram.documents = telegram.documents.map {
            it.copy(remoteFileId = "${it.remoteFileId}-next-session")
        }
        val second = stats(harness.newSync().fullResync())

        assertEquals(0, second.inserted)
        assertEquals(0, second.updated)
    }

    private fun stats(result: AppResult<SyncStats>): SyncStats =
        (result as AppResult.Success).value
}
