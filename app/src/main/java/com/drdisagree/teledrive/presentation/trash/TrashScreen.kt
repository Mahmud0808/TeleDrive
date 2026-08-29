package com.drdisagree.teledrive.presentation.trash

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.common_back
import com.drdisagree.teledrive.resources.common_clear_selection
import com.drdisagree.teledrive.resources.common_delete_forever
import com.drdisagree.teledrive.resources.common_deselect_all
import com.drdisagree.teledrive.resources.common_restore
import com.drdisagree.teledrive.resources.common_select_all
import com.drdisagree.teledrive.resources.common_selection_count
import com.drdisagree.teledrive.resources.trash
import com.drdisagree.teledrive.resources.trash_delete_permanently
import com.drdisagree.teledrive.resources.trash_delete_selected_message
import com.drdisagree.teledrive.resources.trash_empty
import com.drdisagree.teledrive.resources.trash_empty_trash_action
import com.drdisagree.teledrive.resources.trash_empty_trash_title
import com.drdisagree.teledrive.resources.trash_items_permanently_deleted_device
import com.drdisagree.teledrive.resources.trash_restores_with_folder
import com.drdisagree.teledrive.resources.trash_trashed_at
import com.drdisagree.teledrive.domain.model.TrashItem
import com.drdisagree.teledrive.presentation.common.CollectSnackbarMessages
import com.drdisagree.teledrive.presentation.common.Formatters
import com.drdisagree.teledrive.presentation.common.add
import com.drdisagree.teledrive.presentation.components.BlockingProgressDialog
import com.drdisagree.teledrive.presentation.components.ConfirmDialog
import com.drdisagree.teledrive.presentation.components.EmptyState
import com.drdisagree.teledrive.presentation.components.FileThumbnail
import com.drdisagree.teledrive.presentation.components.liftedTopAppBarColors
import com.drdisagree.teledrive.presentation.components.rememberDragSelect
import com.drdisagree.teledrive.presentation.components.rememberToolbarLift

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    viewModel: TrashViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }

    CollectSnackbarMessages(viewModel.messages, snackbarHostState)

    state.working?.let { message ->
        BlockingProgressDialog(message = message)
    }

    BackHandler(enabled = state.selectionMode) { viewModel.clearSelection() }

    val listState = rememberLazyListState()
    val lifted by rememberToolbarLift(listState)
    val allSelected = state.rows.any { it.selectable } &&
            state.selection.size == state.rows.count { it.selectable }
    val dragSelect = rememberDragSelect(
        listState = listState,
        onStart = viewModel::startRangeSelection,
        onRange = { range ->
            viewModel.extendRangeSelection(
                range.mapNotNull { index ->
                    state.rows.getOrNull(index)?.takeIf { it.selectable }?.item?.id
                }
            )
        },
        onEnd = viewModel::endRangeSelection
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.selectionMode) {
                TopAppBar(
                    colors = liftedTopAppBarColors(lifted),
                    title = {
                        Text(stringResource(Res.string.common_selection_count, state.selection.size))
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
                        IconButton(onClick = viewModel::restoreSelected) {
                            Icon(
                                Icons.Filled.RestoreFromTrash,
                                contentDescription = stringResource(Res.string.common_restore)
                            )
                        }
                        IconButton(onClick = { confirmDeleteSelected = true }) {
                            Icon(
                                Icons.Filled.DeleteForever,
                                contentDescription = stringResource(Res.string.common_delete_forever)
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    colors = liftedTopAppBarColors(lifted),
                    title = { Text(stringResource(Res.string.trash)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.common_back)
                            )
                        }
                    },
                    actions = {
                        if (state.items.isNotEmpty()) {
                            IconButton(onClick = { confirmEmpty = true }) {
                                Icon(
                                    Icons.Filled.DeleteForever,
                                    contentDescription = stringResource(Res.string.trash_empty_trash_action)
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        if (state.items.isEmpty() && !state.loading) {
            EmptyState(
                icon = Icons.Outlined.Delete,
                title = stringResource(Res.string.trash_empty),
                description = if (state.autoClearDays > 0) {
                    "Items are deleted permanently after ${state.autoClearDays} days."
                } else {
                    "Deleted items stay here until you remove them."
                },
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(dragSelect),
            contentPadding = padding.add(horizontal = 12.dp, top = 12.dp, bottom = 12.dp)
        ) {
            items(
                state.rows,
                key = { it.key }
            ) { row ->
                val item = row.item
                val selected = row.selectable && item.id in state.selection
                Row(
                    modifier = Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .padding(start = (row.depth * 20).dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(
                            if (selected) MaterialTheme.colorScheme.secondaryContainer
                            else Color.Transparent
                        )
                        .combinedClickable(
                            onClick = {
                                when {
                                    state.selectionMode && row.selectable ->
                                        viewModel.toggleSelection(item.id)

                                    row.expandable -> viewModel.toggleExpanded(item.id)
                                }
                            },
                            onLongClick = {
                                if (row.selectable) viewModel.toggleSelection(item.id)
                            }
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (item) {
                        is TrashItem.File -> FileThumbnail(
                            file = item.file,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(MaterialTheme.shapes.medium)
                        )

                        is TrashItem.Folder -> Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (row.depth == 0) {
                                stringResource(
                                    Res.string.trash_trashed_at,
                                    Formatters.dateTime(item.trashedAt)
                                )
                            } else {
                                stringResource(Res.string.trash_restores_with_folder)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (row.expandable) {
                        IconButton(onClick = { viewModel.toggleExpanded(item.id) }) {
                            Icon(
                                imageVector = if (row.expanded) {
                                    Icons.Filled.ExpandLess
                                } else {
                                    Icons.Filled.ExpandMore
                                },
                                contentDescription = if (row.expanded) {
                                    "Collapse ${item.name}"
                                } else {
                                    "Show contents of ${item.name}"
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmEmpty) {
        ConfirmDialog(
            title = stringResource(Res.string.trash_empty_trash_title),
            message = stringResource(Res.string.trash_items_permanently_deleted_device),
            confirmLabel = stringResource(Res.string.trash_empty_trash_action),
            destructive = true,
            onConfirm = {
                confirmEmpty = false
                viewModel.emptyTrash()
            },
            onDismiss = { confirmEmpty = false }
        )
    }
    if (confirmDeleteSelected) {
        ConfirmDialog(
            title = stringResource(Res.string.trash_delete_permanently),
            message = stringResource(
                Res.string.trash_delete_selected_message,
                state.selection.size
            ),
            confirmLabel = stringResource(Res.string.common_delete_forever),
            destructive = true,
            onConfirm = {
                confirmDeleteSelected = false
                viewModel.deleteSelectedForever()
            },
            onDismiss = { confirmDeleteSelected = false }
        )
    }
}
