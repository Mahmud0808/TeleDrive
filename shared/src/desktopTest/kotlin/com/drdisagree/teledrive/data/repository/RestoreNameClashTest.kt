package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.core.crypto.SecureFileDeleter
import com.drdisagree.teledrive.data.local.entity.FileEntity
import com.drdisagree.teledrive.domain.model.BackupState
import com.drdisagree.teledrive.domain.model.FileCategory
import com.drdisagree.teledrive.domain.repository.TransferRepository
import com.drdisagree.teledrive.testing.unused
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreNameClashTest {

    private val harness = SyncHarness()
    private val files = harness.database.fileDao()

    @After
    fun tearDown() = harness.close()

    @Test
    fun `a restored file whose name is taken gets a numbered name`() = runBlocking {
        files.upsert(file("old", "doc.pdf"))
        val trash = trash()
        trash.moveFilesToTrash(listOf("old"))
        files.upsert(file("new", "doc.pdf"))

        trash.restoreFiles(listOf("old"))

        assertEquals("doc.pdf", files.byId("new")!!.name)
        assertEquals("doc (1).pdf", files.byId("old")!!.name)
    }

    @Test
    fun `a restored file keeps its name when nothing clashes`() = runBlocking {
        files.upsert(file("old", "doc.pdf"))
        val trash = trash()
        trash.moveFilesToTrash(listOf("old"))

        trash.restoreFiles(listOf("old"))

        assertEquals("doc.pdf", files.byId("old")!!.name)
    }

    private fun trash() = TrashRepositoryImpl(
        storagePaths = harness.storagePaths,
        fileDao = files,
        folderDao = harness.database.folderDao(),
        backupDao = harness.database.backupDao(),
        thumbnailDao = harness.database.thumbnailDao(),
        telegramClient = harness.telegram,
        secureFileDeleter = SecureFileDeleter(),
        publishScheduler = harness.scheduler,
        pendingDeleteDao = harness.database.pendingDeleteDao(),
        filePartDao = harness.database.filePartDao(),
        activeChannel = harness.activeChannel,
        transferRepository = unused<TransferRepository>(),
        tombstoneDao = harness.database.folderTombstoneDao()
    )

    private fun file(id: String, name: String) = FileEntity(
        id = id,
        folderId = null,
        name = name,
        sizeBytes = 10,
        mimeType = "application/pdf",
        category = FileCategory.DOCUMENT,
        localPath = null,
        contentHash = null,
        chatId = TEST_CHAT,
        messageId = null,
        remoteFileId = null,
        remoteUniqueId = null,
        backupState = BackupState.NONE,
        createdAt = 1,
        modifiedAt = 1,
        addedAt = 1
    )
}
