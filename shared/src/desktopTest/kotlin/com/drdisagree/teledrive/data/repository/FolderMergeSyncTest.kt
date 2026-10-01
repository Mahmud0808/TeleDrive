package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.core.crypto.SecureFileDeleter
import com.drdisagree.teledrive.data.local.entity.FileEntity
import com.drdisagree.teledrive.data.local.entity.FolderEntity
import com.drdisagree.teledrive.data.local.entity.FolderTombstoneEntity
import com.drdisagree.teledrive.data.remote.telegram.RemoteFolderEntry
import com.drdisagree.teledrive.data.remote.telegram.RemoteFolderState
import com.drdisagree.teledrive.data.remote.telegram.RemoteFolderTombstone
import com.drdisagree.teledrive.domain.model.BackupState
import com.drdisagree.teledrive.domain.model.FileCategory
import com.drdisagree.teledrive.domain.repository.TransferRepository
import com.drdisagree.teledrive.testing.unused
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderMergeSyncTest {

    private val harness = SyncHarness()
    private val database = harness.database
    private val folders = database.folderDao()
    private val tombstones = database.folderTombstoneDao()
    private val telegram = harness.telegram
    private val recent = System.currentTimeMillis() - 60_000

    @After
    fun tearDown() = harness.close()

    @Test
    fun `pushing merges in folders created and renamed elsewhere`() = runBlocking {
        folders.upsert(folder("x", "Old", changedAt = recent + 1_000))
        folders.upsert(folder("mine", "Mine", changedAt = recent + 5_000))
        folders.markPendingPublish()
        telegram.publishFolderState(
            RemoteFolderState(
                folders = listOf(entry("x", "Renamed", recent + 2_000), entry("theirs", "Theirs", recent + 1_500))
            ),
            messageId = 900
        )

        harness.folderState.push()

        val uploaded = telegram.uploadedFolderState().folders.associate { it.id to it.name }
        assertEquals(mapOf("x" to "Renamed", "mine" to "Mine", "theirs" to "Theirs"), uploaded)
        assertEquals("Renamed", folders.byId("x")!!.name)
        assertEquals("Theirs", folders.byId("theirs")!!.name)
        assertEquals(listOf(900L), telegram.deletedMessages)
    }

    @Test
    fun `a folder deleted elsewhere disappears and its files move to the root`() = runBlocking {
        folders.upsert(folder("x", "Work", changedAt = recent + 1_000))
        database.fileDao().upsert(file("f1", folderId = "x"))
        telegram.publishFolderState(RemoteFolderState(deleted = listOf(RemoteFolderTombstone("x", recent + 2_000))))

        harness.folderState.pull()

        assertNull(folders.byId("x"))
        val row = database.fileDao().byId("f1")
        assertNotNull(row)
        assertNull(row!!.folderId)
        assertEquals(listOf("x"), tombstones.inChat(TEST_CHAT).map { it.id })
    }

    @Test
    fun `a folder changed after its deletion elsewhere is kept`() = runBlocking {
        folders.upsert(folder("x", "Work", changedAt = recent + 3_000))
        telegram.publishFolderState(RemoteFolderState(deleted = listOf(RemoteFolderTombstone("x", recent + 2_000))))

        harness.folderState.pull()

        assertNotNull(folders.byId("x"))
        assertTrue(tombstones.inChat(TEST_CHAT).isEmpty())
    }

    @Test
    fun `a newer subfolder survives its parent's deletion at the root`() = runBlocking {
        folders.upsert(folder("p", "Parent", changedAt = recent + 1_000))
        folders.upsert(folder("c", "Child", parentId = "p", changedAt = recent + 3_000))
        telegram.publishFolderState(RemoteFolderState(deleted = listOf(RemoteFolderTombstone("p", recent + 2_000))))

        harness.folderState.pull()

        assertNull(folders.byId("p"))
        val child = folders.byId("c")
        assertNotNull(child)
        assertNull(child!!.parentId)
    }

    @Test
    fun `pulling never queues a publish or uploads anything`() = runBlocking {
        folders.upsert(folder("x", "Old", changedAt = recent + 1_000))
        telegram.publishFolderState(
            RemoteFolderState(
                folders = listOf(entry("x", "Renamed", recent + 2_000), entry("y", "New", recent + 2_000)),
                deleted = listOf(RemoteFolderTombstone("z", recent + 2_000))
            )
        )

        harness.folderState.pull()

        assertEquals("Renamed", folders.byId("x")!!.name)
        assertEquals(0, folders.pendingPublishCount())
        assertTrue(telegram.uploads.isEmpty())
        assertEquals(0, harness.scheduler.kicks)
    }

    @Test
    fun `updating a folder keeps its subfolders and the files inside`() = runBlocking {
        folders.upsert(folder("p", "Parent", changedAt = recent + 1_000))
        folders.upsert(folder("c", "Child", parentId = "p", changedAt = recent + 1_000))
        database.fileDao().upsert(file("f1", folderId = "p"))
        telegram.publishFolderState(
            RemoteFolderState(
                folders = listOf(entry("p", "Renamed", recent + 2_000), entry("c", "Child", recent + 1_000, parentId = "p"))
            )
        )

        harness.folderState.pull()

        assertEquals("Renamed", folders.byId("p")!!.name)
        assertEquals("p", folders.byId("c")!!.parentId)
        assertEquals("p", database.fileDao().byId("f1")!!.folderId)
    }

    @Test
    fun `a deletion older than the retention window is ignored`() = runBlocking {
        folders.upsert(folder("x", "Work", changedAt = 1_000))
        telegram.publishFolderState(RemoteFolderState(deleted = listOf(RemoteFolderTombstone("x", 2_000))))

        harness.folderState.pull()

        assertNotNull(folders.byId("x"))
        assertTrue(tombstones.inChat(TEST_CHAT).isEmpty())
    }

    @Test
    fun `a failed download stops the push before anything is replaced`() = runBlocking {
        folders.upsert(folder("x", "Mine", changedAt = recent + 1_000))
        folders.markPendingPublish()
        telegram.publishFolderState(RemoteFolderState(folders = listOf(entry("y", "Theirs", recent + 1_000))))
        telegram.failDownloads = true

        val result = runCatching { harness.folderState.push() }

        assertTrue(result.isFailure)
        assertTrue(telegram.uploads.isEmpty())
        assertTrue(telegram.deletedMessages.isEmpty())
    }

    @Test
    fun `a deleted folder id is never reused for a folder recreated from a file`() = runBlocking {
        tombstones.upsert(listOf(FolderTombstoneEntity("x", TEST_CHAT, recent + 2_000)))

        val id = harness.folderPaths.resolveOrCreate("Trips", "x")

        assertNotNull(id)
        assertNotEquals("x", id)
        assertEquals("Trips", folders.byId(id!!)!!.name)
    }

    @Test
    fun `emptying the trash sends the deletion to other devices`() = runBlocking {
        folders.upsert(folder("t", "Old stuff", changedAt = recent + 1_000).copy(trashedAt = recent + 1_500))
        val trash = TrashRepositoryImpl(
            storagePaths = harness.storagePaths,
            fileDao = database.fileDao(),
            folderDao = folders,
            backupDao = database.backupDao(),
            thumbnailDao = database.thumbnailDao(),
            telegramClient = telegram,
            secureFileDeleter = SecureFileDeleter(),
            publishScheduler = harness.scheduler,
            pendingDeleteDao = database.pendingDeleteDao(),
            filePartDao = database.filePartDao(),
            activeChannel = harness.activeChannel,
            transferRepository = unused<TransferRepository>(),
            tombstoneDao = tombstones
        )

        trash.emptyTrash()
        harness.folderState.push()

        assertNull(folders.byId("t"))
        assertEquals(listOf("t"), telegram.uploadedFolderState().deleted.map { it.id })
    }

    private fun folder(
        id: String,
        name: String,
        parentId: String? = null,
        changedAt: Long
    ) = FolderEntity(
        id = id,
        chatId = TEST_CHAT,
        parentId = parentId,
        name = name,
        createdAt = recent,
        modifiedAt = changedAt,
        changedAt = changedAt
    )

    private fun entry(id: String, name: String, clock: Long, parentId: String? = null) = RemoteFolderEntry(
        id = id,
        parentId = parentId,
        name = name,
        createdAt = recent,
        modifiedAt = clock,
        changedAt = clock
    )

    private fun file(id: String, folderId: String?) = FileEntity(
        id = id,
        folderId = folderId,
        name = "$id.jpg",
        sizeBytes = 10,
        mimeType = "image/jpeg",
        category = FileCategory.IMAGE,
        localPath = null,
        contentHash = null,
        chatId = TEST_CHAT,
        messageId = 40,
        remoteFileId = "remote-40",
        remoteUniqueId = "unique-40",
        backupState = BackupState.BACKED_UP,
        createdAt = 1_000,
        modifiedAt = 1_000,
        addedAt = 1_000
    )
}
