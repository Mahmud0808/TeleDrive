package com.drdisagree.teledrive.data.local

import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvailableOfflineMigrationTest {

    @Test
    fun `upgrading from version 13 keeps nested folders, their files and every flag`() =
        runBlocking {
            val file = File.createTempFile("teledrive-v13", ".db")
            file.delete()
            createLegacyDatabase(
                file,
                version = 13,
                folder("root", parentId = null, pinned = true),
                folder("child", parentId = "root", pinned = false),
                folder("other", parentId = null, pinned = false),
                file("a", folderId = "child", pinned = false, favorite = false),
                file("b", folderId = null, pinned = true, favorite = true),
                file("c", folderId = "other", pinned = false, favorite = false)
            )

            val database = openMigrated(file)
            try {
                val folders = database.folderDao()
                val files = database.fileDao()

                assertEquals("root", folders.byId("child")!!.parentId)
                assertTrue(folders.byId("root")!!.isAvailableOffline)
                assertFalse(folders.byId("child")!!.isAvailableOffline)
                assertFalse(folders.byId("other")!!.isAvailableOffline)

                assertEquals("child", files.byId("a")!!.folderId)
                assertEquals("other", files.byId("c")!!.folderId)
                assertTrue(files.byId("b")!!.isAvailableOffline)
                assertTrue(files.byId("b")!!.isFavorite)
                assertFalse(files.byId("a")!!.isAvailableOffline)
                assertEquals(1_000L, files.byId("a")!!.modifiedAt)

                assertEquals(
                    listOf("root"),
                    folders.observeAvailableOffline(7).first().map { it.folder.id }
                )
                assertEquals(
                    setOf("a", "b"),
                    files.observeAvailableOfflineMissingIds(7).first().toSet()
                )
            } finally {
                database.close()
                file.delete()
            }
        }

    private fun folder(id: String, parentId: String?, pinned: Boolean) =
        """INSERT INTO folders (id, chatId, parentId, name, isHidden, isArchived, isFavorite,
               isPinned, trashedAt, preTrashParentId, pendingPublish, createdAt, modifiedAt,
               changedAt)
               VALUES ('$id', 7, ${parentId?.let { "'$it'" } ?: "NULL"}, '$id', 0, 0, 0,
               ${if (pinned) 1 else 0}, NULL, NULL, 0, 1, 1, 1)"""

    private fun file(id: String, folderId: String?, pinned: Boolean, favorite: Boolean) =
        """INSERT INTO files (id, folderId, name, sizeBytes, mimeType, category, localPath,
               contentHash, chatId, messageId, remoteFileId, remoteUniqueId, backupState,
               isHidden, isArchived, isFavorite, isPinned, isEncrypted, width, height,
               durationMs, trashedAt, preTrashFolderId, pendingPublish, partCount, iconFileId,
               createdAt, modifiedAt, addedAt)
               VALUES ('$id', ${folderId?.let { "'$it'" } ?: "NULL"}, '$id.bin', 10,
               'application/octet-stream', 'OTHER', NULL, NULL, 7, 42, 'remote-$id',
               'unique-$id', 'BACKED_UP', 0, 0, ${if (favorite) 1 else 0},
               ${if (pinned) 1 else 0}, 0, NULL, NULL, NULL, NULL, NULL, 0, 0, NULL, 1, 1000,
               1)"""
}
