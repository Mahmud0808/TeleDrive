package com.drdisagree.teledrive.presentation.preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.message_download_queued
import com.drdisagree.teledrive.resources.message_moved_to_trash
import com.drdisagree.teledrive.resources.message_renamed
import com.drdisagree.teledrive.resources.message_upload_queued
import com.drdisagree.teledrive.resources.preview_added_favorites
import com.drdisagree.teledrive.resources.preview_archived
import com.drdisagree.teledrive.resources.preview_hidden
import com.drdisagree.teledrive.resources.preview_local_copy_removed
import com.drdisagree.teledrive.resources.preview_nothing_to_remove
import com.drdisagree.teledrive.resources.preview_removed_favorites
import com.drdisagree.teledrive.resources.preview_unarchived
import com.drdisagree.teledrive.resources.preview_unhidden
import com.drdisagree.teledrive.core.files.DeleteConsentRequest
import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.domain.model.FileQuerySpec
import com.drdisagree.teledrive.domain.model.DriveFile
import com.drdisagree.teledrive.domain.model.FileCategory
import com.drdisagree.teledrive.domain.model.FileSortField
import com.drdisagree.teledrive.domain.model.LinkMetadata
import com.drdisagree.teledrive.domain.model.SortDirection
import com.drdisagree.teledrive.domain.repository.FileRepository
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import com.drdisagree.teledrive.domain.repository.TransferRepository
import com.drdisagree.teledrive.domain.repository.TrashRepository
import com.drdisagree.teledrive.presentation.common.UiText
import com.drdisagree.teledrive.presentation.common.toUiText
import com.drdisagree.teledrive.presentation.navigation.Route
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PreviewUiState(
    val files: List<DriveFile> = emptyList(),
    val initialIndex: Int = 0,
    val ready: Boolean = false,
    val closed: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class PreviewViewModel(
    savedStateHandle: SavedStateHandle,
    private val fileRepository: FileRepository,
    private val trashRepository: TrashRepository,
    private val transferRepository: TransferRepository,
    private val settingsRepository: SettingsRepository,
    private val contentResolver: PreviewContentResolver
) : ViewModel() {

    private val route: Route.Preview = savedStateHandle.toRoute()

    private val _uiState = MutableStateFlow(PreviewUiState())
    val uiState: StateFlow<PreviewUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<UiText>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private val _infoTarget = MutableStateFlow<DriveFile?>(null)
    val infoTarget: StateFlow<DriveFile?> = _infoTarget.asStateFlow()

    private val contentCache = mutableMapOf<String, StateFlow<PreviewContent>>()

    init {
        viewModelScope.launch {
            val target = fileRepository.getFile(route.fileId)
            if (target == null) {
                _uiState.update { it.copy(closed = true) }
                return@launch
            }
            val prefs = settingsRepository.preferences.first()
            val categories = route.categories
                ?.split(CATEGORY_SEPARATOR)
                ?.mapNotNull { name -> runCatching { FileCategory.valueOf(name) }.getOrNull() }
                .orEmpty()
            val sortField = route.sortField
                ?.let { name -> runCatching { FileSortField.valueOf(name) }.getOrNull() }
            val spec = when {
                sortField != null -> FileQuerySpec(
                    folderId = route.folderId,
                    filterByFolder = route.filterByFolder,
                    categories = categories,
                    nameQuery = route.nameQuery,
                    favoritesOnly = route.favoritesOnly,
                    hiddenOnly = route.hiddenOnly,
                    archivedOnly = route.archivedOnly,
                    showHidden = route.hiddenOnly || target.isHidden,
                    showArchived = route.archivedOnly || target.isArchived,
                    sortField = sortField,
                    sortDirection = if (route.sortDescending) {
                        SortDirection.DESCENDING
                    } else {
                        SortDirection.ASCENDING
                    }
                )

                route.mediaOnly -> FileQuerySpec(
                    categories = listOf(FileCategory.IMAGE, FileCategory.VIDEO),
                    showHidden = target.isHidden,
                    showArchived = target.isArchived,
                    sortField = prefs.sortField,
                    sortDirection = prefs.sortDirection
                )

                else -> FileQuerySpec(
                    folderId = target.folderId,
                    filterByFolder = true,
                    showHidden = target.isHidden,
                    showArchived = target.isArchived,
                    sortField = prefs.sortField,
                    sortDirection = prefs.sortDirection
                )
            }
            val siblings = fileRepository.observeFiles(spec).first()
            val files = if (siblings.any { it.id == target.id }) siblings else listOf(target)
            _uiState.update {
                it.copy(
                    files = files,
                    initialIndex = files.indexOfFirst { file -> file.id == target.id }
                        .coerceAtLeast(0),
                    ready = true
                )
            }
        }
    }

    val backgroundPlayback: StateFlow<Boolean> = settingsRepository.preferences
        .map { it.backgroundPlayback }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Follows the row and the transfer queue rather than resolving once, so a
     * download the user starts reports progress and the viewer switches to the
     * file the moment it lands. Freeing the local copy falls back the same way.
     */
    fun contentFor(file: DriveFile): StateFlow<PreviewContent> =
        contentCache.getOrPut(file.id) {
            combine(
                fileRepository.observeFile(file.id),
                transferRepository.observeActiveForFile(file.id)
            ) { latest, transfer ->
                val incoming = transfer?.takeIf { it.type.isIncoming }
                (latest ?: file) to incoming
            }
                .distinctUntilChanged()
                .flatMapLatest { (latest, incoming) ->
                    if (incoming != null) {
                        flowOf(
                            PreviewContent.DownloadProgress(
                                transferred = incoming.transferredBytes,
                                total = incoming.sizeBytes
                            )
                        )
                    } else {
                        contentResolver.resolve(latest)
                    }
                }
                .stateIn(
                    viewModelScope,
                    SharingStarted.Lazily,
                    PreviewContent.Loading
                )
        }

    /** Live file for the info sheet and rename updates. */
    fun observeFile(fileId: String): Flow<DriveFile?> = fileRepository.observeFile(fileId)

    fun download(file: DriveFile) {
        viewModelScope.launch {
            when (val result = transferRepository.enqueueDownload(file.id)) {
                is AppResult.Success -> _messages.tryEmit(UiText.Resource(Res.string.message_download_queued))
                is AppResult.Failure -> _messages.tryEmit(result.error.toUiText())
            }
        }
    }

    fun upload(file: DriveFile) {
        viewModelScope.launch {
            when (val result = transferRepository.enqueueUpload(file.id)) {
                is AppResult.Success -> _messages.tryEmit(UiText.Resource(Res.string.message_upload_queued))
                is AppResult.Failure -> _messages.tryEmit(result.error.toUiText())
            }
        }
    }

    private val _deleteConsentRequests = MutableSharedFlow<DeleteConsentRequest>(extraBufferCapacity = 1)
    val deleteConsentRequests = _deleteConsentRequests.asSharedFlow()
    private var pendingLocalCopyId: String? = null

    fun freeUpSpace(file: DriveFile) {
        removeLocalCopy(file.id)
    }

    fun retryLocalCopyRemoval() {
        pendingLocalCopyId?.let(::removeLocalCopy)
    }

    private suspend fun refreshFile(fileId: String) {
        val updated = fileRepository.getFiles(listOf(fileId)).firstOrNull() ?: return
        _uiState.update { state ->
            state.copy(files = state.files.map { if (it.id == fileId) updated else it })
        }
    }

    private fun removeLocalCopy(fileId: String) {
        viewModelScope.launch {
            when (val result = fileRepository.deleteLocalCopy(listOf(fileId))) {
                is AppResult.Success -> {
                    val consent = result.value.consentRequest
                    if (consent != null) {
                        pendingLocalCopyId = fileId
                        _deleteConsentRequests.tryEmit(consent)
                    } else {
                        pendingLocalCopyId = null
                        _messages.tryEmit(
                            if (result.value.deletedCount > 0) {
                                UiText.Resource(Res.string.preview_local_copy_removed)
                            } else {
                                UiText.Resource(Res.string.preview_nothing_to_remove)
                            }
                        )
                    }
                    refreshFile(fileId)
                }

                is AppResult.Failure -> _messages.tryEmit(result.error.toUiText())
            }
        }
    }

    fun setFavorite(file: DriveFile, favorite: Boolean) {
        viewModelScope.launch {
            fileRepository.setFilesFavorite(listOf(file.id), favorite)
            refreshFile(file.id)
            _messages.tryEmit(
                if (favorite) UiText.Resource(Res.string.preview_added_favorites) else UiText.Resource(Res.string.preview_removed_favorites
                )
            )
        }
    }

    fun setHidden(file: DriveFile, hidden: Boolean) {
        viewModelScope.launch {
            fileRepository.setFilesHidden(listOf(file.id), hidden)
            refreshFile(file.id)
            _messages.tryEmit(
                if (hidden) UiText.Resource(Res.string.preview_hidden) else UiText.Resource(Res.string.preview_unhidden
                )
            )
        }
    }

    fun setArchived(file: DriveFile, archived: Boolean) {
        viewModelScope.launch {
            fileRepository.setFilesArchived(listOf(file.id), archived)
            refreshFile(file.id)
            _messages.tryEmit(
                if (archived) UiText.Resource(Res.string.preview_archived) else UiText.Resource(Res.string.preview_unarchived
                )
            )
        }
    }

    fun rename(file: DriveFile, newName: String) {
        viewModelScope.launch {
            when (val result = fileRepository.renameFile(file.id, newName)) {
                is AppResult.Success -> {
                    refreshFile(file.id)
                    _messages.tryEmit(UiText.Resource(Res.string.message_renamed))
                }

                is AppResult.Failure -> _messages.tryEmit(result.error.toUiText())
            }
        }
    }

    fun moveToTrash(file: DriveFile) {
        viewModelScope.launch {
            trashRepository.moveFilesToTrash(listOf(file.id))
            _messages.tryEmit(UiText.Resource(Res.string.message_moved_to_trash))
            val remaining = _uiState.value.files.filterNot { it.id == file.id }
            if (remaining.isEmpty()) {
                _uiState.update { it.copy(closed = true) }
            } else {
                _uiState.update { it.copy(files = remaining) }
            }
        }
    }

    /** Pinch sizing is a reading preference, so it outlives the screen. */
    val textScale: StateFlow<Float> = settingsRepository.preferences
        .map { it.textPreviewScale }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1f)

    fun setTextScale(scale: Float) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(textPreviewScale = scale) }
        }
    }

    private val linkCache = mutableMapOf<String, LinkMetadata?>()

    /** Metadata is fetched once per link and reused for this screen. */
    suspend fun linkPreview(url: String): LinkMetadata? =
        linkCache.getOrPut(url) { fileRepository.linkPreview(url) }

    fun showInfo(file: DriveFile) {
        viewModelScope.launch {
            _infoTarget.update { fileRepository.getFile(file.id) ?: file }
        }
    }

    fun dismissInfo() = _infoTarget.update { null }
}
