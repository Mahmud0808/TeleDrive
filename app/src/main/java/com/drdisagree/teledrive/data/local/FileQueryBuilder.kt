package com.drdisagree.teledrive.data.local

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.drdisagree.teledrive.data.local.FileQueryBuilder.build
import com.drdisagree.teledrive.domain.model.FileCategory
import com.drdisagree.teledrive.domain.model.FileSortField
import com.drdisagree.teledrive.domain.model.SortDirection

/**
 * Builds parameterized queries against the `files` table for the browser,
 * gallery, and search screens. Sorting cannot be expressed with Room's
 * compile-time queries without an explosion of variants, hence raw queries.
 */
object FileQueryBuilder {

    data class Spec(
        val chatId: Long? = null,
        val folderId: String? = null,
        val filterByFolder: Boolean = false,
        val categories: List<FileCategory> = emptyList(),
        val nameQuery: String? = null,
        val extension: String? = null,
        val minSizeBytes: Long? = null,
        val maxSizeBytes: Long? = null,
        val modifiedAfter: Long? = null,
        val modifiedBefore: Long? = null,
        val backedUpOnly: Boolean = false,
        val notBackedUpOnly: Boolean = false,
        val favoritesOnly: Boolean = false,
        val hiddenOnly: Boolean = false,
        val archivedOnly: Boolean = false,
        val showHidden: Boolean = false,
        val showArchived: Boolean = false,
        val sortField: FileSortField = FileSortField.NAME,
        val sortDirection: SortDirection = SortDirection.ASCENDING
    )

    fun build(spec: Spec): SupportSQLiteQuery = query("*", spec)

    /** Same filter and order as [build], reading only the ids of every match. */
    fun buildIds(spec: Spec): SupportSQLiteQuery = query("id", spec)

    private fun query(projection: String, spec: Spec): SupportSQLiteQuery {
        val where = StringBuilder("trashedAt IS NULL")
        val args = mutableListOf<Any>()

        if (spec.chatId != null) {
            where.append(" AND chatId = ?")
            args.add(spec.chatId)
        }

        if (spec.filterByFolder) {
            if (spec.folderId == null) {
                where.append(" AND folderId IS NULL")
            } else {
                where.append(" AND folderId = ?")
                args.add(spec.folderId)
            }
        }
        if (spec.categories.isNotEmpty()) {
            where.append(" AND category IN (${spec.categories.joinToString(",") { "?" }})")
            args.addAll(spec.categories.map { it.name })
        }
        spec.nameQuery?.takeIf { it.isNotBlank() }?.let {
            where.append(" AND name LIKE ? ESCAPE '\\'")
            args.add("%${escapeLike(it)}%")
        }
        spec.extension?.takeIf { it.isNotBlank() }?.let {
            where.append(" AND name LIKE ? ESCAPE '\\'")
            args.add("%.${escapeLike(it)}")
        }
        spec.minSizeBytes?.let {
            where.append(" AND sizeBytes >= ?")
            args.add(it)
        }
        spec.maxSizeBytes?.let {
            where.append(" AND sizeBytes <= ?")
            args.add(it)
        }
        spec.modifiedAfter?.let {
            where.append(" AND modifiedAt >= ?")
            args.add(it)
        }
        spec.modifiedBefore?.let {
            where.append(" AND modifiedAt <= ?")
            args.add(it)
        }
        if (spec.backedUpOnly) where.append(" AND backupState = 'BACKED_UP'")
        if (spec.notBackedUpOnly) where.append(" AND backupState != 'BACKED_UP'")
        if (spec.favoritesOnly) where.append(" AND isFavorite = 1")
        if (spec.hiddenOnly) where.append(" AND isHidden = 1")
        if (spec.archivedOnly) where.append(" AND isArchived = 1")
        if (!spec.showHidden) where.append(" AND isHidden = 0")
        if (!spec.showArchived) where.append(" AND isArchived = 0")

        val orderColumn = when (spec.sortField) {
            FileSortField.NAME -> "name COLLATE NOCASE"
            FileSortField.SIZE -> "sizeBytes"
            FileSortField.DATE_MODIFIED -> "modifiedAt"
            FileSortField.DATE_ADDED -> "addedAt"
            FileSortField.TYPE -> "mimeType"
            FileSortField.BACKUP_STATUS -> "backupState"
        }
        val direction = when (spec.sortDirection) {
            SortDirection.ASCENDING -> "ASC"
            SortDirection.DESCENDING -> "DESC"
        }

        val sql = "SELECT $projection FROM files WHERE $where " +
                "ORDER BY $orderColumn $direction, id ASC"
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
