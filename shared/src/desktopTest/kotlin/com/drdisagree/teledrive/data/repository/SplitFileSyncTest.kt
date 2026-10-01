package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.core.telegram.RemoteDocument
import com.drdisagree.teledrive.core.transfer.FileParts
import com.drdisagree.teledrive.data.remote.telegram.RemoteFileManifest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitFileSyncTest {

    private val harness = SyncHarness()
    private val database = harness.database
    private val telegram = harness.telegram
    private val scheduler = harness.scheduler
    private val sync = harness.newSync()

    @After
    fun tearDown() = harness.close()

    @Test
    fun `fresh install restores part 0 of a split file whose caption lost its part fields`() =
        runBlocking {
            telegram.documents = splitFile(damagedFirstPart = true)

            sync.fullResync()

            val parts = database.filePartDao().partsOf(SPLIT_ID)
            assertEquals(listOf(0, 1, 2), parts.map { it.partIndex })
            val first = parts.first()
            assertEquals(10L, first.messageId)
            assertEquals(0L, first.plainOffset)
            assertEquals(FileParts.PART_SIZE, first.plainSize)

            val row = database.fileDao().byId(SPLIT_ID)
            assertNotNull(row)
            assertEquals(10L, row!!.messageId)
            assertEquals(3, row.partCount)
            assertTrue(row.pendingPublish)
            assertEquals(1, scheduler.kicks)
        }

    @Test
    fun `later parts never recreate the folder a split file was moved out of`() = runBlocking {
        telegram.documents = splitFile(damagedFirstPart = true)

        sync.fullResync()

        val names = database.folderDao().allFolders(TEST_CHAT).map { it.name }
        assertEquals(listOf("New"), names)
        assertEquals(NEW_FOLDER_ID, database.fileDao().byId(SPLIT_ID)!!.folderId)
    }

    @Test
    fun `a repaired part 0 is not repaired again on later syncs`() = runBlocking {
        telegram.documents = splitFile(damagedFirstPart = true)

        sync.fullResync()
        sync.fullResync()
        assertEquals(1, scheduler.kicks)

        database.fileDao().clearPendingPublish(SPLIT_ID)
        sync.fullResync()
        assertEquals(1, scheduler.kicks)
        assertFalse(database.fileDao().byId(SPLIT_ID)!!.pendingPublish)
    }

    @Test
    fun `a fixed caption ends the repair for good`() = runBlocking {
        telegram.documents = splitFile(damagedFirstPart = true)
        sync.fullResync()
        database.fileDao().clearPendingPublish(SPLIT_ID)

        telegram.documents = splitFile(damagedFirstPart = false)
        harness.newSync().fullResync()

        assertEquals(1, scheduler.kicks)
        assertFalse(database.fileDao().byId(SPLIT_ID)!!.pendingPublish)
        assertEquals(listOf(0, 1, 2), database.filePartDao().partsOf(SPLIT_ID).map { it.partIndex })
    }

    @Test
    fun `a healthy split file is left alone`() = runBlocking {
        telegram.documents = splitFile(damagedFirstPart = false)

        sync.fullResync()

        assertEquals(listOf(0, 1, 2), database.filePartDao().partsOf(SPLIT_ID).map { it.partIndex })
        assertFalse(database.fileDao().byId(SPLIT_ID)!!.pendingPublish)
        assertEquals(0, scheduler.kicks)
    }

    @Test
    fun `an ordinary file gains no parts and no repair`() = runBlocking {
        telegram.documents = listOf(document(20, harness.caption(manifest("single", "photo.jpg"))))

        sync.fullResync()

        val row = database.fileDao().byId("single")
        assertNotNull(row)
        assertEquals(0, row!!.partCount)
        assertFalse(row.pendingPublish)
        assertTrue(database.filePartDao().partsOf("single").isEmpty())
        assertEquals(0, scheduler.kicks)
    }

    @Test
    fun `republishing a split file keeps its part fields and icon`() = runBlocking {
        telegram.documents = splitFile(damagedFirstPart = false)
        sync.fullResync()
        val publisher = FileManifestPublisher(
            telegram, harness.codec, harness.folderPaths, database.filePartDao()
        )
        val row = database.fileDao().byId(SPLIT_ID)!!.copy(name = "renamed.mkv", iconFileId = "icon-1")

        publisher.publish(row)

        val written = harness.codec.decode(telegram.editedCaptions.getValue(10L))!!
        assertTrue(written.isPart)
        assertEquals(0, written.partIndex)
        assertEquals(3, written.partCount)
        assertEquals(0L, written.partOffset)
        assertEquals(FileParts.PART_SIZE, written.partSize)
        assertEquals("renamed.mkv", written.name)
        assertEquals("icon-1", written.iconFileId)
    }

    @Test
    fun `republishing an ordinary file writes no part fields`() = runBlocking {
        telegram.documents = listOf(document(20, harness.caption(manifest("single", "photo.jpg"))))
        sync.fullResync()
        val publisher = FileManifestPublisher(
            telegram, harness.codec, harness.folderPaths, database.filePartDao()
        )

        publisher.publish(database.fileDao().byId("single")!!)

        val written = harness.codec.decode(telegram.editedCaptions.getValue(20L))!!
        assertFalse(written.isPart)
        assertEquals(0, written.partCount)
        assertNull(written.iconFileId)
    }

    private fun splitFile(damagedFirstPart: Boolean): List<RemoteDocument> {
        val whole = manifest(SPLIT_ID, "movie.mkv", SPLIT_SIZE, "New", NEW_FOLDER_ID)
        val stale = whole.copy(folderPath = "Old", folderId = "old-folder")
        val part0 = if (damagedFirstPart) whole else FileParts.asFirstPart(whole, 3)
        return listOf(
            document(12, harness.caption(partOf(stale, 2)), FileParts.PART_SIZE),
            document(11, harness.caption(partOf(stale, 1)), FileParts.PART_SIZE),
            document(10, harness.caption(part0), FileParts.PART_SIZE)
        )
    }

    private fun partOf(manifest: RemoteFileManifest, index: Int) = manifest.copy(
        version = RemoteFileManifest.PART_VERSION,
        partCount = 3,
        partIndex = index,
        partOffset = FileParts.offsetOf(index),
        partSize = FileParts.sizeOf(index, SPLIT_SIZE)
    )

    private companion object {
        const val SPLIT_ID = "split"
        const val NEW_FOLDER_ID = "new-folder"
        val SPLIT_SIZE = FileParts.PART_SIZE * 2 + 1_000
    }
}
