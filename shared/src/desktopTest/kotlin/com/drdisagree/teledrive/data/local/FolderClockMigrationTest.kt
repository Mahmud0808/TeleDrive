package com.drdisagree.teledrive.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.drdisagree.teledrive.data.local.database.MIGRATION_11_12
import com.drdisagree.teledrive.data.local.database.TeleDriveDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderClockMigrationTest {

    @Test
    fun `upgrading from version 11 keeps every row and seeds the folder clock`() = runBlocking {
        val file = File.createTempFile("teledrive-v11", ".db")
        file.delete()
        createVersion11(file)

        val database = Room.databaseBuilder<TeleDriveDatabase>(name = file.absolutePath)
            .addMigrations(MIGRATION_11_12)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        try {
            val folder = database.folderDao().byId("folder-1")!!
            assertEquals("Work", folder.name)
            assertEquals(1_234L, folder.modifiedAt)
            assertEquals(1_234L, folder.changedAt)
            assertTrue(folder.isHidden)
            assertTrue(folder.isPinned)

            val row = database.fileDao().byId("file-1")!!
            assertEquals("report.pdf", row.name)
            assertEquals("folder-1", row.folderId)
            assertEquals(42L, row.messageId)
        } finally {
            database.close()
            file.delete()
        }
    }

    private fun createVersion11(file: File) {
        val schema = Json.parseToJsonElement(schemaFile(11).readText())
            .jsonObject.getValue("database").jsonObject
        val connection = BundledSQLiteDriver().open(file.absolutePath)
        try {
            schema.getValue("entities").jsonArray.forEach { entity ->
                val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
                connection.execSQL(
                    entity.jsonObject.getValue("createSql").jsonPrimitive.content
                        .replace("\${TABLE_NAME}", table)
                )
                entity.jsonObject["indices"]?.jsonArray?.forEach { index ->
                    connection.execSQL(
                        index.jsonObject.getValue("createSql").jsonPrimitive.content
                            .replace("\${TABLE_NAME}", table)
                    )
                }
            }
            schema.getValue("setupQueries").jsonArray.forEach {
                connection.execSQL(it.jsonPrimitive.content)
            }
            connection.execSQL(
                """INSERT INTO folders (id, chatId, parentId, name, isHidden, isArchived,
                   isFavorite, isPinned, trashedAt, preTrashParentId, pendingPublish,
                   createdAt, modifiedAt)
                   VALUES ('folder-1', 7, NULL, 'Work', 1, 0, 0, 1, NULL, NULL, 0, 1000, 1234)"""
            )
            connection.execSQL(
                """INSERT INTO files (id, folderId, name, sizeBytes, mimeType, category,
                   localPath, contentHash, chatId, messageId, remoteFileId, remoteUniqueId,
                   backupState, isHidden, isArchived, isFavorite, isPinned, isEncrypted,
                   width, height, durationMs, trashedAt, preTrashFolderId, pendingPublish,
                   partCount, iconFileId, createdAt, modifiedAt, addedAt)
                   VALUES ('file-1', 'folder-1', 'report.pdf', 10, 'application/pdf',
                   'DOCUMENT', NULL, NULL, 7, 42, 'remote', 'unique', 'BACKED_UP',
                   0, 0, 0, 0, 0, NULL, NULL, NULL, NULL, NULL, 0, 0, NULL, 1, 1, 1)"""
            )
            connection.execSQL("PRAGMA user_version = 11")
        } finally {
            connection.close()
        }
    }

    private fun schemaFile(version: Int): File {
        val relative = "schemas/com.drdisagree.teledrive.data.local.database.TeleDriveDatabase/$version.json"
        return listOf(File(relative), File("shared/$relative")).first { it.exists() }
    }
}
