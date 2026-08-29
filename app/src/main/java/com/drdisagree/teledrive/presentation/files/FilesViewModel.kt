package com.drdisagree.teledrive.presentation.files

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.drdisagree.teledrive.R
import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.core.files.FileImporter
import com.drdisagree.teledrive.core.files.MimeTypes
import com.drdisagree.teledrive.core.files.PendingShare
import com.drdisagree.teledrive.data.local.FileQueryBuilder
import com.drdisagree.teledrive.domain.model.DriveFile
import com.drdisagree.teledrive.domain.model.DriveFolder
import com.drdisagree.teledrive.domain.model.FileSortField
import com.drdisagree.teledrive.domain.model.SortDirection
import com.drdisagree.teledrive.domain.model.UserPreferences
import com.drdisagree.teledrive.domain.model.ViewMode
import com.drdisagree.teledrive.domain.repository.FileRepository
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import com.drdisagree.teledrive.domain.repository.SyncRepository
import com.drdisagree.teledrive.domain.repository.TransferRepository
import com.drdisagree.teledrive.domain.repository.TrashRepository
import com.drdisagree.teledrive.presentation.common.ListPosition
import com.drdisagree.teledrive.presentation.common.toUserMessage
import com.drdisagree.teledrive.presentation.components.GridZoomLevel
import com.drdisagree.teledrive.presentation.components.SelectionCapabilities
import com.drdisagree.teledrive.presentation.components.zoomedIn
import com.drdisagree.teledrive.presentation.components.zoomedOut
import com.drdisagree.teledrive.presentation.navigation.Route
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

data class RenameTarget(
    val id: String,
    val name: String,
    val isFolder: Boolean
)

data class FilesUiState(
    val folderId: String? = null,
    val folderName: String = "",
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
}

@OptIn(ExperimentalCoroutinesApi::class)
class FilesViewModel(
    private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val fileRepository: FileRepository,
    private val trashRepository: TrashRepository,
    private val transferRepository: TransferRepository,
    private val settingsRepository: SettingsRepository,
    private val syncRepository: SyncRepository,
    private val fileImporter: FileImporter,
    private val pendingShare: PendingShare
) : ViewModel() {

    private val folderId: String? = savedStateHandle.toRoute<Route.Files>().folderId

    val listPosition = ListPosition(savedStateHandle)

    private val selection = MutableStateFlow<Set<String>>(emptySet())
    private val folderSelection = MutableStateFlow<Set<String>>(emptySet())

    private val _working = MutableStateFlow<String?>(null)
    val working: StateFlow<String?> = _working.asStateFlow()

    private var rangeBase: Pair<Set<String>, Set<String>>? = null
    private val _allSelected = MutableStateFlow(false)
    val allSelected: StateFlow<Boolean> = _allSelected.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private val _folderInfoTarget = MutableStateFlow<DriveFolder?>(null)
    val folderInfoTarget: StateFlow<DriveFolder?> = _folderInfoTarget.asStateFlow()

    private val _editRequests = MutableSharedFlow<EditNoteRequest>(extraBufferCapacity = 2)
    val editRequests = _editRequests.asSharedFlow()

    private val _shareRequests = MutableSharedFlow<ShareRequest>(extraBufferCapacity = 2)
    val shareRequests = _shareRequests.asSharedFlow()

    private val _infoTarget = MutableStateFlow<DriveFile?>(null)
    val infoTarget: StateFlow<DriveFile?> = _infoTarget.asStateFlow()

    private val _copying = MutableStateFlow(false)
    val copying: StateFlow<Boolean> = _copying.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** Pulls newly added remote files into the local index. */
    fun refresh() {
        if (_refreshing.value) return
        _refreshing.update { true }
        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            when (val result = syncRepository.incrementalSync()) {
                is AppResult.Success -> {
                    val stats = result.value
                    if (stats.inserted > 0 || stats.updated > 0) {
                        _messages.tryEmit(
                            "${stats.inserted} new, ${stats.updated} updated"
                        )
                    }
                }

                is AppResult.Failure -> _messages.tryEmit(result.error.toUserMessage(context))
            }
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed < MIN_REFRESH_VISIBLE_MS) delay((MIN_REFRESH_VISIBLE_MS - elapsed).milliseconds)
            _refreshing.update { false }
        }
    }

    private val querySpec: Flow<FileQueryBuilder.Spec> = settingsRepository.preferences
        .map { prefs ->
            FileQueryBuilder.Spec(
                folderId = folderId,
                filterByFolder = true,
                showHidden = false,
                showArchived = false,
                sortField = prefs.sortField,
                sortDirection = prefs.sortDirection
            )
        }
        .distinctUntilChanged()

    val pagedFiles: Flow<PagingData<DriveFile>> = querySpec
        .flatMapLatest { fileRepository.pagedFiles(it) }
        .cachedIn(viewModelScope)

    /**
     * Follows the selected rows rather than sampling them once, so an action
     * taken from the menu updates the menu that is still open. Paths whose
     * file was deleted outside the app are cleared first.
     */
    private val selectionCapabilities: Flow<Pair<Set<String>, SelectionCapabilities>> = selection
        .flatMapLatest { ids ->
            if (ids.isEmpty()) {
                flowOf(ids to SelectionCapabilities.of(emptyList()))
            } else {
                flow {
                    fileRepository.reconcileLocalCopies(ids.toList())
                    emitAll(
                        fileRepository.observeFilesByIds(ids.toList())
                            .map { files -> ids to SelectionCapabilities.of(files) }
                    )
                }
            }
        }

    val uiState: StateFlow<FilesUiState> = combine(
        settingsRepository.preferences,
        selectionCapabilities,
        folderSelection,
        folderFlow(),
        settingsRepository.preferences.flatMapLatest { prefs ->
            fileRepository.observeFolders(
                parentId = folderId,
                showHidden = false,
                showArchived = false,
                sortField = prefs.sortField,
                sortDirection = prefs.sortDirection
            )
        }
    ) { prefs: UserPreferences, selectionState, selectedFolders, folder, folders ->
        val (selected, capabilities) = selectionState
        FilesUiState(
            folderId = folderId,
            folderName = folder?.name ?: context.getString(R.string.files_root_name),
            breadcrumbs = breadcrumbsFor(folder),
            folders = folders,
            selection = selected,
            folderSelection = selectedFolders,
            viewMode = prefs.viewMode,
            gridSize = prefs.gridSize,
            sortField = prefs.sortField,
            sortDirection = prefs.sortDirection,
            showHidden = prefs.showHiddenFiles,
            loaded = true,
            capabilities = capabilities
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FilesUiState())

    private fun folderFlow(): Flow<DriveFolder?> =
        folderId?.let { fileRepository.observeFolder(it) }
            ?: MutableStateFlow<DriveFolder?>(null)

    private suspend fun breadcrumbsFor(folder: DriveFolder?): List<FolderCrumb> {
        val trail = ArrayDeque<FolderCrumb>()
        var current = folder
        var guard = 0
        while (current != null && guard++ < MAX_BREADCRUMB_DEPTH) {
            trail.addFirst(FolderCrumb(current.id, current.name))
            current = current.parentId?.let { fileRepository.getFolder(it) }
        }
        trail.addFirst(FolderCrumb(null, context.getString(R.string.files_root_name)))
        return trail.toList()
    }

    fun toggleSelection(fileId: String) {
        _allSelected.update { false }
        selection.update { current ->
            if (fileId in current) current - fileId else current + fileId
        }
    }

    fun toggleFolderSelection(folderId: String) {
        _allSelected.update { false }
        folderSelection.update { current ->
            if (folderId in current) current - folderId else current + folderId
        }
    }

    fun clearSelection() {
        _allSelected.update { false }
        selection.update { emptySet() }
        folderSelection.update { emptySet() }
    }

    /** Selects every folder and file under the current folder, loaded or not. */
    fun selectAll() {
        viewModelScope.launch {
            val ids = fileRepository.fileIds(querySpec.first())
            folderSelection.update { uiState.value.folders.map { folder -> folder.id }.toSet() }
            selection.update { ids.toSet() }
            _allSelected.update { true }
        }
    }

    fun startRangeSelection() {
        rangeBase = selection.value to folderSelection.value
    }

    fun extendRangeSelection(fileIds: List<String>, folderIds: List<String>) {
        val base = rangeBase ?: return
        _allSelected.update { false }
        selection.update { base.first + fileIds }
        folderSelection.update { base.second + folderIds }
    }

    fun endRangeSelection() {
        rangeBase = null
    }

    fun zoomIn() = applyZoom(GridZoomLevel::zoomedIn)

    fun zoomOut() = applyZoom(GridZoomLevel::zoomedOut)

    private fun applyZoom(transform: (GridZoomLevel) -> GridZoomLevel) = updatePrefs { prefs ->
        val next = transform(GridZoomLevel(prefs.viewMode, prefs.gridSize))
        prefs.copy(viewMode = next.viewMode, gridSize = next.gridSize)
    }


    fun setSort(field: FileSortField, direction: SortDirection) =
        updatePrefs { it.copy(sortField = field, sortDirection = direction) }

    /** Creates a folder and reports whether it landed, for callers that refresh. */
    suspend fun createFolderIn(parentId: String?, name: String): Boolean =
        when (val result = fileRepository.createFolder(parentId, name)) {
            is AppResult.Success -> true
            is AppResult.Failure -> {
                _messages.tryEmit(result.error.toUserMessage(context))
                false
            }
        }

    fun createFolder(name: String) {
        viewModelScope.launch {
            when (val result = fileRepository.createFolder(folderId, name)) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> _messages.tryEmit(result.error.toUserMessage(context))
            }
        }
    }

    private val _renameTarget = MutableStateFlow<RenameTarget?>(null)
    val renameTarget: StateFlow<RenameTarget?> = _renameTarget.asStateFlow()

    fun requestRenameSelected() {
        val fileId = selection.value.singleOrNull()
        val folderId = folderSelection.value.singleOrNull()
        viewModelScope.launch {
            _renameTarget.value = when {
                fileId != null && folderId == null ->
                    fileRepository.getFile(fileId)?.let {
                        RenameTarget(it.id, it.name, isFolder = false)
                    }

                folderId != null && fileId == null ->
                    fileRepository.getFolder(folderId)?.let {
                        RenameTarget(it.id, it.name, isFolder = true)
                    }

                else -> null
            }
        }
    }

    fun dismissRename() {
        _renameTarget.value = null
    }

    fun confirmRename(newName: String) {
        val target = _renameTarget.value ?: return
        _renameTarget.value = null
        clearSelection()
        if (target.isFolder) renameFolder(target.id, newName) else renameFile(target.id, newName)
    }

    fun renameFile(fileId: String, newName: String) {
        viewModelScope.launch {
            fileRepository.renameFile(fileId, newName).let {
                if (it is AppResult.Failure) _messages.tryEmit(it.error.toUserMessage(context))
            }
        }
    }

    fun renameFolder(id: String, newName: String) {
        viewModelScope.launch {
            fileRepository.renameFolder(id, newName).let {
                if (it is AppResult.Failure) _messages.tryEmit(it.error.toUserMessage(context))
            }
        }
    }

    fun trashSelected() {
        val ids = selection.value.toList()
        val folderIds = folderSelection.value.toList()
        val total = ids.size + folderIds.size
        clearSelection()
        _working.value = "Moving $total to trash…"
        viewModelScope.launch {
            withContext(NonCancellable) {
                if (ids.isNotEmpty()) trashRepository.moveFilesToTrash(ids)
                folderIds.forEach { trashRepository.moveFolderToTrash(it) }
            }
            _working.value = null
            _messages.tryEmit(context.getString(R.string.message_moved_to_trash_count, total))
        }
    }

    fun uploadSelected() {
        val ids = selection.value.toList()
        clearSelection()
        viewModelScope.launch {
            var failures = 0
            ids.forEach { id ->
                if (transferRepository.enqueueUpload(id) is AppResult.Failure) failures++
            }
            _messages.tryEmit(
                if (failures == 0) "${ids.size} queued for upload"
                else "${ids.size - failures} queued, $failures failed"
            )
        }
    }

    fun downloadSelected() {
        val fileIds = selection.value.toList()
        val folderIds = folderSelection.value.toList()
        clearSelection()
        viewModelScope.launch {
            val ids = (
                    fileIds + folderIds.flatMap { fileRepository.fileIdsInTree(it) }
                    ).distinct()
            if (ids.isEmpty()) {
                _messages.tryEmit(context.getString(R.string.message_nothing_to_download))
                return@launch
            }
            var failures = 0
            ids.forEach { id ->
                if (transferRepository.enqueueDownload(id) is AppResult.Failure) failures++
            }
            _messages.tryEmit(
                if (failures == 0) "${ids.size} queued for download"
                else "${ids.size - failures} queued, $failures failed"
            )
        }
    }

    fun favoriteSelected(favorite: Boolean) {
        val ids = selection.value.toList()
        val folderIds = folderSelection.value.toList()
        clearSelection()
        viewModelScope.launch {
            if (ids.isNotEmpty()) fileRepository.setFilesFavorite(ids, favorite)
            folderIds.forEach { fileRepository.setFolderFavorite(it, favorite) }
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

    fun moveSelected(targetFolderId: String?) {
        val ids = selection.value.toList()
        val folderIds = folderSelection.value.toList()
        clearSelection()
        viewModelScope.launch {
            var failures = 0
            if (ids.isNotEmpty()) {
                val result = fileRepository.moveFiles(ids, targetFolderId)
                if (result is AppResult.Failure) {
                    _messages.tryEmit(result.error.toUserMessage(context))
                    return@launch
                }
            }
            folderIds.forEach { id ->
                val result = fileRepository.moveFolder(id, targetFolderId)
                if (result is AppResult.Failure) {
                    failures++
                    _messages.tryEmit(result.error.toUserMessage(context))
                }
            }
            if (failures == 0) _messages.tryEmit(
                context.getString(
                    R.string.message_moved_count,
                    ids.size + folderIds.size
                )
            )
        }
    }

    fun copySelected(targetFolderId: String?) {
        val ids = selection.value.toList()
        clearSelection()
        _copying.update { true }
        viewModelScope.launch {
            when (val result = fileRepository.copyFiles(ids, targetFolderId)) {
                is AppResult.Success -> _messages.tryEmit(
                    if (result.value == ids.size) "Copied ${result.value}"
                    else "Copied ${result.value} of ${ids.size}"
                )

                is AppResult.Failure -> _messages.tryEmit(result.error.toUserMessage(context))
            }
            _copying.update { false }
        }
    }

    /** Folder tree access for the move/copy picker. */
    suspend fun childFolders(parentId: String?): List<DriveFolder> =
        fileRepository.observeFolders(parentId, showHidden = true, showArchived = true).first()

    suspend fun folderName(id: String): String =
        fileRepository.getFolder(id)?.name.orEmpty()

    suspend fun parentFolderId(id: String): String? = fileRepository.getFolder(id)?.parentId

    private val _deleteConsentRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val deleteConsentRequests = _deleteConsentRequests.asSharedFlow()
    private var pendingLocalCopyIds: List<String> = emptyList()

    fun deleteLocalCopies() {
        val ids = selection.value.toList()
        clearSelection()
        removeLocalCopies(ids)
    }

    fun retryLocalCopyRemoval() {
        removeLocalCopies(pendingLocalCopyIds)
    }

    private fun removeLocalCopies(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            when (val result = fileRepository.deleteLocalCopy(ids)) {
                is AppResult.Success -> {
                    val consent = result.value.consentRequest
                    if (consent != null) {
                        pendingLocalCopyIds = ids
                        _deleteConsentRequests.tryEmit(consent)
                    } else {
                        pendingLocalCopyIds = emptyList()
                        _messages.tryEmit(
                            "Removed ${result.value.deletedCount} local copies"
                        )
                    }
                }

                is AppResult.Failure -> _messages.tryEmit(result.error.toUserMessage(context))
            }
        }
    }

    /** Imports picked documents into the drive and queues them for upload. */
    val sharedUris: StateFlow<List<Uri>> = pendingShare.uris

    /** Takes files handed over by another app into [target]. */
    fun acceptShare(uris: List<Uri>, target: String?) {
        pendingShare.clear()
        importAndUpload(uris, target)
    }

    fun dismissShare() = pendingShare.clear()

    fun importAndUpload(uris: List<Uri>, target: String? = folderId) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var imported = 0
            var restored = 0
            var duplicates = 0
            var copied = 0
            var failed = 0
            for (uri in uris) {
                val file = fileImporter.import(uri)
                if (file == null) {
                    failed++
                    continue
                }
                /* Identical bytes are already in Telegram. In this folder that
                   is a plain duplicate; elsewhere it only needs a server-side
                   copy, which costs no upload. */
                val existing = fileRepository.findDuplicate(file.path)
                if (existing != null) {
                    if (existing.folderId == target) {
                        duplicates++
                    } else if (fileRepository.copyFiles(listOf(existing.id), target)
                                is AppResult.Success
                    ) {
                        copied++
                    } else {
                        failed++
                    }
                    fileImporter.discard(file)
                    continue
                }
                when (val result = fileRepository.importLocalFile(file.path, target, file.name)) {
                    is AppResult.Success -> {
                        val drive = result.value
                        if (drive.hasRemoteCopy) {
                            restored++
                        } else {
                            transferRepository.enqueueUpload(drive.id)
                            imported++
                        }
                    }

                    is AppResult.Failure -> failed++
                }
            }
            _messages.tryEmit(importSummary(imported, restored, duplicates, copied, failed))
        }
    }

    private fun importSummary(
        imported: Int,
        restored: Int,
        duplicates: Int,
        copied: Int,
        failed: Int
    ): String = when {
        imported == 0 && restored == 0 && duplicates == 0 && copied > 0 ->
            context.getString(R.string.files_import_copied, copied)

        imported > 0 && copied > 0 ->
            context.getString(R.string.files_import_uploading_copied, imported, copied)

        imported == 0 && restored == 0 && duplicates > 0 ->
            context.getString(R.string.files_import_duplicates, duplicates)

        imported > 0 && duplicates > 0 ->
            context.getString(R.string.files_import_uploading_duplicates, imported, duplicates)

        imported == 0 && restored > 0 && failed == 0 ->
            context.getString(R.string.files_import_restored, restored)

        imported == 0 && restored == 0 ->
            context.getString(R.string.files_import_failed)

        restored > 0 && failed == 0 ->
            context.getString(R.string.files_import_uploading_restored, imported, restored)

        failed == 0 -> context.getString(R.string.files_import_uploading, imported)
        else -> context.getString(R.string.files_import_uploading_failed, imported, failed)
    }

    /** Opens the selected text file in the note editor. */
    fun editSelectedNote() {
        val fileId = selection.value.singleOrNull() ?: return
        clearSelection()
        viewModelScope.launch {
            val file = fileRepository.getFiles(listOf(fileId)).firstOrNull() ?: return@launch
            if (!MimeTypes.isText(file.mimeType)) {
                _messages.tryEmit(context.getString(R.string.note_not_editable))
                return@launch
            }
            _editRequests.tryEmit(
                EditNoteRequest(fileId = file.id, title = file.name.substringBeforeLast('.'))
            )
        }
    }

    /** Only files with a local copy can leave the app, so the rest are reported. */
    fun shareSelected() {
        val ids = selection.value.toList()
        if (ids.isEmpty()) return
        clearSelection()
        viewModelScope.launch {
            val files = fileRepository.getFiles(ids)
            val paths = files.mapNotNull { it.localPath }
            if (paths.isEmpty()) {
                _messages.tryEmit(context.getString(R.string.files_share_needs_local))
                return@launch
            }
            val mimeType = files.map { it.mimeType }.distinct().singleOrNull() ?: "*/*"
            _shareRequests.tryEmit(ShareRequest(paths, mimeType))
            if (paths.size < files.size) {
                _messages.tryEmit(context.getString(R.string.files_share_needs_local))
            }
        }
    }

    fun showInfo(file: DriveFile) = _infoTarget.update { file }

    /** Opens details for whichever single item is selected. */
    fun showInfoForSelection() {
        val fileId = selection.value.singleOrNull()
        val folderId = folderSelection.value.singleOrNull()
        viewModelScope.launch {
            when {
                fileId != null ->
                    fileRepository.getFiles(listOf(fileId)).firstOrNull()?.let { file ->
                        _infoTarget.update { file }
                    }

                folderId != null ->
                    uiState.value.folders.firstOrNull { it.id == folderId }?.let { folder ->
                        _folderInfoTarget.update { folder }
                    }
            }
            clearSelection()
        }
    }

    fun dismissFolderInfo() = _folderInfoTarget.update { null }

    fun dismissInfo() = _infoTarget.update { null }

    private fun updatePrefs(transform: (UserPreferences) -> UserPreferences) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    companion object {
        private const val MIN_REFRESH_VISIBLE_MS = 700L
        private const val MAX_BREADCRUMB_DEPTH = 64
    }
}

/** Local copies bound for another app. */
data class ShareRequest(val paths: List<String>, val mimeType: String)

/** A text file bound for the note editor. */
data class EditNoteRequest(val fileId: String, val title: String)
