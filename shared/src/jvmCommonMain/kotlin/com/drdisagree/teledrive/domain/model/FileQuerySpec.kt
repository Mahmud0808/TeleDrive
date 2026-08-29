package com.drdisagree.teledrive.domain.model

data class FileQuerySpec(
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
