package com.drdisagree.teledrive.presentation.files

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.drdisagree.teledrive.R
import com.drdisagree.teledrive.domain.model.DriveFile
import com.drdisagree.teledrive.domain.model.DriveFolder
import com.drdisagree.teledrive.domain.model.FileSortField
import com.drdisagree.teledrive.domain.model.SortDirection
import com.drdisagree.teledrive.domain.model.ViewMode
import com.drdisagree.teledrive.presentation.common.CollectSnackbarMessages
import com.drdisagree.teledrive.presentation.common.add
import com.drdisagree.teledrive.presentation.common.isInitialLoad
import com.drdisagree.teledrive.presentation.common.rememberPosition
import com.drdisagree.teledrive.presentation.common.shareLocalFiles
import com.drdisagree.teledrive.presentation.components.BlockingProgressDialog
import com.drdisagree.teledrive.presentation.components.BottomBarSnackbarHost
import com.drdisagree.teledrive.presentation.components.ConfirmDialog
import com.drdisagree.teledrive.presentation.components.EmptyState
import com.drdisagree.teledrive.presentation.components.FileGridItem
import com.drdisagree.teledrive.presentation.components.FileInfoSheet
import com.drdisagree.teledrive.presentation.components.FileListItem
import com.drdisagree.teledrive.presentation.components.FolderGridItem
import com.drdisagree.teledrive.presentation.components.FolderInfoSheet
import com.drdisagree.teledrive.presentation.components.FolderPickerDialog
import com.drdisagree.teledrive.presentation.components.FolderRow
import com.drdisagree.teledrive.presentation.components.LoadingState
import com.drdisagree.teledrive.presentation.components.RenameDialog
import com.drdisagree.teledrive.presentation.components.liftedTopAppBarColors
import com.drdisagree.teledrive.presentation.components.pinchZoom
import com.drdisagree.teledrive.presentation.components.rememberDragSelect
import com.drdisagree.teledrive.presentation.components.rememberToolbarLift
import com.drdisagree.teledrive.presentation.navigation.FabBottomBarInset
import com.drdisagree.teledrive.presentation.navigation.LocalBottomBarInset
import com.drdisagree.teledrive.presentation.preview.PreviewSequence
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FilesScreen(
    onOpenFolder: (String) -> Unit,
    onOpenCrumb: (String?) -> Unit,
    onOpenFile: (String, PreviewSequence) -> Unit,
    onOpenSearch: () -> Unit,
    onNewNote: (String?) -> Unit,
    onEditNote: (String, String) -> Unit,
    onBack: (() -> Unit)?,
    viewModel: FilesViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val renameTarget by viewModel.renameTarget.collectAsStateWithLifecycle()
    val files = viewModel.pagedFiles.collectAsLazyPagingItems()
    val infoTarget by viewModel.infoTarget.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val allSelected by viewModel.allSelected.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateFolder by remember { mutableStateOf(false) }
    var confirmDeleteLocal by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showMovePicker by remember { mutableStateOf(false) }
    var showCopyPicker by remember { mutableStateOf(false) }
    var showSelectionOverflow by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf(false) }
    val uploadPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> viewModel.importAndUpload(uris) }

    val deleteConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.retryLocalCopyRemoval()
    }

    CollectSnackbarMessages(viewModel.messages, snackbarHostState)
    LaunchedEffect(Unit) {
        viewModel.deleteConsentRequests.collect { request ->
            deleteConsentLauncher.launch(IntentSenderRequest.Builder(request).build())
        }
    }

    val working by viewModel.working.collectAsStateWithLifecycle()
    working?.let { BlockingProgressDialog(message = it) }

    BackHandler(enabled = state.selectionMode) { viewModel.clearSelection() }

    val gridState = rememberLazyGridState()
    gridState.rememberPosition(viewModel.listPosition, state.folders.size + files.itemCount)
    var fabVisible by remember { mutableStateOf(true) }
    val listScrolls by remember {
        derivedStateOf { gridState.canScrollForward || gridState.canScrollBackward }
    }
    LaunchedEffect(listScrolls) { if (!listScrolls) fabVisible = true }
    val fabScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!listScrolls) return Offset.Zero
                when {
                    available.y < -FAB_SCROLL_THRESHOLD -> fabVisible = false
                    available.y > FAB_SCROLL_THRESHOLD -> fabVisible = true
                }
                return Offset.Zero
            }
        }
    }
    val lifted by rememberToolbarLift(gridState)

    Scaffold(
        snackbarHost = {
            BottomBarSnackbarHost(
                hostState = snackbarHostState,
                applyInset = !(fabVisible && !state.selectionMode)
            )
        },
        topBar = {
            if (state.selectionMode) {
                TopAppBar(
                    colors = liftedTopAppBarColors(lifted),
                    title = {
                        Text(
                            text = stringResource(
                                R.string.common_selection_count,
                                state.selectionCount
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.common_clear_selection)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (allSelected) viewModel.clearSelection() else viewModel.selectAll()
                            }
                        ) {
                            Icon(
                                imageVector = if (allSelected) {
                                    Icons.Filled.Deselect
                                } else {
                                    Icons.Filled.SelectAll
                                },
                                contentDescription = if (allSelected) {
                                    stringResource(R.string.common_deselect_all)
                                } else {
                                    stringResource(R.string.common_select_all)
                                }
                            )
                        }
                        IconButton(onClick = { showMovePicker = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.DriveFileMove,
                                contentDescription = stringResource(R.string.files_move)
                            )
                        }
                        Box {
                            IconButton(onClick = { showSelectionOverflow = true }) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.common_actions)
                                )
                            }
                            DropdownMenu(
                                expanded = showSelectionOverflow,
                                onDismissRequest = { showSelectionOverflow = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.common_download)) },
                                    enabled = state.folderInSelection ||
                                            state.capabilities.canDownload,
                                    onClick = {
                                        showSelectionOverflow = false
                                        viewModel.downloadSelected()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.common_upload)) },
                                    enabled = !state.folderInSelection &&
                                            state.capabilities.canUpload,
                                    onClick = {
                                        showSelectionOverflow = false
                                        viewModel.uploadSelected()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.note_edit_action)) },
                                    enabled = state.selectionCount == 1 &&
                                            state.folderSelection.isEmpty(),
                                    onClick = {
                                        showSelectionOverflow = false
                                        viewModel.editSelectedNote()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.common_share)) },
                                    /* A folder is not a stream Android can hand
                                       to another app, so any folder in the
                                       selection rules sharing out. */
                                    enabled = state.selectionCount > 0 &&
                                            state.folderSelection.isEmpty(),
                                    onClick = {
                                        showSelectionOverflow = false
                                        viewModel.shareSelected()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.info_details)) },
                                    enabled = state.selectionCount +
                                            state.folderSelection.size == 1,
                                    onClick = {
                                        showSelectionOverflow = false
                                        viewModel.showInfoForSelection()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.common_rename)) },
                                    enabled = state.selectionCount == 1,
                                    onClick = {
                                        showSelectionOverflow = false
                                        viewModel.requestRenameSelected()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.files_copy)) },
                                    enabled = !state.folderInSelection,
                                    onClick = {
                                        showSelectionOverflow = false
                                        showCopyPicker = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.common_add_favorites)) },
                                    onClick = {
                                        showSelectionOverflow = false
                                        viewModel.favoriteSelected(true)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.common_free_space)) },
                                    enabled = !state.folderInSelection &&
                                            state.capabilities.canFreeUpSpace,
                                    onClick = {
                                        showSelectionOverflow = false
                                        confirmDeleteLocal = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.files_hide)) },
                                    enabled = !state.folderInSelection,
                                    onClick = {
                                        showSelectionOverflow = false
                                        viewModel.hideSelected(true)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.files_archive)) },
                                    enabled = !state.folderInSelection,
                                    onClick = {
                                        showSelectionOverflow = false
                                        viewModel.archiveSelected(true)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.common_move_trash)) },
                                    onClick = {
                                        showSelectionOverflow = false
                                        confirmTrash = true
                                    }
                                )
                            }
                        }
                    }
                )
            } else {
                TopAppBar(
                    colors = liftedTopAppBarColors(lifted),
                    title = { Text(stringResource(R.string.files)) },
                    navigationIcon = {
                        onBack?.let {
                            IconButton(onClick = it) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.common_back)
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenSearch) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = stringResource(R.string.files_search)
                            )
                        }
                        IconButton(onClick = { showCreateFolder = true }) {
                            Icon(
                                Icons.Filled.CreateNewFolder,
                                contentDescription = stringResource(R.string.files_new_folder)
                            )
                        }
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Sort,
                                contentDescription = stringResource(R.string.files_sort)
                            )
                        }
                        SortMenu(
                            expanded = showSortMenu,
                            current = state.sortField,
                            direction = state.sortDirection,
                            onDismiss = { showSortMenu = false },
                            onSelect = { field, direction ->
                                showSortMenu = false
                                viewModel.setSort(field, direction)
                            }
                        )
                    }
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = fabVisible && !state.selectionMode,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                FloatingActionButtonMenu(
                    expanded = showAddMenu,
                    modifier = Modifier.padding(
                        bottom = if (LocalBottomBarInset.current > 0.dp) {
                            FabBottomBarInset
                        } else {
                            0.dp
                        }
                    ),
                    button = {
                        ToggleFloatingActionButton(
                            checked = showAddMenu,
                            onCheckedChange = { showAddMenu = it }
                        ) {
                            Icon(
                                imageVector = if (showAddMenu) {
                                    Icons.Filled.Close
                                } else {
                                    Icons.Filled.Add
                                },
                                contentDescription = stringResource(R.string.common_add),
                                // The container animates to primary when checked.
                                tint = if (showAddMenu) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            )
                        }
                    }
                ) {
                    FloatingActionButtonMenuItem(
                        onClick = {
                            showAddMenu = false
                            uploadPicker.launch(arrayOf("*/*"))
                        },
                        icon = { Icon(Icons.Filled.Upload, contentDescription = null) },
                        text = { Text(stringResource(R.string.files_add_files)) }
                    )
                    FloatingActionButtonMenuItem(
                        onClick = {
                            showAddMenu = false
                            onNewNote(state.folderId)
                        },
                        icon = { Icon(Icons.Filled.EditNote, contentDescription = null) },
                        text = { Text(stringResource(R.string.note_new)) }
                    )
                }
            }
        }
    ) { padding ->
        /* A sort or filter change swaps in a fresh pager, which empties the
           list for a frame. Showing the loading screen again would unmount the
           rows and kill the reorder animation, so it is kept to the first load
           of this folder only. */
        var everLoaded by remember(state.folderId) { mutableStateOf(false) }
        val loadingNow = !state.loaded || files.isInitialLoad
        LaunchedEffect(loadingNow) {
            if (!loadingNow) everLoaded = true
        }
        val isLoading = loadingNow && !everLoaded
        val isEmpty = files.itemCount == 0 && state.folders.isEmpty() && !loadingNow
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .nestedScroll(fabScrollConnection)
        ) {
            if (state.breadcrumbs.size > 1) {
                Breadcrumbs(crumbs = state.breadcrumbs, onOpen = onOpenCrumb)
            }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                if (isLoading) {
                    LoadingState()
                } else if (isEmpty) {
                    EmptyState(
                        icon = Icons.Outlined.FolderOff,
                        title = stringResource(R.string.files_nothing_here_yet),
                        description = stringResource(R.string.files_back_import_appear)
                    )
                } else {
                    FilesContent(
                        gridState = gridState,
                        state = state,
                        files = files,
                        padding = PaddingValues(
                            bottom = padding.calculateBottomPadding() +
                                    LocalBottomBarInset.current
                        ),
                        onOpenFolder = onOpenFolder,
                        onOpenFile = onOpenFile,
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    if (showMovePicker || showCopyPicker) {
        val moving = showMovePicker
        FolderPickerHost(
            title = stringResource(
                if (moving) R.string.files_move_to else R.string.files_copy_to
            ),
            confirmLabel = stringResource(
                if (moving) R.string.files_move_here else R.string.files_copy_here
            ),
            viewModel = viewModel,
            excludedFolderIds = state.folderSelection,
            onConfirm = { target ->
                showMovePicker = false
                showCopyPicker = false
                if (moving) viewModel.moveSelected(target) else viewModel.copySelected(target)
            },
            onDismiss = {
                showMovePicker = false
                showCopyPicker = false
            }
        )
    }
    if (showCreateFolder) {
        RenameDialog(
            title = stringResource(R.string.files_new_folder),
            initialValue = "",
            confirmLabel = stringResource(R.string.common_create),
            onConfirm = {
                showCreateFolder = false
                viewModel.createFolder(it)
            },
            onDismiss = { showCreateFolder = false }
        )
    }
    renameTarget?.let { target ->
        RenameDialog(
            title = if (target.isFolder) stringResource(R.string.files_rename_folder) else stringResource(
                R.string.files_rename_file
            ),
            initialValue = target.name,
            confirmLabel = stringResource(R.string.common_rename),
            onConfirm = viewModel::confirmRename,
            onDismiss = viewModel::dismissRename
        )
    }
    if (confirmTrash) {
        ConfirmDialog(
            title = stringResource(
                R.string.common_confirm_trash_count_title,
                state.selectionCount
            ),
            message = stringResource(R.string.common_restore_trash_emptied),
            confirmLabel = stringResource(R.string.common_move_trash),
            destructive = true,
            onConfirm = {
                confirmTrash = false
                viewModel.trashSelected()
            },
            onDismiss = { confirmTrash = false }
        )
    }
    if (confirmDeleteLocal) {
        ConfirmDialog(
            title = stringResource(R.string.files_delete_local_copies),
            message = stringResource(R.string.files_only_files_verified_telegram),
            confirmLabel = stringResource(R.string.files_delete_local),
            destructive = true,
            onConfirm = {
                confirmDeleteLocal = false
                viewModel.deleteLocalCopies()
            },
            onDismiss = { confirmDeleteLocal = false }
        )
    }
    LaunchedEffect(Unit) {
        viewModel.editRequests.collect { request ->
            onEditNote(request.fileId, request.title)
        }
    }

    val shareContext = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.shareRequests.collect { request ->
            shareLocalFiles(shareContext, request.paths, request.mimeType)
        }
    }

    val sharedUris by viewModel.sharedUris.collectAsStateWithLifecycle()
    if (sharedUris.isNotEmpty()) {
        FolderPickerHost(
            title = stringResource(R.string.files_share_destination_title),
            confirmLabel = stringResource(R.string.files_share_destination_confirm),
            viewModel = viewModel,
            onConfirm = { target -> viewModel.acceptShare(sharedUris, target) },
            onDismiss = viewModel::dismissShare
        )
    }

    val folderInfoTarget by viewModel.folderInfoTarget.collectAsStateWithLifecycle()
    folderInfoTarget?.let { folder ->
        FolderInfoSheet(folder = folder, onDismiss = viewModel::dismissFolderInfo)
    }

    infoTarget?.let { file ->
        FileInfoSheet(file = file, onDismiss = viewModel::dismissInfo)
    }
}

/**
 * Folder path strip. Deep trees keep only the last three folders inline; the
 * rest collapse into a leading menu so the row never grows past one line.
 */
@Composable
private fun Breadcrumbs(
    crumbs: List<FolderCrumb>,
    onOpen: (String?) -> Unit
) {
    var showCollapsed by remember { mutableStateOf(false) }
    val inlineCount = INLINE_CRUMBS.coerceAtMost(crumbs.size)
    val collapsed = crumbs.dropLast(inlineCount)
    val inline = crumbs.takeLast(inlineCount)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (collapsed.isNotEmpty()) {
            Box {
                CrumbLabel(
                    text = "…",
                    current = false,
                    onClick = { showCollapsed = true }
                )
                DropdownMenu(
                    expanded = showCollapsed,
                    onDismissRequest = { showCollapsed = false }
                ) {
                    collapsed.forEach { crumb ->
                        DropdownMenuItem(
                            text = { Text(crumb.name) },
                            onClick = {
                                showCollapsed = false
                                onOpen(crumb.id)
                            }
                        )
                    }
                }
            }
            CrumbSeparator()
        }
        inline.forEachIndexed { index, crumb ->
            if (index > 0) CrumbSeparator()
            CrumbLabel(
                text = crumb.name,
                current = index == inline.lastIndex,
                onClick = { onOpen(crumb.id) },
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}

@Composable
private fun CrumbSeparator() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun CrumbLabel(
    text: String,
    current: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (current) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.primary
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .then(if (current) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = 6.dp, vertical = 4.dp)
    )
}

private const val INLINE_CRUMBS = 3

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilesContent(
    gridState: LazyGridState,
    state: FilesUiState,
    files: LazyPagingItems<DriveFile>,
    padding: PaddingValues,
    onOpenFolder: (String) -> Unit,
    onOpenFile: (String, PreviewSequence) -> Unit,
    viewModel: FilesViewModel
) {
    val columns = if (state.viewMode == ViewMode.GRID) state.gridSize else 1
    val dragSelect = rememberDragSelect(
        gridState = gridState,
        onStart = viewModel::startRangeSelection,
        onRange = { range ->
            val folderIds = mutableListOf<String>()
            val fileIds = mutableListOf<String>()
            for (index in range) {
                if (index < state.folders.size) {
                    folderIds += state.folders[index].id
                } else {
                    files.peek(index - state.folders.size)?.let { fileIds += it.id }
                }
            }
            viewModel.extendRangeSelection(fileIds, folderIds)
        },
        onEnd = viewModel::endRangeSelection
    )
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(columns),
        modifier = Modifier
            .fillMaxSize()
            .pinchZoom(onZoomIn = viewModel::zoomIn, onZoomOut = viewModel::zoomOut)
            .then(dragSelect),
        contentPadding = padding.add(horizontal = 12.dp, top = 12.dp, bottom = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(
            count = state.folders.size,
            key = { "folder-${state.folders[it].id}" },
            span = {
                if (state.viewMode == ViewMode.GRID) GridItemSpan(1)
                else GridItemSpan(maxLineSpan)
            }
        ) { index ->
            val folder = state.folders[index]
            val folderSelected = folder.id in state.folderSelection
            if (state.viewMode == ViewMode.GRID) {
                FolderGridItem(
                    folder = folder,
                    selected = folderSelected,
                    onClick = {
                        if (state.selectionMode) viewModel.toggleFolderSelection(folder.id)
                        else onOpenFolder(folder.id)
                    },
                    onLongClick = { viewModel.toggleFolderSelection(folder.id) },
                    modifier = Modifier.animateItem()
                )
            } else {
                FolderRow(
                    folder = folder,
                    selected = folderSelected,
                    onClick = {
                        if (state.selectionMode) viewModel.toggleFolderSelection(folder.id)
                        else onOpenFolder(folder.id)
                    },
                    onLongClick = { viewModel.toggleFolderSelection(folder.id) },
                    modifier = Modifier.animateItem()
                )
            }
        }

        items(
            count = files.itemCount,
            key = files.itemKey { "file-${it.id}" }
        ) { index ->
            val file = files[index] ?: return@items
            val selected = file.id in state.selection
            if (state.viewMode == ViewMode.GRID) {
                FileGridItem(
                    file = file,
                    selected = selected,
                    onClick = {
                        if (state.selectionMode) viewModel.toggleSelection(file.id)
                        else onOpenFile(
                            file.id,
                            PreviewSequence(
                                folderId = state.folderId,
                                filterByFolder = true,
                                sortField = state.sortField,
                                sortDirection = state.sortDirection
                            )
                        )
                    },
                    onLongClick = { viewModel.toggleSelection(file.id) },
                    modifier = Modifier.animateItem()
                )
            } else {
                FileListItem(
                    file = file,
                    selected = selected,
                    selectionMode = state.selectionMode,
                    onClick = {
                        if (state.selectionMode) viewModel.toggleSelection(file.id)
                        else onOpenFile(
                            file.id,
                            PreviewSequence(
                                folderId = state.folderId,
                                filterByFolder = true,
                                sortField = state.sortField,
                                sortDirection = state.sortDirection
                            )
                        )
                    },
                    onLongClick = { viewModel.toggleSelection(file.id) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SortMenu(
    expanded: Boolean,
    current: FileSortField,
    direction: SortDirection,
    onDismiss: () -> Unit,
    onSelect: (FileSortField, SortDirection) -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        FileSortField.entries.forEach { field ->
            val selected = field == current
            val ascending = direction == SortDirection.ASCENDING
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            when (field) {
                                FileSortField.NAME -> R.string.files_sort_name
                                FileSortField.SIZE -> R.string.files_sort_size
                                FileSortField.DATE_MODIFIED -> R.string.files_sort_date_modified
                                FileSortField.DATE_ADDED -> R.string.files_sort_date_added
                                FileSortField.TYPE -> R.string.files_sort_type
                                FileSortField.BACKUP_STATUS -> R.string.files_sort_backup_status
                            }
                        )
                    )
                },
                trailingIcon = if (selected) {
                    {
                        Icon(
                            imageVector = if (ascending) {
                                Icons.Filled.ArrowUpward
                            } else {
                                Icons.Filled.ArrowDownward
                            },
                            contentDescription = stringResource(
                                if (ascending) {
                                    R.string.files_sort_ascending
                                } else {
                                    R.string.files_sort_descending
                                }
                            ),
                            modifier = Modifier.size(SORT_ICON_SIZE)
                        )
                    }
                } else null,
                onClick = {
                    val newDirection = if (selected && ascending) {
                        SortDirection.DESCENDING
                    } else {
                        SortDirection.ASCENDING
                    }
                    onSelect(field, newDirection)
                }
            )
        }
    }
}

/**
 * Bridges the picker's synchronous tree callbacks to the suspending
 * repository by caching each level as it is browsed.
 */
@Composable
private fun FolderPickerHost(
    title: String,
    confirmLabel: String,
    viewModel: FilesViewModel,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
    excludedFolderIds: Set<String> = emptySet()
) {
    val childrenCache = remember { mutableStateMapOf<String, List<DriveFolder>>() }
    val namesCache = remember { mutableStateMapOf<String, String>() }
    val parentsCache = remember { mutableStateMapOf<String, String?>() }
    var requestedLevel by remember { mutableStateOf<String?>(ROOT_KEY) }
    var reloadToken by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(requestedLevel, reloadToken) {
        val level = requestedLevel ?: return@LaunchedEffect
        val parentId = level.takeIf { it != ROOT_KEY }
        val folders = viewModel.childFolders(parentId)
        childrenCache[level] = folders
        folders.forEach { folder ->
            namesCache[folder.id] = folder.name
            parentsCache[folder.id] = parentId
        }
    }

    FolderPickerDialog(
        title = title,
        confirmLabel = confirmLabel,
        childrenOf = { parentId ->
            val key = parentId ?: ROOT_KEY
            childrenCache[key] ?: run {
                requestedLevel = key
                emptyList()
            }
        },
        nameOf = { namesCache[it].orEmpty() },
        parentOf = { parentsCache[it] },
        excludedFolderIds = excludedFolderIds,
        onCreateFolder = { parentId, name ->
            scope.launch {
                if (viewModel.createFolderIn(parentId, name)) {
                    /* Drop the cached level so the new folder shows up. */
                    childrenCache.remove(parentId ?: ROOT_KEY)
                    requestedLevel = parentId ?: ROOT_KEY
                    reloadToken++
                }
            }
        },
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

private const val ROOT_KEY = "__root__"

private const val FAB_SCROLL_THRESHOLD = 6f

private val SORT_ICON_SIZE = 18.dp
