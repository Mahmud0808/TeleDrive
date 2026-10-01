package com.drdisagree.teledrive.data.local

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderClockMigrationTest {

    @Test
    fun `upgrading from version 11 keeps every row and seeds the folder clock`() = runBlocking {
        val file = File.createTempFile("teledrive-v11", ".db")
        file.delete()
        createLegacyDatabase(
            file,
            version = 11,
            """INSERT INTO folders (id, chatId, parentId, name, isHidden, isArchived,
                   isFavorite, isPinned, trashedAt, preTrashParentId, pendingPublish,
                   createdAt, modifiedAt)
                   VALUES ('folder-1', 7, NULL, 'Work', 1, 0, 0, 1, NULL, NULL, 0, 1000, 1234)""",
            """INSERT INTO files (id, folderId, name, sizeBytes, mimeType, category,
                   localPath, contentHash, chatId, messageId, remoteFileId, remoteUniqueId,
                   backupState, isHidden, isArchived, isFavorite, isPinned, isEncrypted,
                   width, height, durationMs, trashedAt, preTrashFolderId, pendingPublish,
                   partCount, iconFileId, createdAt, modifiedAt, addedAt)
                   VALUES ('file-1', 'folder-1', 'report.pdf', 10, 'application/pdf',
                   'DOCUMENT', NULL, NULL, 7, 42, 'remote', 'unique', 'BACKED_UP',
                   0, 0, 0, 0, 0, NULL, NULL, NULL, NULL, NULL, 0, 0, NULL, 1, 1, 1)"""
        )

        val database = openMigrated(file)
        try {
            val folder = database.folderDao().byId("folder-1")!!
            assertEquals("Work", folder.name)
            assertEquals(1_234L, folder.modifiedAt)
            assertEquals(1_234L, folder.changedAt)
            assertTrue(folder.isHidden)
            assertTrue(folder.isAvailableOffline)

            val row = database.fileDao().byId("file-1")!!
            assertEquals("report.pdf", row.name)
            assertEquals("folder-1", row.folderId)
            assertEquals(42L, row.messageId)

            assertTrue(database.folderTombstoneDao().inChat(7L).isEmpty())
        } finally {
            database.close()
            file.delete()
        }
    }
}
