package com.drdisagree.teledrive.data.local

import androidx.paging.PagingConfig
import androidx.paging.PagingSource.LoadParams
import androidx.paging.PagingSource.LoadResult
import androidx.paging.PagingState
import com.drdisagree.teledrive.data.local.entity.FileEntity
import com.drdisagree.teledrive.data.repository.SyncHarness
import com.drdisagree.teledrive.data.repository.TEST_CHAT
import com.drdisagree.teledrive.domain.model.BackupState
import com.drdisagree.teledrive.domain.model.FileCategory
import com.drdisagree.teledrive.domain.model.FileQuerySpec
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

class OffsetRefreshPagingSourceTest {

    private val harness = SyncHarness()
    private val files = harness.database.fileDao()
    private val config = PagingConfig(pageSize = 60, prefetchDistance = 120, enablePlaceholders = false)
    private val spec = FileQueryBuilder.build(FileQuerySpec(chatId = TEST_CHAT))

    @After
    fun tearDown() = harness.close()

    @Test
    fun `a reload after scrolling deep returns the rows that were on screen`() = runBlocking {
        repeat(1_000) { files.upsert(file(it)) }
        val first = OffsetRefreshPagingSource(files.pagingSource(spec))
        val window = first.load(LoadParams.Refresh(600, config.initialLoadSize, false))
            as LoadResult.Page
        val state = PagingState(listOf(window), anchorPosition = 30, config, 0)

        val key = first.getRefreshKey(state)
        val reloaded = OffsetRefreshPagingSource(files.pagingSource(spec))
            .load(LoadParams.Refresh(key, config.initialLoadSize, false)) as LoadResult.Page

        assertTrue(reloaded.data.any { it.name == name(630) })
    }

    private fun name(index: Int) = "f%04d".format(index)

    private fun file(index: Int) = FileEntity(
        id = "id-$index",
        folderId = null,
        name = name(index),
        sizeBytes = 10,
        mimeType = "image/jpeg",
        category = FileCategory.IMAGE,
        localPath = null,
        contentHash = null,
        chatId = TEST_CHAT,
        messageId = index.toLong(),
        remoteFileId = "r$index",
        remoteUniqueId = "u$index",
        backupState = BackupState.BACKED_UP,
        createdAt = 1,
        modifiedAt = 1,
        addedAt = 1
    )
}
