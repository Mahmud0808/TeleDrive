package com.drdisagree.teledrive.data.local

import androidx.room.RoomRawQuery
import com.drdisagree.teledrive.data.local.FileQueryBuilder.build
import com.drdisagree.teledrive.domain.model.FileQuerySpec
import com.drdisagree.teledrive.domain.model.FileSortField
import com.drdisagree.teledrive.domain.model.SortDirection

/**
 * Builds parameterized queries against the `files` table for the browser,
 * gallery, and search screens. Sorting cannot be expressed with Room's
 * compile-time queries without an explosion of variants, hence raw queries.
 */
object FileQueryBuilder {
    fun build(spec: FileQuerySpec): RoomRawQuery = query("*", spec)

    /** Same filter and order as [build], reading only the ids of every match. */
    fun buildIds(spec: FileQuerySpec): RoomRawQuery = query("id", spec)

    private fun query(projection: String, spec: FileQuerySpec): RoomRawQuery {
        val where = StringBuilder("trashedAt IS NULL")
        val args = mutableListOf<Any>()

        val chatId = spec.chatId
        if (chatId != null) {
            where.append(" AND chatId = ?")
            args.add(chatId)
        }

        val folderId = spec.folderId
        if (spec.filterByFolder) {
            if (folderId == null) {
                where.append(" AND folderId IS NULL")
            } else {
                where.append(" AND folderId = ?")
                args.add(folderId)
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
        return RoomRawQuery(sql) { statement ->
            args.forEachIndexed { index, value ->
                when (value) {
                    is String -> statement.bindText(index + 1, value)
                    is Long -> statement.bindLong(index + 1, value)
                    is Int -> statement.bindLong(index + 1, value.toLong())
                    else -> error("Unsupported query argument: $value")
                }
            }
        }
    }

    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
