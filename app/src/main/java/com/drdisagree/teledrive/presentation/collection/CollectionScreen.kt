package com.drdisagree.teledrive.presentation.collection

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.drdisagree.teledrive.R
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
    viewModel: CollectionViewModel = hiltViewModel()
) {
    val files = viewModel.files.collectAsLazyPagingItems()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val selectionMode = selection.isNotEmpty()
    val allSelected by viewModel.allSelected.collectAsStateWithLifecycle()
    var confirmTrash by remember { mutableStateOf(false) }

    BackHandler(enabled = selectionMode) { viewModel.clearSelection() }

    if (confirmTrash) {
        ConfirmDialog(
            title = stringResource(R.string.common_confirm_trash_count_title, selection.size),
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

    val listState = rememberLazyListState()
    val lifted by rememberToolbarLift(listState)

    Scaffold(
        topBar = {
            TopAppBar(
                colors = liftedTopAppBarColors(lifted),
                title = {
                    Text(
                        text = if (selectionMode) {
                            stringResource(R.string.common_selection_count, selection.size)
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
                            contentDescription = if (selectionMode) stringResource(R.string.common_clear_selection) else stringResource(
                                R.string.common_back
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
                                    stringResource(R.string.common_deselect_all)
                                } else {
                                    stringResource(R.string.common_select_all)
                                }
                            )
                        }
                        IconButton(onClick = viewModel::removeFromCollection) {
                            Icon(
                                Icons.Filled.RemoveCircleOutline,
                                contentDescription = stringResource(
                                    R.string.collection_remove_from,
                                    stringResource(viewModel.type.titleRes)
                                )
                            )
                        }
                        IconButton(onClick = { confirmTrash = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.common_move_trash)
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
        if (files.itemCount == 0) {
            EmptyState(
                icon = viewModel.type.icon,
                title = stringResource(
                    R.string.collection_empty_title,
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
                viewModel.extendRangeSelection(
                    range.mapNotNull { index -> files.peek(index)?.id }
                )
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
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}
