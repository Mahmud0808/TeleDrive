package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.data.local.entity.FileEntity
import com.drdisagree.teledrive.data.local.entity.FolderEntity
import com.drdisagree.teledrive.data.remote.telegram.RemoteFolderEntry
import com.drdisagree.teledrive.data.remote.telegram.RemoteFolderState
import com.drdisagree.teledrive.domain.model.BackupState
import com.drdisagree.teledrive.domain.model.FileCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteChangesSyncTest {

    private val harness = SyncHarness()
    private val database = harness.database
    private val telegram = harness.telegram

    @After
    fun tearDown() = harness.close()

    @Test
    fun `a rename and move made on another device arrive on catch up`() = runBlocking {
        telegram.documents = listOf(document(20, harness.caption(manifest("f1", "a.jpg"))))
        harness.newSync().syncOnStart()

        telegram.publishFolderState(RemoteFolderState(folders = listOf(folder("trips", "Trips"))))
        telegram.documents = listOf(
            document(
                20,
                harness.caption(
                    manifest("f1", "b.jpg", folderPath = "Trips", folderId = "trips", modifiedAt = 3_000)
                )
            )
        )
        val sync = harness.newSync()

        sync.incrementalSync()
        assertEquals("a.jpg", database.fileDao().byId("f1")!!.name)

        assertNotNull(sync.catchUpWithRemote())
        val row = database.fileDao().byId("f1")!!
        assertEquals("b.jpg", row.name)
        assertEquals("trips", row.folderId)
        assertEquals("Trips", database.folderDao().byId("trips")!!.name)
    }

    @Test
    fun `catch up is throttled and skipped while another sync runs`() = runBlocking {
        telegram.documents = listOf(document(20, harness.caption(manifest("f1", "a.jpg"))))
        val sync = harness.newSync()

        var nested: Any? = "not called"
        telegram.onFetch = {
            telegram.onFetch = {}
            nested = sync.catchUpWithRemote()
        }
        sync.fullResync()
        assertNull(nested)

        assertNull(sync.catchUpWithRemote())
    }

    @Test
    fun `catch up never shows the rebuilding indicator`() = runBlocking {
        telegram.documents = listOf(document(20, harness.caption(manifest("f1", "a.jpg"))))
        val sync = harness.newSync()
        val seen = mutableListOf<Boolean>()
        telegram.onFetch = { seen += sync.syncing.first() }

        sync.catchUpWithRemote()
        assertTrue(seen.isNotEmpty())
        assertFalse(seen.any { it })

        seen.clear()
        sync.fullResync()
        assertTrue(seen.all { it })
    }

    @Test
    fun `an upload finishing during a full scan keeps its remote copy`() = runBlocking {
        telegram.documents = listOf(document(20, harness.caption(manifest("f1", "a.jpg"))))
        val sync = harness.newSync()
        sync.fullResync()
        database.fileDao().upsert(localFile("uploading", messageId = null))
        database.fileDao().upsert(localFile("gone", messageId = 25))

        var firstPages = 0
        telegram.onFetch = { from ->
            if (from == 0L && ++firstPages == 2) {
                database.fileDao().setRemoteMapping(
                    id = "uploading",
                    chatId = TEST_CHAT,
                    messageId = 30,
                    remoteFileId = "remote-30",
                    remoteUniqueId = "unique-30",
                    state = BackupState.BACKED_UP
                )
            }
        }
        sync.fullResync()

        val uploaded = database.fileDao().byId("uploading")!!
        assertEquals(30L, uploaded.messageId)
        assertEquals(BackupState.BACKED_UP, uploaded.backupState)
        assertNull(database.fileDao().byId("gone")!!.messageId)
    }

    @Test
    fun `a folder flag changed on another device is applied and local offline access survives`() =
        runBlocking {
            database.folderDao().upsert(
                localFolder("x", "Work", modifiedAt = 5_000, changedAt = 5_000).copy(isAvailableOffline = true)
            )
            telegram.publishFolderState(
                RemoteFolderState(
                    folders = listOf(
                        folder("x", "Work", modifiedAt = 5_000, changedAt = 6_000).copy(hidden = true)
                    )
                )
            )

            harness.folderState.pull()

            val row = database.folderDao().byId("x")!!
            assertTrue(row.isHidden)
            assertTrue(row.isAvailableOffline)
            assertEquals(5_000L, row.modifiedAt)
            assertEquals(6_000L, row.changedAt)
        }

    @Test
    fun `a newer local folder change is kept`() = runBlocking {
        database.folderDao().upsert(localFolder("x", "Work", modifiedAt = 5_000, changedAt = 7_000))
        telegram.publishFolderState(
            RemoteFolderState(
                folders = listOf(
                    folder("x", "Work", modifiedAt = 5_000, changedAt = 6_000).copy(hidden = true)
                )
            )
        )

        harness.folderState.pull()

        assertFalse(database.folderDao().byId("x")!!.isHidden)
    }

    @Test
    fun `a folder document from an older app version is applied by modifiedAt`() = runBlocking {
        database.folderDao().upsert(localFolder("x", "Old name", modifiedAt = 1_000, changedAt = 1_000))
        telegram.publishRawFolderState(
            """{"v":1,"f":[{"id":"x","n":"New name","ct":500,"mt":2000}]}"""
        )

        harness.folderState.pull()

        val row = database.folderDao().byId("x")!!
        assertEquals("New name", row.name)
        assertEquals(2_000L, row.changedAt)
    }

    @Test
    fun `a folder created from a file path takes the flags from the folder document`() =
        runBlocking {
            harness.folderPaths.resolveOrCreate("Trips", "trips")
            telegram.publishFolderState(
                RemoteFolderState(folders = listOf(folder("trips", "Trips").copy(hidden = true)))
            )

            harness.folderState.pull()

            assertTrue(database.folderDao().byId("trips")!!.isHidden)
        }

    @Test
    fun `folder changes advance the sync clock but not the modified date`() = runBlocking {
        val dao = database.folderDao()
        dao.upsert(localFolder("x", "Work", modifiedAt = 1_000, changedAt = 1_000))

        dao.setHidden("x", true, 2_000)
        assertEquals(2_000L, dao.byId("x")!!.changedAt)
        dao.setArchived("x", true, 3_000)
        assertEquals(3_000L, dao.byId("x")!!.changedAt)
        dao.setFavorite("x", true, 4_000)
        assertEquals(4_000L, dao.byId("x")!!.changedAt)
        dao.moveToTrash("x", 5_000)
        assertEquals(5_000L, dao.byId("x")!!.changedAt)
        dao.restoreFromTrash("x", 6_000)
        assertEquals(6_000L, dao.byId("x")!!.changedAt)
        assertEquals(1_000L, dao.byId("x")!!.modifiedAt)

        dao.rename("x", "Jobs", 7_000)
        assertEquals(7_000L, dao.byId("x")!!.changedAt)
        assertEquals(7_000L, dao.byId("x")!!.modifiedAt)

        dao.setAvailableOffline("x", true)
        assertEquals(7_000L, dao.byId("x")!!.changedAt)
    }

    private fun folder(
        id: String,
        name: String,
        modifiedAt: Long = 1_000,
        changedAt: Long? = null
    ) = RemoteFolderEntry(
        id = id,
        name = name,
        createdAt = 500,
        modifiedAt = modifiedAt,
        changedAt = changedAt
    )

    private fun localFolder(id: String, name: String, modifiedAt: Long, changedAt: Long) =
        FolderEntity(
            id = id,
            chatId = TEST_CHAT,
            parentId = null,
            name = name,
            createdAt = 500,
            modifiedAt = modifiedAt,
            changedAt = changedAt
        )

    private fun localFile(id: String, messageId: Long?) = FileEntity(
        id = id,
        folderId = null,
        name = "$id.jpg",
        sizeBytes = 10,
        mimeType = "image/jpeg",
        category = FileCategory.IMAGE,
        localPath = "/storage/$id.jpg",
        contentHash = null,
        chatId = if (messageId != null) TEST_CHAT else null,
        messageId = messageId,
        remoteFileId = messageId?.let { "remote-$it" },
        remoteUniqueId = messageId?.let { "unique-$it" },
        backupState = if (messageId != null) BackupState.BACKED_UP else BackupState.QUEUED,
        createdAt = 1_000,
        modifiedAt = 1_000,
        addedAt = 1_000
    )
}
