package com.drdisagree.teledrive.presentation.preview

import com.drdisagree.teledrive.domain.model.FileCategory
import com.drdisagree.teledrive.domain.model.FileSortField
import com.drdisagree.teledrive.domain.model.SortDirection
import com.drdisagree.teledrive.presentation.navigation.Route

/**
 * The list a grid is showing, handed to the viewer so paging sideways walks
 * the same files in the same order instead of a freshly guessed query.
 */
data class PreviewSequence(
    val folderId: String? = null,
    val filterByFolder: Boolean = false,
    val nameQuery: String? = null,
    val categories: List<FileCategory> = emptyList(),
    val favoritesOnly: Boolean = false,
    val hiddenOnly: Boolean = false,
    val archivedOnly: Boolean = false,
    val sortField: FileSortField = FileSortField.NAME,
    val sortDirection: SortDirection = SortDirection.ASCENDING
) {

    fun routeFor(fileId: String): Route.Preview = Route.Preview(
        fileId = fileId,
        folderId = folderId,
        filterByFolder = filterByFolder,
        nameQuery = nameQuery,
        categories = categories.takeIf { it.isNotEmpty() }
            ?.joinToString(CATEGORY_SEPARATOR) { it.name },
        favoritesOnly = favoritesOnly,
        hiddenOnly = hiddenOnly,
        archivedOnly = archivedOnly,
        sortField = sortField.name,
        sortDescending = sortDirection == SortDirection.DESCENDING
    )
}

const val CATEGORY_SEPARATOR = ","
