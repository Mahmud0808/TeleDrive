package com.drdisagree.teledrive.presentation.files

import com.drdisagree.teledrive.domain.model.DriveFolder
import com.drdisagree.teledrive.domain.model.FileSortField
import com.drdisagree.teledrive.domain.model.SortDirection
import com.drdisagree.teledrive.domain.model.ViewMode
import com.drdisagree.teledrive.presentation.common.UiText
import com.drdisagree.teledrive.presentation.components.SelectionCapabilities

data class FilesUiState(
    val folderId: String? = null,
    val folderName: UiText = UiText.Plain(""),
    val breadcrumbs: List<FolderCrumb> = emptyList(),
    val folders: List<DriveFolder> = emptyList(),
    val selection: Set<String> = emptySet(),
    val folderSelection: Set<String> = emptySet(),
    val viewMode: ViewMode = ViewMode.GRID,
    val gridSize: Int = 3,
    val sortField: FileSortField = FileSortField.NAME,
    val sortDirection: SortDirection = SortDirection.ASCENDING,
    val showHidden: Boolean = false,
    val loaded: Boolean = false,
    val capabilities: SelectionCapabilities = SelectionCapabilities()
) {
    val selectionMode: Boolean get() = selection.isNotEmpty() || folderSelection.isNotEmpty()
    val selectionCount: Int get() = selection.size + folderSelection.size
    val folderInSelection: Boolean get() = folderSelection.isNotEmpty()

    val allSelectedAvailableOffline: Boolean
        get() = selectionMode &&
                !capabilities.anyNotAvailableOffline &&
                folders.filter { it.id in folderSelection }.all { it.isAvailableOffline }

    val allSelectedFavorite: Boolean
        get() = selectionMode &&
                !capabilities.anyUnfavorited &&
                folders.filter { it.id in folderSelection }.all { it.isFavorite }
}
