package com.drdisagree.teledrive.presentation.collection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.drdisagree.teledrive.domain.model.DriveFile
import com.drdisagree.teledrive.domain.model.DriveFolder
import com.drdisagree.teledrive.domain.model.FileQuerySpec
import com.drdisagree.teledrive.domain.model.FileSortField
import com.drdisagree.teledrive.domain.model.SortDirection
import com.drdisagree.teledrive.domain.model.TransferState
import com.drdisagree.teledrive.domain.repository.FileRepository
import com.drdisagree.teledrive.domain.repository.TransferRepository
import com.drdisagree.teledrive.domain.repository.TrashRepository
import com.drdisagree.teledrive.domain.usecase.MakeAvailableOfflineUseCase
import com.drdisagree.teledrive.presentation.navigation.Route
import com.drdisagree.teledrive.presentation.preview.PreviewSequence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CollectionViewModel(
    savedStateHandle: SavedStateHandle,
    private val fileRepository: FileRepository,
    private val trashRepository: TrashRepository,
    private val transferRepository: TransferRepository,
    private val makeAvailableOffline: MakeAvailableOfflineUseCase
) : ViewModel() {

    val type: CollectionType = runCatching {
        CollectionType.valueOf(savedStateHandle.toRoute<Route.Collection>().type)
    }.getOrDefault(CollectionType.FAVORITES)

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    private val _folderSelection = MutableStateFlow<Set<String>>(emptySet())
    val folderSelection: StateFlow<Set<String>> = _folderSelection.asStateFlow()

    val folders: StateFlow<List<DriveFolder>> = when (type) {
        CollectionType.FAVORITES -> fileRepository.observeFavoriteFolders()
        CollectionType.AVAILABLE_OFFLINE -> fileRepository.observeAvailableOfflineFolders()
        CollectionType.ARCHIVED -> fileRepository.observeArchivedFolders()
        CollectionType.HIDDEN -> fileRepository.observeHiddenFolders()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _allSelected = MutableStateFlow(false)
    val allSelected: StateFlow<Boolean> = _allSelected.asStateFlow()

    private var rangeBase: Pair<Set<String>, Set<String>>? = null

    private val spec = FileQuerySpec(
        favoritesOnly = type == CollectionType.FAVORITES,
        availableOfflineOnly = type == CollectionType.AVAILABLE_OFFLINE,
        hiddenOnly = type == CollectionType.HIDDEN,
        archivedOnly = type == CollectionType.ARCHIVED,
        showHidden = type == CollectionType.HIDDEN,
        showArchived = type == CollectionType.ARCHIVED,
        sortField = FileSortField.DATE_ADDED,
        sortDirection = SortDirection.DESCENDING
    )

    val previewSequence = PreviewSequence(
        favoritesOnly = type == CollectionType.FAVORITES,
        availableOfflineOnly = type == CollectionType.AVAILABLE_OFFLINE,
        hiddenOnly = type == CollectionType.HIDDEN,
        archivedOnly = type == CollectionType.ARCHIVED,
        sortField = FileSortField.DATE_ADDED,
        sortDirection = SortDirection.DESCENDING
    )

    val files: Flow<PagingData<DriveFile>> = fileRepository.pagedFiles(spec)
        .cachedIn(viewModelScope)

    /** Files on their way down, keyed by id; a null progress means not started yet. */
    val downloads: StateFlow<Map<String, Float?>> =
        if (type == CollectionType.AVAILABLE_OFFLINE) {
            transferRepository.observeActiveDownloads().map { tasks ->
                tasks.associate { task ->
                    task.fileId.orEmpty() to task.progress.takeIf {
                        task.state == TransferState.RUNNING && it > 0f
                    }
                }
            }
        } else {
            flowOf(emptyMap())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val missingCount: StateFlow<Int> =
        if (type == CollectionType.AVAILABLE_OFFLINE) {
            combine(fileRepository.observeAvailableOfflineMissingIds(), downloads) { ids, active ->
                ids.count { it !in active }
            }
        } else {
            flowOf(0)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun downloadMissing() {
        viewModelScope.launch { makeAvailableOffline.downloadMissing() }
    }

    fun toggleSelection(id: String) {
        _allSelected.update { false }
        _selection.update { if (id in it) it - id else it + id }
    }

    fun toggleFolderSelection(id: String) {
        _allSelected.update { false }
        _folderSelection.update { if (id in it) it - id else it + id }
    }

    fun clearSelection() {
        _allSelected.update { false }
        _selection.update { emptySet() }
        _folderSelection.update { emptySet() }
    }

    /** Selects every file in this collection, including pages not loaded yet. */
    fun selectAll() {
        viewModelScope.launch {
            _selection.update { fileRepository.fileIds(spec).toSet() }
            _folderSelection.update { folders.value.map { folder -> folder.id }.toSet() }
            _allSelected.update { true }
        }
    }

    fun startRangeSelection() {
        rangeBase = _selection.value to _folderSelection.value
    }

    fun extendRangeSelection(fileIds: List<String>, folderIds: List<String>) {
        val base = rangeBase ?: return
        _allSelected.update { false }
        _selection.update { base.first + fileIds }
        _folderSelection.update { base.second + folderIds }
    }

    fun endRangeSelection() {
        rangeBase = null
    }

    fun removeFromCollection() {
        val ids = _selection.value.toList()
        val folderIds = _folderSelection.value.toList()
        clearSelection()
        viewModelScope.launch {
            when (type) {
                CollectionType.FAVORITES -> {
                    if (ids.isNotEmpty()) fileRepository.setFilesFavorite(ids, false)
                    folderIds.forEach { fileRepository.setFolderFavorite(it, false) }
                }

                CollectionType.AVAILABLE_OFFLINE ->
                    makeAvailableOffline(ids, folderIds, available = false)

                CollectionType.ARCHIVED -> {
                    if (ids.isNotEmpty()) fileRepository.setFilesArchived(ids, false)
                    folderIds.forEach { fileRepository.setFolderArchived(it, false) }
                }

                CollectionType.HIDDEN -> {
                    if (ids.isNotEmpty()) fileRepository.setFilesHidden(ids, false)
                    folderIds.forEach { fileRepository.setFolderHidden(it, false) }
                }
            }
        }
    }

    fun trashSelected() {
        val ids = _selection.value.toList()
        val folderIds = _folderSelection.value.toList()
        clearSelection()
        viewModelScope.launch {
            if (ids.isNotEmpty()) trashRepository.moveFilesToTrash(ids)
            folderIds.forEach { trashRepository.moveFolderToTrash(it) }
        }
    }
}
