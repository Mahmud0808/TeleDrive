package com.drdisagree.teledrive.presentation.collection

import com.drdisagree.teledrive.presentation.components.FolderRow
import androidx.compose.foundation.lazy.items
import com.drdisagree.teledrive.presentation.common.AppBackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.collection_empty_title
import com.drdisagree.teledrive.resources.collection_remove_from
import com.drdisagree.teledrive.resources.common_back
import com.drdisagree.teledrive.resources.common_clear_selection
import com.drdisagree.teledrive.resources.common_confirm_trash_count_title
import com.drdisagree.teledrive.resources.common_deselect_all
import com.drdisagree.teledrive.resources.common_move_trash
import com.drdisagree.teledrive.resources.common_restore_trash_emptied
import com.drdisagree.teledrive.resources.common_select_all
import com.drdisagree.teledrive.resources.common_selection_count
import com.drdisagree.teledrive.presentation.common.isInitialLoad
import com.drdisagree.teledrive.presentation.components.ConfirmDialog
import com.drdisagree.teledrive.presentation.components.EmptyState
import com.drdisagree.teledrive.presentation.components.FileListItem
import com.drdisagree.teledrive.presentation.components.LoadingState
import com.drdisagree.teledrive.presentation.components.liftedTopAppBarColors
import com.drdisagree.teledrive.presentation.components.rememberDragSelect
import com.drdisagree.teledrive.presentation.components.rememberToolbarLift
import com.drdisagree.teledrive.presentation.preview.PreviewSequence

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    onBack: () -> Unit,
    onOpenFile: (String, PreviewSequence) -> Unit,
    onOpenFolder: (String) -> Unit,
    viewModel: CollectionViewModel = koinViewModel()
) {
    val files = viewModel.files.collectAsLazyPagingItems()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val folderSelection by viewModel.folderSelection.collectAsStateWithLifecycle()
    val selectionMode = selection.isNotEmpty() || folderSelection.isNotEmpty()
    val selectionCount = selection.size + folderSelection.size
    val allSelected by viewModel.allSelected.collectAsStateWithLifecycle()
    var confirmTrash by remember { mutableStateOf(false) }

    AppBackHandler(enabled = selectionMode) { viewModel.clearSelection() }

    if (confirmTrash) {
        ConfirmDialog(
            title = stringResource(Res.string.common_confirm_trash_count_title, selectionCount),
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

    val listState = rememberLazyListState()
    val lifted by rememberToolbarLift(listState)

    Scaffold(
        topBar = {
            TopAppBar(
                colors = liftedTopAppBarColors(lifted),
                title = {
                    Text(
                        text = if (selectionMode) {
                            stringResource(Res.string.common_selection_count, selectionCount)
                        } else {
                            stringResource(viewModel.type.titleRes)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { if (selectionMode) viewModel.clearSelection() else onBack() }
                    ) {
                        Icon(
                            imageVector = if (selectionMode) {
                                Icons.Filled.Close
                            } else {
                                Icons.AutoMirrored.Filled.ArrowBack
                            },
                            contentDescription = if (selectionMode) stringResource(Res.string.common_clear_selection) else stringResource(
                                Res.string.common_back
                            )
                        )
                    }
                },
                actions = {
                    if (selectionMode) {
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
                        IconButton(onClick = viewModel::removeFromCollection) {
                            Icon(
                                Icons.Filled.RemoveCircleOutline,
                                contentDescription = stringResource(
                                    Res.string.collection_remove_from,
                                    stringResource(viewModel.type.titleRes)
                                )
                            )
                        }
                        IconButton(onClick = { confirmTrash = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(Res.string.common_move_trash)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (files.isInitialLoad) {
            LoadingState()
            return@Scaffold
        }
        if (files.itemCount == 0 && folders.isEmpty()) {
            EmptyState(
                icon = viewModel.type.icon,
                title = stringResource(
                    Res.string.collection_empty_title,
                    stringResource(viewModel.type.titleRes).lowercase()
                ),
                description = stringResource(viewModel.type.emptyMessageRes),
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }
        val dragSelect = rememberDragSelect(
            listState = listState,
            onStart = viewModel::startRangeSelection,
            onRange = { range ->
                val folderIds = mutableListOf<String>()
                val fileIds = mutableListOf<String>()
                for (index in range) {
                    if (index < folders.size) {
                        folderIds += folders[index].id
                    } else {
                        val fileIndex = index - folders.size
                        if (fileIndex < files.itemCount) {
                            files.peek(fileIndex)?.let { fileIds += it.id }
                        }
                    }
                }
                viewModel.extendRangeSelection(fileIds, folderIds)
            },
            onEnd = viewModel::endRangeSelection
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .then(dragSelect),
            contentPadding = PaddingValues(
                start = 8.dp,
                end = 8.dp,
                top = 8.dp,
                bottom = 8.dp + padding.calculateBottomPadding()
            )
        ) {
            items(folders, key = { "folder-${it.id}" }) { folder ->
                FolderRow(
                    folder = folder,
                    selected = folder.id in folderSelection,
                    showFavorite = viewModel.type != CollectionType.FAVORITES,
                    onClick = {
                        if (selectionMode) viewModel.toggleFolderSelection(folder.id)
                        else onOpenFolder(folder.id)
                    },
                    onLongClick = { viewModel.toggleFolderSelection(folder.id) },
                    modifier = Modifier.animateItem()
                )
            }
            items(
                count = files.itemCount,
                key = files.itemKey { it.id }
            ) { index ->
                val file = files[index] ?: return@items
                FileListItem(
                    file = file,
                    selected = file.id in selection,
                    selectionMode = selectionMode,
                    onClick = {
                        if (selectionMode) viewModel.toggleSelection(file.id)
                        else onOpenFile(file.id, viewModel.previewSequence)
                    },
                    onLongClick = { viewModel.toggleSelection(file.id) },
                    showFavorite = viewModel.type != CollectionType.FAVORITES,
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}
