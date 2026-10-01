package com.drdisagree.teledrive.domain.usecase

import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.domain.model.BackupState
import com.drdisagree.teledrive.domain.model.DriveFile
import com.drdisagree.teledrive.domain.model.FileCategory
import com.drdisagree.teledrive.domain.repository.FileRepository
import com.drdisagree.teledrive.domain.repository.TransferRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepOnDeviceUseCaseTest {

    private val remoteOnly = file("remote", local = false, remote = true)
    private val alreadyLocal = file("local", local = true, remote = true)
    private val localOnly = file("staged", local = true, remote = false)
    private val inFolder = file("nested", local = false, remote = true)

    private val files = FakeFiles(
        listOf(remoteOnly, alreadyLocal, localOnly, inFolder),
        tree = mapOf("folder" to listOf("nested", "local"))
    )
    private val transfers = FakeTransfers()
    private val keepOnDevice = KeepOnDeviceUseCase(files, transfers)

    @Test
    fun `pinning queues only files without a local copy`() = runBlocking {
        val queued = keepOnDevice(listOf("remote", "local", "staged"), pinned = true)

        assertEquals(1, queued)
        assertEquals(listOf("remote"), transfers.downloads)
        assertEquals(listOf("remote", "local", "staged"), files.pinnedFiles)
    }

    @Test
    fun `pinning a folder fetches what is missing inside it`() = runBlocking {
        val queued = keepOnDevice(emptyList(), listOf("folder"), pinned = true)

        assertEquals(1, queued)
        assertEquals(listOf("nested"), transfers.downloads)
        assertEquals(listOf("folder"), files.pinnedFolders)
    }

    @Test
    fun `a file picked directly and through its folder is queued once`() = runBlocking {
        keepOnDevice(listOf("nested"), listOf("folder"), pinned = true)

        assertEquals(listOf("nested"), transfers.downloads)
    }

    @Test
    fun `unpinning never downloads`() = runBlocking {
        val queued = keepOnDevice(listOf("remote"), listOf("folder"), pinned = false)

        assertEquals(0, queued)
        assertTrue(transfers.downloads.isEmpty())
        assertEquals(listOf("remote"), files.unpinnedFiles)
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

    private class FakeFiles(
        private val all: List<DriveFile>,
        private val tree: Map<String, List<String>>
    ) : FileRepository by unused() {
        val pinnedFiles = mutableListOf<String>()
        val unpinnedFiles = mutableListOf<String>()
        val pinnedFolders = mutableListOf<String>()

        override suspend fun setFilesPinned(ids: List<String>, pinned: Boolean) {
            (if (pinned) pinnedFiles else unpinnedFiles) += ids
        }

        override suspend fun setFolderPinned(id: String, pinned: Boolean) {
            if (pinned) pinnedFolders += id
        }

        override suspend fun fileIdsInTree(folderId: String) = tree[folderId].orEmpty()

        override suspend fun filesByIds(ids: List<String>) = all.filter { it.id in ids }
    }

    private class FakeTransfers : TransferRepository by unused() {
        val downloads = mutableListOf<String>()

        override suspend fun enqueueDownload(fileId: String, priority: Int): AppResult<String> {
            downloads += fileId
            return AppResult.Success(fileId)
        }
    }
}

private inline fun <reified T : Any> unused(): T = Proxy.newProxyInstance(
    T::class.java.classLoader,
    arrayOf(T::class.java)
) { _, method, _ -> throw UnsupportedOperationException(method.name) } as T
