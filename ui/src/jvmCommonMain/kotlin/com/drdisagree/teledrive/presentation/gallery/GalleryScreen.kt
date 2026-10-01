package com.drdisagree.teledrive.presentation.gallery

import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VisibilityOff
import com.drdisagree.teledrive.presentation.components.ActionMenu
import com.drdisagree.teledrive.presentation.components.MenuAction
import com.drdisagree.teledrive.resources.common_make_available_offline
import com.drdisagree.teledrive.resources.common_organize
import com.drdisagree.teledrive.resources.common_remove_from_offline
import com.drdisagree.teledrive.resources.common_storage_actions
import com.drdisagree.teledrive.resources.files_archive
import com.drdisagree.teledrive.resources.files_hide
import com.drdisagree.teledrive.resources.preview_remove_favorites
import androidx.compose.material3.SnackbarHostState
import com.drdisagree.teledrive.presentation.common.CollectSnackbarMessages
import com.drdisagree.teledrive.presentation.components.BottomBarSnackbarHost
import com.drdisagree.teledrive.presentation.files.FolderPickerHost
import com.drdisagree.teledrive.resources.files_move_to
import com.drdisagree.teledrive.resources.files_move_here
import com.drdisagree.teledrive.presentation.common.AppBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.common_actions
import com.drdisagree.teledrive.resources.common_add_favorites
import com.drdisagree.teledrive.resources.common_back
import com.drdisagree.teledrive.resources.common_clear_selection
import com.drdisagree.teledrive.resources.common_confirm_trash_count_title
import com.drdisagree.teledrive.resources.common_deselect_all
import com.drdisagree.teledrive.resources.common_download
import com.drdisagree.teledrive.resources.common_move_trash
import com.drdisagree.teledrive.resources.common_rename
import com.drdisagree.teledrive.resources.common_rename_file
import com.drdisagree.teledrive.resources.common_restore_trash_emptied
import com.drdisagree.teledrive.resources.common_select_all
import com.drdisagree.teledrive.resources.common_selection_count
import com.drdisagree.teledrive.resources.common_upload
import com.drdisagree.teledrive.resources.date_days_ago
import com.drdisagree.teledrive.resources.date_today
import com.drdisagree.teledrive.resources.date_yesterday
import com.drdisagree.teledrive.resources.gallery_albums_appear_organise_media
import com.drdisagree.teledrive.resources.gallery_no_albums
import com.drdisagree.teledrive.resources.gallery_no_media
import com.drdisagree.teledrive.resources.gallery_photos_videos_appear_here
import com.drdisagree.teledrive.resources.gallery_title
import com.drdisagree.teledrive.domain.model.MediaAlbum
import com.drdisagree.teledrive.domain.model.ViewMode
import com.drdisagree.teledrive.presentation.common.DayBucket
import com.drdisagree.teledrive.presentation.common.Formatters
import com.drdisagree.teledrive.presentation.common.isInitialLoad
import com.drdisagree.teledrive.presentation.components.RefreshAction
import com.drdisagree.teledrive.presentation.components.RefreshableContent
import com.drdisagree.teledrive.presentation.components.ConfirmDialog
import com.drdisagree.teledrive.presentation.components.EmptyState
import com.drdisagree.teledrive.presentation.components.FileGridItem
import com.drdisagree.teledrive.presentation.components.FileListItem
import com.drdisagree.teledrive.presentation.components.LoadingState
import com.drdisagree.teledrive.presentation.components.RenameDialog
import com.drdisagree.teledrive.presentation.components.liftedTopAppBarColors
import com.drdisagree.teledrive.presentation.components.pinchZoom
import com.drdisagree.teledrive.presentation.components.rememberDragSelect
import com.drdisagree.teledrive.presentation.components.rememberToolbarLift
import com.drdisagree.teledrive.presentation.navigation.LocalBottomBarInset
import com.drdisagree.teledrive.presentation.preview.PreviewSequence
import com.drdisagree.teledrive.presentation.common.rememberPosition

@Composable
private fun DayHeaderRow(dayStartMillis: Long, modifier: Modifier = Modifier) {
    Text(
        text = when (val bucket = Formatters.dayBucket(dayStartMillis)) {
            DayBucket.Today -> stringResource(Res.string.date_today)
            DayBucket.Yesterday -> stringResource(Res.string.date_yesterday)
            is DayBucket.DaysAgo -> stringResource(Res.string.date_days_ago, bucket.days)
            is DayBucket.Absolute -> bucket.text
        },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GalleryTabSelector(
    selected: GalleryTab,
    onSelect: (GalleryTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        GalleryTab.entries.forEachIndexed { index, tab ->
            ToggleButton(
                checked = tab == selected,
                onCheckedChange = { onSelect(tab) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    GalleryTab.entries.lastIndex ->
                        ButtonGroupDefaults.connectedTrailingButtonShapes()

                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(tab.labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AlbumGrid(
    albums: List<MediaAlbum>,
    columns: Int,
    bottomPadding: Dp,
    onOpenAlbum: (MediaAlbum) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit
) {
    if (albums.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.PhotoLibrary,
            title = stringResource(Res.string.gallery_no_albums),
            description = stringResource(Res.string.gallery_albums_appear_organise_media)
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier
            .fillMaxSize()
            .pinchZoom(onZoomIn = onZoomIn, onZoomOut = onZoomOut),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 12.dp,
            bottom = 12.dp + bottomPadding
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(albums, key = { it.folderId ?: UNFILED_ALBUM_KEY }) { album ->
            AlbumCard(
                album = album,
                onClick = { onOpenAlbum(album) },
                modifier = Modifier.animateItem()
            )
        }
    }
}

private const val UNFILED_ALBUM_KEY = "__unfiled__"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    onOpenFile: (String, PreviewSequence) -> Unit,
    onOpenAlbum: (MediaAlbum) -> Unit = {},
    onBack: (() -> Unit)? = null,
    viewModel: GalleryViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val media = viewModel.pagedMedia.collectAsLazyPagingItems()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    var confirmTrash by remember { mutableStateOf(false) }
    var showSelectionOverflow by remember { mutableStateOf(false) }
    var showMovePicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val renameTarget by viewModel.renameTarget.collectAsStateWithLifecycle()

    CollectSnackbarMessages(viewModel.messages, snackbarHostState)

    if (showMovePicker) {
        FolderPickerHost(
            title = stringResource(Res.string.files_move_to),
            confirmLabel = stringResource(Res.string.files_move_here),
            loadChildren = viewModel::childFolders,
            createFolder = viewModel::createFolderIn,
            onConfirm = { target ->
                showMovePicker = false
                viewModel.moveSelected(target)
            },
            onDismiss = { showMovePicker = false }
        )
    }

    if (confirmTrash) {
        ConfirmDialog(
            title = stringResource(
                Res.string.common_confirm_trash_count_title,
                state.selection.size
            ),
            message = stringResource(Res.string.common_restore_trash_emptied),
            confirmLabel = stringResource(Res.string.common_move_trash),
            destructive = true,
            onConfirm = {
                confirmTrash = false
                viewModel.trashSelected()
            },
            onDismiss = { confirmTrash = false }
        )
    }

    renameTarget?.let { file ->
        RenameDialog(
            title = stringResource(Res.string.common_rename_file),
            initialValue = file.name,
            confirmLabel = stringResource(Res.string.common_rename),
            onConfirm = viewModel::confirmRename,
            onDismiss = viewModel::dismissRename
        )
    }

    AppBackHandler(enabled = state.selectionMode) { viewModel.clearSelection() }

    val mediaListState = rememberSaveable(state.tab, saver = LazyListState.Saver) {
        LazyListState()
    }
    val allSelected by viewModel.allSelected.collectAsStateWithLifecycle()
    val previewSequence by viewModel.previewSequence.collectAsStateWithLifecycle()
    val mediaGridState = rememberSaveable(state.tab, saver = LazyGridState.Saver) {
        LazyGridState()
    }
    val scrolled = if (state.viewMode == ViewMode.LIST) mediaListState else mediaGridState
    if (state.viewMode == ViewMode.LIST) {
        mediaListState.rememberPosition(viewModel.listPosition, media.itemCount)
    } else {
        mediaGridState.rememberPosition(viewModel.listPosition, media.itemCount)
    }
    val lifted by rememberToolbarLift(scrolled)

    Scaffold(
        snackbarHost = {
            BottomBarSnackbarHost(
                hostState = snackbarHostState,
                applyInset = !state.selectionMode
            )
        },
        topBar = {
            if (state.selectionMode) {
                TopAppBar(
                    colors = liftedTopAppBarColors(lifted),
                    title = {
                        Text(
                            text = stringResource(
                                Res.string.common_selection_count,
                                state.selection.size
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(Res.string.common_clear_selection)
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
                                    stringResource(Res.string.common_deselect_all)
                                } else {
                                    stringResource(Res.string.common_select_all)
                                }
                            )
                        }
                        Box {
                            IconButton(onClick = { showSelectionOverflow = true }) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = stringResource(Res.string.common_actions)
                                )
                            }
                            val storage = buildList {
                                if (state.capabilities.canDownload) {
                                    add(
                                        MenuAction(
                                            label = stringResource(Res.string.common_download),
                                            icon = Icons.Filled.Download
                                        ) { viewModel.downloadSelected() }
                                    )
                                }
                                if (state.capabilities.canUpload) {
                                    add(
                                        MenuAction(
                                            label = stringResource(Res.string.common_upload),
                                            icon = Icons.Filled.Upload
                                        ) { viewModel.uploadSelected() }
                                    )
                                }
                            }
                            val organize = buildList {
                                add(
                                    MenuAction(
                                        label = stringResource(
                                            if (state.allSelectedFavorite) {
                                                Res.string.preview_remove_favorites
                                            } else {
                                                Res.string.common_add_favorites
                                            }
                                        ),
                                        icon = if (state.allSelectedFavorite) {
                                            Icons.Filled.Star
                                        } else {
                                            Icons.Filled.StarOutline
                                        }
                                    ) { viewModel.favoriteSelected(!state.allSelectedFavorite) }
                                )
                                add(
                                    MenuAction(
                                        label = stringResource(
                                            if (state.allSelectedAvailableOffline) {
                                                Res.string.common_remove_from_offline
                                            } else {
                                                Res.string.common_make_available_offline
                                            }
                                        ),
                                        icon = Icons.Filled.OfflinePin
                                    ) { viewModel.setSelectedAvailableOffline(!state.allSelectedAvailableOffline) }
                                )
                                add(
                                    MenuAction(
                                        label = stringResource(Res.string.files_hide),
                                        icon = Icons.Filled.VisibilityOff
                                    ) { viewModel.hideSelected(true) }
                                )
                                add(
                                    MenuAction(
                                        label = stringResource(Res.string.files_archive),
                                        icon = Icons.Filled.Archive
                                    ) { viewModel.archiveSelected(true) }
                                )
                            }
                            val actions = buildList {
                                if (state.selection.size == 1) {
                                    add(
                                        MenuAction(
                                            label = stringResource(Res.string.common_rename),
                                            icon = Icons.Filled.DriveFileRenameOutline
                                        ) { viewModel.requestRenameSelected() }
                                    )
                                }
                                add(
                                    MenuAction(
                                        label = stringResource(Res.string.files_move_to),
                                        icon = Icons.AutoMirrored.Filled.DriveFileMove
                                    ) { showMovePicker = true }
                                )
                                if (storage.isNotEmpty()) {
                                    add(
                                        MenuAction(
                                            label = stringResource(Res.string.common_storage_actions),
                                            icon = Icons.Filled.Storage,
                                            children = storage
                                        )
                                    )
                                }
                                add(
                                    MenuAction(
                                        label = stringResource(Res.string.common_organize),
                                        icon = Icons.Filled.Tune,
                                        children = organize
                                    )
                                )
                                add(
                                    MenuAction(
                                        label = stringResource(Res.string.common_move_trash),
                                        icon = Icons.Filled.DeleteOutline
                                    ) { confirmTrash = true }
                                )
                            }
                            ActionMenu(
                                expanded = showSelectionOverflow,
                                onDismissRequest = { showSelectionOverflow = false },
                                actions = actions
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    colors = liftedTopAppBarColors(lifted),
                    title = { Text(state.albumTitle ?: stringResource(Res.string.gallery_title)) },
                    navigationIcon = {
                        onBack?.let {
                            IconButton(onClick = it) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(Res.string.common_back)
                                )
                            }
                        }
                    },
                    actions = {
                        RefreshAction(refreshing = refreshing, onRefresh = viewModel::refresh)
                    }
                )
            }
        }
    ) { padding ->
        var tabsVisible by remember { mutableStateOf(true) }
        val mediaScrolls by remember(scrolled) {
            derivedStateOf { scrolled.canScrollForward || scrolled.canScrollBackward }
        }
        LaunchedEffect(state.tab) { tabsVisible = true }
        LaunchedEffect(mediaScrolls) { if (!mediaScrolls) tabsVisible = true }
        val tabScrollConnection = remember {
            object : NestedScrollConnection {
                @Suppress("SameReturnValue")
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    if (!mediaScrolls) return Offset.Zero
                    when {
                        available.y < -TAB_SCROLL_THRESHOLD -> tabsVisible = false
                        available.y > TAB_SCROLL_THRESHOLD -> tabsVisible = true
                    }
                    return Offset.Zero
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .nestedScroll(tabScrollConnection)
        ) {
            if (!state.isAlbumView) {
                AnimatedVisibility(
                    visible = tabsVisible,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    GalleryTabSelector(
                        selected = state.tab,
                        onSelect = viewModel::setTab
                    )
                }
            }

            if (state.tab == GalleryTab.ALBUMS && !state.isAlbumView) {
                if (!state.loaded) {
                    LoadingState()
                    return@Column
                }
                AlbumGrid(
                    albums = state.albums,
                    columns = state.albumGridSize,
                    bottomPadding = padding.calculateBottomPadding() + LocalBottomBarInset.current,
                    onOpenAlbum = onOpenAlbum,
                    onZoomIn = viewModel::zoomAlbumsIn,
                    onZoomOut = viewModel::zoomAlbumsOut
                )
                return@Column
            }

            val mediaIdAt: (Int) -> String? = { index ->
                (media.peek(index) as? GalleryListItem.Media)?.file?.id
            }
            val listDragSelect = rememberDragSelect(
                listState = mediaListState,
                onStart = viewModel::startRangeSelection,
                onRange = { range ->
                    viewModel.extendRangeSelection(range.mapNotNull(mediaIdAt))
                },
                onEnd = viewModel::endRangeSelection
            )
            val gridDragSelect = rememberDragSelect(
                gridState = mediaGridState,
                onStart = viewModel::startRangeSelection,
                onRange = { range ->
                    viewModel.extendRangeSelection(range.mapNotNull(mediaIdAt))
                },
                onEnd = viewModel::endRangeSelection
            )
            RefreshableContent(
                isRefreshing = refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    media.isInitialLoad || !state.loaded -> LoadingState()

                    media.itemCount == 0 -> EmptyState(
                        icon = Icons.Outlined.Photo,
                        title = stringResource(Res.string.gallery_no_media),
                        description = stringResource(Res.string.gallery_photos_videos_appear_here)
                    )

                    state.viewMode == ViewMode.LIST -> LazyColumn(
                        state = mediaListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .pinchZoom(
                                onZoomIn = viewModel::zoomIn,
                                onZoomOut = viewModel::zoomOut
                            )
                            .then(listDragSelect),
                        contentPadding = PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            top = 8.dp,
                            bottom = 8.dp + padding.calculateBottomPadding() + LocalBottomBarInset.current
                        )
                    ) {
                        items(
                            count = media.itemCount,
                            key = media.itemKey { it.key }
                        ) { index ->
                            when (val item = media[index]) {
                                is GalleryListItem.DayHeader -> DayHeaderRow(
                                    dayStartMillis = item.dayStartMillis,
                                    modifier = Modifier.animateItem()
                                )

                                is GalleryListItem.Media -> {
                                    val file = item.file
                                    FileListItem(
                                        file = file,
                                        selected = file.id in state.selection,
                                        selectionMode = state.selectionMode,
                                        onClick = {
                                            if (state.selectionMode) {
                                                viewModel.toggleSelection(file.id)
                                            } else {
                                                onOpenFile(file.id, previewSequence)
                                            }
                                        },
                                        onLongClick = { viewModel.toggleSelection(file.id) },
                                        modifier = Modifier.animateItem()
                                    )
                                }

                                null -> Unit
                            }
                        }
                    }

                    else -> LazyVerticalGrid(
                        state = mediaGridState,
                        columns = GridCells.Fixed(state.gridSize),
                        modifier = Modifier
                            .fillMaxSize()
                            .pinchZoom(
                                onZoomIn = viewModel::zoomIn,
                                onZoomOut = viewModel::zoomOut
                            )
                            .then(gridDragSelect),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 12.dp,
                            bottom = 12.dp + padding.calculateBottomPadding() + LocalBottomBarInset.current
                        ),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(
                            count = media.itemCount,
                            key = media.itemKey { it.key },
                            span = { index ->
                                if (media.peek(index) is GalleryListItem.DayHeader) {
                                    GridItemSpan(maxLineSpan)
                                } else {
                                    GridItemSpan(1)
                                }
                            }
                        ) { index ->
                            when (val item = media[index]) {
                                is GalleryListItem.DayHeader -> DayHeaderRow(
                                    dayStartMillis = item.dayStartMillis,
                                    modifier = Modifier.animateItem()
                                )

                                is GalleryListItem.Media -> {
                                    val file = item.file
                                    FileGridItem(
                                        file = file,
                                        selected = file.id in state.selection,
                                        compact = true,
                                        onClick = {
                                            if (state.selectionMode) {
                                                viewModel.toggleSelection(file.id)
                                            } else {
                                                onOpenFile(file.id, previewSequence)
                                            }
                                        },
                                        onLongClick = { viewModel.toggleSelection(file.id) },
                                        modifier = Modifier.animateItem()
                                    )
                                }

                                null -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val TAB_SCROLL_THRESHOLD = 6f
