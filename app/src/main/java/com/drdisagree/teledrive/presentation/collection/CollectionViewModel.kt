package com.drdisagree.teledrive.presentation.collection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.drdisagree.teledrive.data.local.FileQueryBuilder
import com.drdisagree.teledrive.domain.model.DriveFile
import com.drdisagree.teledrive.domain.model.FileSortField
import com.drdisagree.teledrive.domain.model.SortDirection
import com.drdisagree.teledrive.domain.repository.FileRepository
import com.drdisagree.teledrive.domain.repository.TrashRepository
import com.drdisagree.teledrive.presentation.navigation.Route
import com.drdisagree.teledrive.presentation.preview.PreviewSequence
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CollectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val fileRepository: FileRepository,
    private val trashRepository: TrashRepository
) : ViewModel() {

    val type: CollectionType = runCatching {
        CollectionType.valueOf(savedStateHandle.toRoute<Route.Collection>().type)
    }.getOrDefault(CollectionType.FAVORITES)

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    private val _allSelected = MutableStateFlow(false)
    val allSelected: StateFlow<Boolean> = _allSelected.asStateFlow()

    private var rangeBase: Set<String>? = null

    private val spec = FileQueryBuilder.Spec(
        favoritesOnly = type == CollectionType.FAVORITES,
        hiddenOnly = type == CollectionType.HIDDEN,
        archivedOnly = type == CollectionType.ARCHIVED,
        showHidden = type == CollectionType.HIDDEN,
        showArchived = type == CollectionType.ARCHIVED,
        sortField = FileSortField.DATE_ADDED,
        sortDirection = SortDirection.DESCENDING
    )

    val previewSequence = PreviewSequence(
        favoritesOnly = type == CollectionType.FAVORITES,
        hiddenOnly = type == CollectionType.HIDDEN,
        archivedOnly = type == CollectionType.ARCHIVED,
        sortField = FileSortField.DATE_ADDED,
        sortDirection = SortDirection.DESCENDING
    )

    val files: Flow<PagingData<DriveFile>> = fileRepository.pagedFiles(spec)
        .cachedIn(viewModelScope)

    fun toggleSelection(id: String) {
        _allSelected.update { false }
        _selection.update { if (id in it) it - id else it + id }
    }

    fun clearSelection() {
        _allSelected.update { false }
        _selection.update { emptySet() }
    }

    /** Selects every file in this collection, including pages not loaded yet. */
    fun selectAll() {
        viewModelScope.launch {
            _selection.update { fileRepository.fileIds(spec).toSet() }
            _allSelected.update { true }
        }
    }

    /** Removes the property that puts files in this collection. */
    fun startRangeSelection() {
        rangeBase = _selection.value
    }

    fun extendRangeSelection(ids: List<String>) {
        val base = rangeBase ?: return
        _allSelected.update { false }
        _selection.update { base + ids }
    }

    fun endRangeSelection() {
        rangeBase = null
    }

    fun removeFromCollection() {
        val ids = _selection.value.toList()
        clearSelection()
        viewModelScope.launch {
            when (type) {
                CollectionType.FAVORITES -> fileRepository.setFilesFavorite(ids, false)
                CollectionType.ARCHIVED -> fileRepository.setFilesArchived(ids, false)
                CollectionType.HIDDEN -> fileRepository.setFilesHidden(ids, false)
            }
        }
    }

    fun trashSelected() {
        val ids = _selection.value.toList()
        clearSelection()
        viewModelScope.launch { trashRepository.moveFilesToTrash(ids) }
    }
}
