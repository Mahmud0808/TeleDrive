package com.drdisagree.teledrive.presentation.gallery

import com.drdisagree.teledrive.core.common.AppError
import com.drdisagree.teledrive.presentation.common.UiText
import com.drdisagree.teledrive.presentation.common.toUiText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.drdisagree.teledrive.domain.model.DriveFolder
import com.drdisagree.teledrive.core.common.AppResult
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.files_queued_for_download
import com.drdisagree.teledrive.domain.model.DriveFile
import com.drdisagree.teledrive.domain.model.FileCategory
import com.drdisagree.teledrive.domain.model.FileQuerySpec
import com.drdisagree.teledrive.domain.model.FileSortField
import com.drdisagree.teledrive.domain.model.MediaAlbum
import com.drdisagree.teledrive.domain.model.SortDirection
import com.drdisagree.teledrive.domain.repository.FileRepository
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import com.drdisagree.teledrive.domain.repository.SyncRepository
import com.drdisagree.teledrive.domain.repository.TransferRepository
import com.drdisagree.teledrive.domain.repository.TrashRepository
import com.drdisagree.teledrive.domain.usecase.MakeAvailableOfflineUseCase
import com.drdisagree.teledrive.presentation.common.Formatters
import com.drdisagree.teledrive.presentation.common.ListPosition
import com.drdisagree.teledrive.presentation.components.GridZoomLevel
import com.drdisagree.teledrive.presentation.components.MAX_GRID_COLUMNS
import com.drdisagree.teledrive.presentation.components.MIN_GRID_COLUMNS
import com.drdisagree.teledrive.presentation.components.SelectionCapabilities
import com.drdisagree.teledrive.presentation.components.zoomedIn
import com.drdisagree.teledrive.presentation.components.zoomedOut
import com.drdisagree.teledrive.presentation.navigation.Route
import com.drdisagree.teledrive.presentation.preview.PreviewSequence
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModel(
    savedStateHandle: SavedStateHandle,
    private val fileRepository: FileRepository,
    private val trashRepository: TrashRepository,
    private val transferRepository: TransferRepository,
    private val settingsRepository: SettingsRepository,
    private val syncRepository: SyncRepository,
    private val makeAvailableOffline: MakeAvailableOfflineUseCase
) : ViewModel() {

    private val _messages = MutableSharedFlow<UiText>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private fun report(error: AppError) {
        _messages.tryEmit(error.toUiText())
    }

    private fun AppResult<*>.reportFailure() {
        if (this is AppResult.Failure) report(error)
    }

    private val route: Route.GalleryAlbum? = runCatching {
        savedStateHandle.toRoute<Route.GalleryAlbum>()
    }.getOrNull()

    private val albumFolderId: String? = route?.folderId
    private val isAlbumView: Boolean = route != null

    private val tab = MutableStateFlow(GalleryTab.ALL)
    private val sort = MutableStateFlow(FileSortField.DATE_MODIFIED to SortDirection.DESCENDING)

    val listPosition = ListPosition(savedStateHandle)

    private val selection = MutableStateFlow<Set<String>>(emptySet())
    private var rangeBase: Set<String>? = null
    private val _allSelected = MutableStateFlow(false)
    val allSelected: StateFlow<Boolean> = _allSelected.asStateFlow()

    private val spec: Flow<FileQuerySpec> = combine(
        tab,
        sort,
        settingsRepository.preferences
    ) { currentTab, (field, direction), _ ->
        FileQuerySpec(
            folderId = albumFolderId,
            filterByFolder = isAlbumView,
            categories = when (currentTab) {
                GalleryTab.PHOTOS -> listOf(FileCategory.IMAGE)
                GalleryTab.VIDEOS -> listOf(FileCategory.VIDEO)
                else -> listOf(FileCategory.IMAGE, FileCategory.VIDEO)
            },
            showHidden = false,
            showArchived = false,
            sortField = field,
            sortDirection = direction
        )
    }.distinctUntilChanged()

    private val albums: Flow<List<MediaAlbum>> = settingsRepository.preferences
        .map { it.showHiddenFiles to it.showArchivedFiles }
        .distinctUntilChanged()
        .flatMapLatest {
            if (isAlbumView) flowOf(emptyList())
            else fileRepository.observeAlbums(showHidden = false, showArchived = false)
        }

    val previewSequence: StateFlow<PreviewSequence> = combine(tab, sort) { current, (field, dir) ->
        PreviewSequence(
            folderId = albumFolderId,
            filterByFolder = isAlbumView,
            categories = when (current) {
                GalleryTab.PHOTOS -> listOf(FileCategory.IMAGE)
                GalleryTab.VIDEOS -> listOf(FileCategory.VIDEO)
                else -> listOf(FileCategory.IMAGE, FileCategory.VIDEO)
            },
            sortField = field,
            sortDirection = dir
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreviewSequence())

    val pagedMedia: Flow<PagingData<GalleryListItem>> = spec
        .flatMapLatest { current ->
            fileRepository.pagedFiles(current).map { data -> withDayHeaders(data, current) }
        }
        .cachedIn(viewModelScope)

    private val selectionCapabilities: Flow<Pair<Set<String>, SelectionCapabilities>> = selection
        .flatMapLatest { ids ->
            flow {
                emit(ids to SelectionCapabilities.of(fileRepository.getFiles(ids.toList())))
            }
        }

    val uiState: StateFlow<GalleryUiState> = combine(
        tab,
        sort,
        selectionCapabilities,
        settingsRepository.preferences
            .map { it.galleryGridSize to it.albumGridSize }
            .distinctUntilChanged(),
        combine(
            albums,
            settingsRepository.preferences.map { it.galleryViewMode }.distinctUntilChanged()
        ) { albumList, mode -> albumList to mode }
    ) { currentTab, (field, direction), selectionState, sizes, extras ->
        val (selected, capabilities) = selectionState
        val (albumList, mode) = extras
        val (gridSize, albumGridSize) = sizes
        GalleryUiState(
            tab = currentTab,
            albums = albumList,
            albumTitle = route?.title,
            viewMode = mode,
            isAlbumView = isAlbumView,
            gridSize = gridSize,
            albumGridSize = albumGridSize,
            sortField = field,
            sortDirection = direction,
            selection = selected,
            capabilities = capabilities,
            loaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryUiState())

    private fun withDayHeaders(
        data: PagingData<DriveFile>,
        spec: FileQuerySpec
    ): PagingData<GalleryListItem> {
        val scope = spec.categories.joinToString("+") { it.name }
        val mapped = data.map<DriveFile, GalleryListItem> { GalleryListItem.Media(it, scope) }
        if (spec.sortField != FileSortField.DATE_MODIFIED &&
            spec.sortField != FileSortField.DATE_ADDED
        ) {
            return mapped
        }
        return mapped.insertSeparators { before, after ->
            val next = (after as? GalleryListItem.Media)?.file ?: return@insertSeparators null
            val nextDay = Formatters.dayStart(timestampFor(next, spec.sortField))
            val previous = (before as? GalleryListItem.Media)?.file
            val previousDay = previous
                ?.let { Formatters.dayStart(timestampFor(it, spec.sortField)) }
            if (previousDay == nextDay) null else GalleryListItem.DayHeader(nextDay, scope)
        }
    }

    private fun timestampFor(file: DriveFile, sortField: FileSortField): Long =
        if (sortField == FileSortField.DATE_ADDED) file.addedAt else file.modifiedAt

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    fun refresh() {
        if (_refreshing.value) return
        _refreshing.update { true }
        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            syncRepository.fullResync()
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed < MIN_REFRESH_VISIBLE_MS) delay((MIN_REFRESH_VISIBLE_MS - elapsed).milliseconds)
            _refreshing.update { false }
        }
    }

    fun setTab(value: GalleryTab) {
        clearSelection()
        tab.update { value }
    }

    fun setSort(field: FileSortField, direction: SortDirection) =
        sort.update { field to direction }

    fun zoomIn() = applyZoom(GridZoomLevel::zoomedIn)

    fun zoomOut() = applyZoom(GridZoomLevel::zoomedOut)

    fun zoomAlbumsIn() = stepAlbumColumns(-1)

    fun zoomAlbumsOut() = stepAlbumColumns(1)

    private fun stepAlbumColumns(step: Int) {
        viewModelScope.launch {
            settingsRepository.update { prefs ->
                prefs.copy(
                    albumGridSize = (prefs.albumGridSize + step)
                        .coerceIn(MIN_GRID_COLUMNS, MAX_GRID_COLUMNS)
                )
            }
        }
    }

    private fun applyZoom(transform: (GridZoomLevel) -> GridZoomLevel) {
        viewModelScope.launch {
            settingsRepository.update { prefs ->
                val next = transform(GridZoomLevel(prefs.galleryViewMode, prefs.galleryGridSize))
                prefs.copy(galleryViewMode = next.viewMode, galleryGridSize = next.gridSize)
            }
        }
    }

    fun toggleSelection(id: String) {
        _allSelected.update { false }
        selection.update { if (id in it) it - id else it + id }
    }

    fun clearSelection() {
        _allSelected.update { false }
        selection.update { emptySet() }
    }

    fun selectAll() {
        viewModelScope.launch {
            selection.update { fileRepository.fileIds(spec.first()).toSet() }
            _allSelected.update { true }
        }
    }

    fun startRangeSelection() {
        rangeBase = selection.value
    }

    fun extendRangeSelection(ids: List<String>) {
        val base = rangeBase ?: return
        _allSelected.update { false }
        selection.update { base + ids }
    }

    fun endRangeSelection() {
        rangeBase = null
    }

    suspend fun childFolders(parentId: String?): List<DriveFolder> =
        fileRepository.observeFolders(parentId, showHidden = true, showArchived = true).first()

    suspend fun createFolderIn(parentId: String?, name: String): Boolean =
        when (val result = fileRepository.createFolder(parentId, name)) {
            is AppResult.Success -> true
            is AppResult.Failure -> {
                report(result.error)
                false
            }
        }

    fun moveSelected(targetFolderId: String?) {
        val ids = selection.value.toList()
        clearSelection()
        if (ids.isEmpty()) return
        viewModelScope.launch { fileRepository.moveFiles(ids, targetFolderId).reportFailure() }
    }

    fun trashSelected() {
        val ids = selection.value.toList()
        clearSelection()
        viewModelScope.launch { trashRepository.moveFilesToTrash(ids).reportFailure() }
    }

    fun downloadSelected() {
        val ids = selection.value.toList()
        clearSelection()
        viewModelScope.launch {
            ids.forEach { transferRepository.enqueueDownload(it).reportFailure() }
        }
    }

    fun uploadSelected() {
        val ids = selection.value.toList()
        clearSelection()
        viewModelScope.launch {
            ids.forEach { transferRepository.enqueueUpload(it).reportFailure() }
        }
    }

    private val _renameTarget = MutableStateFlow<DriveFile?>(null)
    val renameTarget: StateFlow<DriveFile?> = _renameTarget.asStateFlow()

    fun requestRenameSelected() {
        val id = selection.value.singleOrNull() ?: return
        viewModelScope.launch { _renameTarget.value = fileRepository.getFile(id) }
    }

    fun dismissRename() {
        _renameTarget.value = null
    }

    fun confirmRename(newName: String) {
        val target = _renameTarget.value ?: return
        _renameTarget.value = null
        clearSelection()
        viewModelScope.launch { fileRepository.renameFile(target.id, newName).reportFailure() }
    }

    fun favoriteSelected(favorite: Boolean) {
        val ids = selection.value.toList()
        clearSelection()
        viewModelScope.launch { fileRepository.setFilesFavorite(ids, favorite) }
    }

    fun setSelectedAvailableOffline(available: Boolean) {
        val ids = selection.value.toList()
        clearSelection()
        viewModelScope.launch {
            val queued = makeAvailableOffline(ids, available = available)
            if (queued > 0) {
                _messages.tryEmit(UiText.Resource(Res.string.files_queued_for_download, queued))
            }
        }
    }

    fun hideSelected(hidden: Boolean) {
        val ids = selection.value.toList()
        clearSelection()
        viewModelScope.launch { fileRepository.setFilesHidden(ids, hidden) }
    }

    fun archiveSelected(archived: Boolean) {
        val ids = selection.value.toList()
        clearSelection()
        viewModelScope.launch { fileRepository.setFilesArchived(ids, archived) }
    }

    companion object {
        private const val MIN_REFRESH_VISIBLE_MS = 700L
    }
}
