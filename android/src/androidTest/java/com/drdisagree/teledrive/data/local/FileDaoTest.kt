package com.drdisagree.teledrive.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.drdisagree.teledrive.data.local.database.TeleDriveDatabase
import com.drdisagree.teledrive.data.local.entity.FileEntity
import com.drdisagree.teledrive.data.local.entity.FolderEntity
import com.drdisagree.teledrive.domain.model.BackupState
import com.drdisagree.teledrive.domain.model.FileCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FileDaoTest {

    private lateinit var database: TeleDriveDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TeleDriveDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun file(
        id: String,
        folderId: String? = null,
        name: String = "$id.jpg"
    ) = FileEntity(
        id = id,
        folderId = folderId,
        name = name,
        sizeBytes = 100,
        mimeType = "image/jpeg",
        category = FileCategory.IMAGE,
        localPath = "/local/$name",
        contentHash = null,
        chatId = null,
        messageId = null,
        remoteFileId = null,
        remoteUniqueId = null,
        backupState = BackupState.NONE,
        createdAt = 1,
        modifiedAt = 1,
        addedAt = 1
    )

    private fun folder(id: String, parentId: String? = null) = FolderEntity(
        id = id,
        parentId = parentId,
        name = id,
        createdAt = 1,
        modifiedAt = 1
    )

    @Test
    fun insertAndReadBack() = runBlocking {
        database.fileDao().upsert(file("a"))
        assertEquals("a.jpg", database.fileDao().byId("a")?.name)
    }

    @Test
    fun renameUpdatesRow() = runBlocking {
        database.fileDao().upsert(file("a"))
        database.fileDao().rename("a", "new.jpg", 2)
        val updated = database.fileDao().byId("a")
        assertEquals("new.jpg", updated?.name)
        assertEquals(2L, updated?.modifiedAt)
    }

    @Test
    fun trashRoundTripRestoresFolder() = runBlocking {
        database.folderDao().upsert(folder("dir"))
        database.fileDao().upsert(file("a", folderId = "dir"))

        database.fileDao().moveToTrash(listOf("a"), trashedAt = 10)
        val trashed = database.fileDao().byId("a")
        assertNull(trashed?.folderId)
        assertEquals("dir", trashed?.preTrashFolderId)
        assertEquals(1, database.fileDao().observeTrashRoots().first().size)

        database.fileDao().restoreFromTrash(listOf("a"))
        val restored = database.fileDao().byId("a")
        assertEquals("dir", restored?.folderId)
        assertNull(restored?.trashedAt)
    }

    @Test
    fun trashedFolderHidesItsFilesFromTrashRoots() = runBlocking {
        database.folderDao().upsert(folder("dir"))
        database.folderDao().upsert(folder("nested", parentId = "dir"))
        database.fileDao().upsert(file("a", folderId = "dir"))
        database.fileDao().upsert(file("b", folderId = "nested"))
        database.fileDao().upsert(file("loose"))

        database.fileDao().moveToTrash(listOf("a", "b", "loose"), trashedAt = 10)
        database.folderDao().moveToTrash("nested", 10)
        database.folderDao().moveToTrash("dir", 10)

        val fileRoots = database.fileDao().observeTrashRoots().first()
        assertEquals(listOf("loose"), fileRoots.map { it.id })

        val folderRoots = database.folderDao().observeTrashRoots().first()
        assertEquals(listOf("dir"), folderRoots.map { it.id })

        val nestedIds = database.folderDao().trashedChildrenOf(listOf("dir")).map { it.id }
        assertEquals(listOf("nested"), nestedIds)
        assertEquals(
            listOf("a", "b"),
            database.fileDao().trashedInFolders(listOf("dir", "nested"))
                .map { it.id }
                .sorted()
        )
    }

    @Test
    fun deletingFolderKeepsFilesWithNullFolder() = runBlocking {
        database.folderDao().upsert(folder("dir"))
        database.fileDao().upsert(file("a", folderId = "dir"))
        database.folderDao().delete("dir")
        val orphan = database.fileDao().byId("a")
        assertNotNull(orphan)
        assertNull(orphan?.folderId)
    }

    @Test
    fun folderHierarchyQueries() = runBlocking {
        database.folderDao().upsert(folder("root"))
        database.folderDao().upsert(folder("child", parentId = "root"))
        assertEquals(1, database.folderDao().childrenOf("root").size)
        assertEquals(1, database.folderDao().childrenOf(null).size)
    }

    @Test
    fun remoteMappingUpdate() = runBlocking {
        database.fileDao().upsert(file("a"))
        database.fileDao().setRemoteMapping(
            "a", 42L, 99L, "remote", "unique", BackupState.BACKED_UP
        )
        val updated = database.fileDao().byId("a")
        assertEquals(42L, updated?.chatId)
        assertEquals(BackupState.BACKED_UP, updated?.backupState)
        assertEquals("a", database.fileDao().byRemoteUniqueId("unique")?.id)
    }

    @Test
    fun trashOlderThanFiltersByTimestamp() = runBlocking {
        database.fileDao().upsert(file("old"))
        database.fileDao().upsert(file("new"))
        database.fileDao().moveToTrash(listOf("old"), trashedAt = 100)
        database.fileDao().moveToTrash(listOf("new"), trashedAt = 200)
        val expired = database.fileDao().trashOlderThan(150)
        assertEquals(listOf("old"), expired.map { it.id })
    }
}
