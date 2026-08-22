package com.drdisagree.teledrive.presentation.preview

import android.content.IntentSender
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.data.local.FileQueryBuilder
import com.drdisagree.teledrive.domain.model.DriveFile
import com.drdisagree.teledrive.domain.model.FileCategory
import com.drdisagree.teledrive.domain.model.FileSortField
import com.drdisagree.teledrive.domain.model.SortDirection
import com.drdisagree.teledrive.domain.repository.FileRepository
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import com.drdisagree.teledrive.domain.repository.TransferRepository
import com.drdisagree.teledrive.domain.repository.TrashRepository
import com.drdisagree.teledrive.presentation.common.toUserMessage
import com.drdisagree.teledrive.presentation.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.content.Context
import com.drdisagree.teledrive.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import com.drdisagree.teledrive.domain.model.LinkMetadata

data class PreviewUiState(
    val files: List<DriveFile> = emptyList(),
    val initialIndex: Int = 0,
    val ready: Boolean = false,
    val closed: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PreviewViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
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

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
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
                sortField != null -> FileQueryBuilder.Spec(
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

                route.mediaOnly -> FileQueryBuilder.Spec(
                    categories = listOf(FileCategory.IMAGE, FileCategory.VIDEO),
                    showHidden = target.isHidden,
                    showArchived = target.isArchived,
                    sortField = prefs.sortField,
                    sortDirection = prefs.sortDirection
                )

                else -> FileQueryBuilder.Spec(
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
                transferRepository.observeActive()
            ) { latest, transfers ->
                val incoming = transfers.firstOrNull { task ->
                    task.fileId == file.id && task.type.isIncoming
                }
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
                is AppResult.Success -> _messages.tryEmit(context.getString(R.string.message_download_queued))
                is AppResult.Failure -> _messages.tryEmit(result.error.toUserMessage(context))
            }
        }
    }

    fun upload(file: DriveFile) {
        viewModelScope.launch {
            when (val result = transferRepository.enqueueUpload(file.id)) {
                is AppResult.Success -> _messages.tryEmit(context.getString(R.string.message_upload_queued))
                is AppResult.Failure -> _messages.tryEmit(result.error.toUserMessage(context))
            }
        }
    }

    private val _deleteConsentRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
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
                                context.getString(R.string.preview_local_copy_removed)
                            } else {
                                context.getString(R.string.preview_nothing_to_remove)
                            }
                        )
                    }
                    refreshFile(fileId)
                }
                is AppResult.Failure -> _messages.tryEmit(result.error.toUserMessage(context))
            }
        }
    }

    fun setFavorite(file: DriveFile, favorite: Boolean) {
        viewModelScope.launch {
            fileRepository.setFilesFavorite(listOf(file.id), favorite)
            refreshFile(file.id)
            _messages.tryEmit(if (favorite) context.getString(R.string.preview_added_favorites) else context.getString(R.string.preview_removed_favorites))
        }
    }

    fun setHidden(file: DriveFile, hidden: Boolean) {
        viewModelScope.launch {
            fileRepository.setFilesHidden(listOf(file.id), hidden)
            refreshFile(file.id)
            _messages.tryEmit(if (hidden) context.getString(R.string.preview_hidden) else context.getString(R.string.preview_unhidden))
        }
    }

    fun setArchived(file: DriveFile, archived: Boolean) {
        viewModelScope.launch {
            fileRepository.setFilesArchived(listOf(file.id), archived)
            refreshFile(file.id)
            _messages.tryEmit(if (archived) context.getString(R.string.preview_archived) else context.getString(R.string.preview_unarchived))
        }
    }

    fun rename(file: DriveFile, newName: String) {
        viewModelScope.launch {
            when (val result = fileRepository.renameFile(file.id, newName)) {
                is AppResult.Success -> {
                    refreshFile(file.id)
                    _messages.tryEmit(context.getString(R.string.message_renamed))
                }
                is AppResult.Failure -> _messages.tryEmit(result.error.toUserMessage(context))
            }
        }
    }

    fun moveToTrash(file: DriveFile) {
        viewModelScope.launch {
            trashRepository.moveFilesToTrash(listOf(file.id))
            _messages.tryEmit(context.getString(R.string.message_moved_to_trash))
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
