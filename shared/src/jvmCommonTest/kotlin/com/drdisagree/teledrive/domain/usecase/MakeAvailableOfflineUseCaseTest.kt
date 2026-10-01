package com.drdisagree.teledrive.domain.usecase

import com.drdisagree.teledrive.domain.model.BackupState
import com.drdisagree.teledrive.domain.model.DriveFile
import com.drdisagree.teledrive.domain.model.FileCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MakeAvailableOfflineUseCaseTest {

    private val remoteOnly = file("remote", local = false, remote = true)
    private val alreadyLocal = file("local", local = true, remote = true)
    private val localOnly = file("staged", local = true, remote = false)
    private val inFolder = file("nested", local = false, remote = true)

    private val files = FakeFileRepository(
        listOf(remoteOnly, alreadyLocal, localOnly, inFolder),
        tree = mapOf("folder" to listOf("nested", "local"))
    )
    private val transfers = FakeTransferRepository()
    private val makeAvailableOffline = MakeAvailableOfflineUseCase(files, transfers)

    @Test
    fun `making files available offline queues only those without a local copy`() = runBlocking {
        val queued = makeAvailableOffline(listOf("remote", "local", "staged"), available = true)

        assertEquals(1, queued)
        assertEquals(listOf("remote"), transfers.downloads)
        assertEquals(listOf("remote", "local", "staged"), files.offlineFiles)
    }

    @Test
    fun `making a folder available offline fetches what is missing inside it`() = runBlocking {
        val queued = makeAvailableOffline(emptyList(), listOf("folder"), available = true)

        assertEquals(1, queued)
        assertEquals(listOf("nested"), transfers.downloads)
        assertEquals(listOf("folder"), files.offlineFolders)
    }

    @Test
    fun `a file picked directly and through its folder is queued once`() = runBlocking {
        makeAvailableOffline(listOf("nested"), listOf("folder"), available = true)

        assertEquals(listOf("nested"), transfers.downloads)
    }

    @Test
    fun `removing from offline never downloads`() = runBlocking {
        val queued = makeAvailableOffline(listOf("remote"), listOf("folder"), available = false)

        assertEquals(0, queued)
        assertTrue(transfers.downloads.isEmpty())
        assertEquals(listOf("remote"), files.onlineOnlyFiles)
    }

    private fun file(id: String, local: Boolean, remote: Boolean) = DriveFile(
        id = id,
        folderId = null,
        name = "$id.jpg",
        sizeBytes = 1,
        mimeType = "image/jpeg",
        category = FileCategory.IMAGE,
        localPath = if (local) "/storage/$id.jpg" else null,
        contentHash = null,
        chatId = if (remote) 1L else null,
        messageId = if (remote) 1L else null,
        remoteFileId = null,
        remoteUniqueId = null,
        backupState = BackupState.NONE,
        isHidden = false,
        isArchived = false,
        isFavorite = false,
        isEncrypted = false,
        width = null,
        height = null,
        durationMs = null,
        trashedAt = null,
        createdAt = 0,
        modifiedAt = 0,
        addedAt = 0
    )
}
