package com.drdisagree.teledrive.data.local

import com.drdisagree.teledrive.domain.model.FileCategory
import com.drdisagree.teledrive.domain.model.FileSortField
import com.drdisagree.teledrive.domain.model.SortDirection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileQueryBuilderTest {

    @Test
    fun `default spec hides trashed hidden and archived`() {
        val sql = FileQueryBuilder.build(FileQueryBuilder.Spec()).sql
        assertTrue(sql.contains("trashedAt IS NULL"))
        assertTrue(sql.contains("isHidden = 0"))
        assertTrue(sql.contains("isArchived = 0"))
    }

    @Test
    fun `folder filter distinguishes root from unfiltered`() {
        val unfiltered = FileQueryBuilder.build(FileQueryBuilder.Spec(filterByFolder = false)).sql
        assertFalse(unfiltered.contains("folderId"))

        val root = FileQueryBuilder.build(
            FileQueryBuilder.Spec(filterByFolder = true, folderId = null)
        ).sql
        assertTrue(root.contains("folderId IS NULL"))

        val specific = FileQueryBuilder.build(
            FileQueryBuilder.Spec(filterByFolder = true, folderId = "abc")
        ).sql
        assertTrue(specific.contains("folderId = ?"))
    }

    @Test
    fun `category filter uses placeholders`() {
        val sql = FileQueryBuilder.build(
            FileQueryBuilder.Spec(categories = listOf(FileCategory.IMAGE, FileCategory.VIDEO))
        ).sql
        assertTrue(sql.contains("category IN (?,?)"))
    }

    @Test
    fun `name query escapes like wildcards`() {
        val query = FileQueryBuilder.build(
            FileQueryBuilder.Spec(nameQuery = "100%_done")
        )
        assertTrue(query.sql.contains("ESCAPE"))
    }

    @Test
    fun `sort direction and field are applied`() {
        val sql = FileQueryBuilder.build(
            FileQueryBuilder.Spec(
                sortField = FileSortField.SIZE,
                sortDirection = SortDirection.DESCENDING
            )
        ).sql
        assertTrue(sql.contains("ORDER BY sizeBytes DESC"))
    }

    @Test
    fun `backup filters are mutually exclusive clauses`() {
        val backed = FileQueryBuilder.build(FileQueryBuilder.Spec(backedUpOnly = true)).sql
        assertTrue(backed.contains("backupState = 'BACKED_UP'"))
        val notBacked = FileQueryBuilder.build(FileQueryBuilder.Spec(notBackedUpOnly = true)).sql
        assertTrue(notBacked.contains("backupState != 'BACKED_UP'"))
    }
}
