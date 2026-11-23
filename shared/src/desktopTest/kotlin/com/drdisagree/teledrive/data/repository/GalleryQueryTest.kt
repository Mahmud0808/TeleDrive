package com.drdisagree.teledrive.data.repository

import androidx.paging.PagingSource
import com.drdisagree.teledrive.data.local.FileQueryBuilder
import com.drdisagree.teledrive.data.local.entity.FileEntity
import com.drdisagree.teledrive.domain.model.BackupState
import com.drdisagree.teledrive.domain.model.FileCategory
import com.drdisagree.teledrive.domain.model.FileQuerySpec
import com.drdisagree.teledrive.domain.model.FileSortField
import com.drdisagree.teledrive.domain.model.SortDirection
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryQueryTest {

    private val harness = SyncHarness()
    private val files = harness.database.fileDao()

    @After
    fun tearDown() = harness.close()

    @Test
    fun `newest first puts one header before each day`() = runBlocking {
        files.upsert(photo("a", at(2026, 3, 1, 9)))
        files.upsert(photo("b", at(2026, 3, 1, 18)))
        files.upsert(photo("c", at(2026, 3, 2, 12)))
        files.upsert(photo("d", at(2026, 2, 27, 8)))

        assertEquals(
            listOf("2026-03-02", "c", "2026-03-01", "b", "a", "2026-02-27", "d"),
            rows(SortDirection.DESCENDING)
        )
    }

    @Test
    fun `oldest first still puts each header before its day`() = runBlocking {
        files.upsert(photo("a", at(2026, 3, 1, 9)))
        files.upsert(photo("b", at(2026, 3, 1, 18)))
        files.upsert(photo("c", at(2026, 3, 2, 12)))

        assertEquals(
            listOf("2026-03-01", "a", "b", "2026-03-02", "c"),
            rows(SortDirection.ASCENDING)
        )
    }

    @Test
    fun `days and their photo counts follow the display order`() = runBlocking {
        files.upsert(photo("a", at(2026, 3, 1, 9)))
        files.upsert(photo("b", at(2026, 3, 1, 18)))
        files.upsert(photo("c", at(2026, 3, 2, 12)))

        val query = FileQueryBuilder.buildGalleryDays(spec(SortDirection.DESCENDING))
        val days = files.observeGalleryDays(query).first()

        assertEquals(listOf("2026-03-02" to 1, "2026-03-01" to 2), days.map { it.day to it.count })
    }

    private fun spec(direction: SortDirection) = FileQuerySpec(
        categories = listOf(FileCategory.IMAGE),
        sortField = FileSortField.DATE_TAKEN,
        sortDirection = direction,
        chatId = TEST_CHAT
    )

    private suspend fun rows(direction: SortDirection): List<String> {
        val spec = FileQuerySpec(
            categories = listOf(FileCategory.IMAGE),
            sortField = FileSortField.DATE_TAKEN,
            sortDirection = direction,
            chatId = TEST_CHAT
        )
        val result = files.galleryPagingSource(FileQueryBuilder.buildGallery(spec))
            .load(PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = true))
        val page = result as PagingSource.LoadResult.Page
        assertEquals(0, page.itemsAfter)
        return page.data.map { row -> row.file?.id ?: row.day }
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int): Long =
        LocalDateTime.of(year, month, day, hour, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private fun photo(id: String, takenAt: Long) = FileEntity(
        id = id,
        folderId = null,
        name = "$id.jpg",
        sizeBytes = 10,
        mimeType = "image/jpeg",
        category = FileCategory.IMAGE,
        localPath = null,
        contentHash = null,
        chatId = TEST_CHAT,
        messageId = null,
        remoteFileId = null,
        remoteUniqueId = null,
        backupState = BackupState.NONE,
        createdAt = takenAt,
        modifiedAt = takenAt,
        addedAt = takenAt
    )
}
