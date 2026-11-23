package com.drdisagree.teledrive.data.local

import androidx.room.RoomRawQuery
import com.drdisagree.teledrive.data.local.FileQueryBuilder.build
import com.drdisagree.teledrive.domain.model.FileQuerySpec
import com.drdisagree.teledrive.domain.model.FileSortField
import com.drdisagree.teledrive.domain.model.SortDirection

/** Raw queries, because Room's compile-time queries cannot express every sort option. */
object FileQueryBuilder {

    private fun coveringColumn(spec: FileQuerySpec): String? = when {
        spec.favoritesOnly -> "isFavorite"
        spec.availableOfflineOnly -> "isAvailableOffline"
        spec.archivedOnly -> "isArchived"
        spec.hiddenOnly -> "isHidden"
        else -> null
    }

    private fun folderTree(name: String, column: String): String =
        "$name(id) AS (" +
                "SELECT id FROM folders WHERE $column = 1 " +
                "UNION " +
                "SELECT folders.id FROM folders " +
                "JOIN $name ON folders.parentId = $name.id)"

    private fun notUnder(name: String): String =
        " AND (folderId IS NULL OR folderId NOT IN (SELECT id FROM $name))"

    fun build(spec: FileQuerySpec): RoomRawQuery = query("*", spec)

    fun buildIds(spec: FileQuerySpec): RoomRawQuery = query("id", spec)

    fun buildGalleryDays(spec: FileQuerySpec): RoomRawQuery {
        val filters = filters(spec)
        val tables = filters.trees + galleryBase(spec, filters)
        val sql = "WITH RECURSIVE ${tables.joinToString(", ")} " +
                "SELECT day, COUNT(*) AS count FROM base GROUP BY day ORDER BY day ${directionOf(spec)}"
        return raw(sql, filters.args)
    }

    private fun galleryBase(spec: FileQuerySpec, filters: QueryFilters): String {
        val column = if (spec.sortField == FileSortField.DATE_ADDED) "addedAt" else "createdAt"
        val day = "date($column / 1000, 'unixepoch', 'localtime')"
        return "base AS (SELECT id, $column AS sortValue, $day AS day " +
                "FROM files WHERE ${filters.where})"
    }

    fun buildGallery(spec: FileQuerySpec): RoomRawQuery {
        val filters = filters(spec)
        val direction = directionOf(spec)
        val tables = filters.trees + listOf(
            galleryBase(spec, filters),
            "entries AS (SELECT 0 AS isHeader, day, sortValue, id AS fileId FROM base " +
                    "UNION ALL SELECT 1, day, NULL, NULL FROM base GROUP BY day)"
        )
        val sql = "WITH RECURSIVE ${tables.joinToString(", ")} " +
                "SELECT entries.isHeader AS isHeader, entries.day AS day, files.* " +
                "FROM entries LEFT JOIN files ON files.id = entries.fileId " +
                "ORDER BY entries.day $direction, entries.isHeader DESC, " +
                "entries.sortValue $direction, entries.fileId ASC"
        return raw(sql, filters.args)
    }

    private fun query(projection: String, spec: FileQuerySpec): RoomRawQuery {
        val filters = filters(spec)
        val orderColumn = when (spec.sortField) {
            FileSortField.NAME -> "name COLLATE NOCASE"
            FileSortField.SIZE -> "sizeBytes"
            FileSortField.DATE_MODIFIED -> "modifiedAt"
            FileSortField.DATE_TAKEN -> "createdAt"
            FileSortField.DATE_ADDED -> "addedAt"
            FileSortField.TYPE -> "mimeType"
            FileSortField.BACKUP_STATUS -> "backupState"
        }
        val prefix = if (filters.trees.isEmpty()) {
            ""
        } else {
            "WITH RECURSIVE ${filters.trees.joinToString(", ")} "
        }
        val sql = prefix + "SELECT $projection FROM files WHERE ${filters.where} " +
                "ORDER BY $orderColumn ${directionOf(spec)}, id ASC"
        return raw(sql, filters.args)
    }

    private fun directionOf(spec: FileQuerySpec): String = when (spec.sortDirection) {
        SortDirection.ASCENDING -> "ASC"
        SortDirection.DESCENDING -> "DESC"
    }

    private fun raw(sql: String, args: List<Any>): RoomRawQuery = RoomRawQuery(sql) { statement ->
        args.forEachIndexed { index, value ->
            when (value) {
                is String -> statement.bindText(index + 1, value)
                is Long -> statement.bindLong(index + 1, value)
                is Int -> statement.bindLong(index + 1, value.toLong())
                else -> error("Unsupported query argument: $value")
            }
        }
    }

    private fun filters(spec: FileQuerySpec): QueryFilters {
        val where = StringBuilder("trashedAt IS NULL")
        val args = mutableListOf<Any>()
        val trees = mutableListOf<String>()

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
        if (spec.availableOfflineOnly) where.append(" AND isAvailableOffline = 1")
        if (spec.hiddenOnly) where.append(" AND isHidden = 1")
        if (spec.archivedOnly) where.append(" AND isArchived = 1")
        coveringColumn(spec)?.let { column ->
            trees += folderTree("covering_folders", column)
            where.append(notUnder("covering_folders"))
        }
        if (!spec.showHidden) {
            where.append(" AND isHidden = 0")
            trees += folderTree("hidden_folders", "isHidden")
            where.append(notUnder("hidden_folders"))
        }
        if (!spec.showArchived) {
            where.append(" AND isArchived = 0")
            trees += folderTree("archived_folders", "isArchived")
            where.append(notUnder("archived_folders"))
        }

        return QueryFilters(trees, where.toString(), args)
    }

    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
