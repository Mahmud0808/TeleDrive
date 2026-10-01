package com.drdisagree.teledrive.presentation.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drdisagree.teledrive.domain.model.FileCategory
import com.drdisagree.teledrive.presentation.common.AppBackHandler
import com.drdisagree.teledrive.presentation.components.ActionMenu
import com.drdisagree.teledrive.presentation.components.EmptyState
import com.drdisagree.teledrive.presentation.components.FileListItem
import com.drdisagree.teledrive.presentation.components.FolderRow
import com.drdisagree.teledrive.presentation.components.LoadingState
import com.drdisagree.teledrive.presentation.components.MenuAction
import com.drdisagree.teledrive.presentation.components.label
import com.drdisagree.teledrive.presentation.components.liftedTopAppBarColors
import com.drdisagree.teledrive.presentation.components.rememberToolbarLift
import com.drdisagree.teledrive.presentation.preview.PreviewSequence
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.app_backed_up
import com.drdisagree.teledrive.resources.app_no_results
import com.drdisagree.teledrive.resources.app_not_backed_up
import com.drdisagree.teledrive.resources.app_nothing_matches_search
import com.drdisagree.teledrive.resources.app_search_drive
import com.drdisagree.teledrive.resources.app_search_files
import com.drdisagree.teledrive.resources.app_search_name_filter_type
import com.drdisagree.teledrive.resources.common_actions
import com.drdisagree.teledrive.resources.common_add_favorites
import com.drdisagree.teledrive.resources.common_back
import com.drdisagree.teledrive.resources.common_clear
import com.drdisagree.teledrive.resources.common_download
import com.drdisagree.teledrive.resources.common_make_available_offline
import com.drdisagree.teledrive.resources.common_move_trash
import com.drdisagree.teledrive.resources.common_select_all
import com.drdisagree.teledrive.resources.common_remove_from_offline
import com.drdisagree.teledrive.resources.search_open_folder
import com.drdisagree.teledrive.resources.search_section_files
import com.drdisagree.teledrive.resources.search_section_folders
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenFile: (String, PreviewSequence) -> Unit,
    onOpenFolder: (String, String?) -> Unit,
    viewModel: SearchViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val lifted by rememberToolbarLift(listState)
    var showSelectionMenu by remember { mutableStateOf(false) }

    AppBackHandler(enabled = state.selectionMode) { viewModel.clearSelection() }

    Scaffold(
        topBar = {
            if (state.selectionMode) {
                SearchSelectionBar(
                    state = state,
                    lifted = lifted,
                    menuExpanded = showSelectionMenu,
                    onMenuExpandedChange = { showSelectionMenu = it },
                    viewModel = viewModel,
                    onOpenFolder = onOpenFolder
                )
            } else {
                TopAppBar(
                    colors = liftedTopAppBarColors(lifted),
                    title = {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = viewModel::setQuery,
                            placeholder = { Text(stringResource(Res.string.app_search_files)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                if (state.query.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setQuery("") }) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = stringResource(Res.string.common_clear)
                                        )
                                    }
                                }
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.common_back)
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.filters.backedUpOnly,
                    onClick = { viewModel.setBackedUpOnly(!state.filters.backedUpOnly) },
                    label = { Text(stringResource(Res.string.app_backed_up)) }
                )
                FilterChip(
                    selected = state.filters.notBackedUpOnly,
                    onClick = { viewModel.setNotBackedUpOnly(!state.filters.notBackedUpOnly) },
                    label = { Text(stringResource(Res.string.app_not_backed_up)) }
                )
                FileCategory.entries.filter { it != FileCategory.OTHER }.forEach { category ->
                    FilterChip(
                        selected = state.filters.category == category,
                        onClick = {
                            viewModel.setCategory(
                                if (state.filters.category == category) null else category
                            )
                        },
                        label = { Text(category.label()) }
                    )
                }
            }

            when {
                state.searching -> LoadingState()
                !state.searched -> EmptyState(
                    icon = Icons.Outlined.Search,
                    title = stringResource(Res.string.app_search_drive),
                    description = stringResource(Res.string.app_search_name_filter_type)
                )

                state.results.isEmpty() && state.folders.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.Search,
                    title = stringResource(Res.string.app_no_results),
                    description = stringResource(Res.string.app_nothing_matches_search)
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 12.dp,
                        bottom = 12.dp + padding.calculateBottomPadding()
                    )
                ) {
                    if (state.folders.isNotEmpty()) {
                        item(key = "folders_header") {
                            SearchSectionHeader(
                                title = stringResource(Res.string.search_section_folders),
                                modifier = Modifier.animateItem()
                            )
                        }
                        items(state.folders, key = { "folder_${it.id}" }) { folder ->
                            FolderRow(
                                folder = folder,
                                onClick = { onOpenFolder(folder.id, null) },
                                modifier = Modifier.animateItem()
                            )
                        }
                        if (state.results.isNotEmpty()) {
                            item(key = "files_header") {
                                SearchSectionHeader(
                                    title = stringResource(Res.string.search_section_files),
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                    items(state.results, key = { it.id }) { file ->
                        FileListItem(
                            file = file,
                            selected = file.id in state.selection,
                            selectionMode = state.selectionMode,
                            onClick = {
                                if (state.selectionMode) {
                                    viewModel.toggleSelection(file.id)
                                } else {
                                    onOpenFile(
                                        file.id,
                                        PreviewSequence(
                                            nameQuery = state.query.takeIf { it.isNotBlank() },
                                            categories = state.filters.category
                                                ?.let { category -> listOf(category) }
                                                .orEmpty(),
                                            sortField = state.filters.sortField,
                                            sortDirection = state.filters.sortDirection
                                        )
                                    )
                                }
                            },
                            onLongClick = { viewModel.toggleSelection(file.id) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSelectionBar(
    state: SearchUiState,
    lifted: Boolean,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    viewModel: SearchViewModel,
    onOpenFolder: (String, String?) -> Unit
) {
    val selectedFile = state.selectedFiles.singleOrNull()
    val folderId = selectedFile?.folderId
    val actions = buildList {
        if (folderId != null && selectedFile?.isArchived != true) {
            add(
                MenuAction(
                    label = stringResource(Res.string.search_open_folder),
                    icon = Icons.Filled.FolderOpen
                ) {
                    viewModel.clearSelection()
                    onOpenFolder(folderId, selectedFile?.id)
                }
            )
        }
        if (state.capabilities.canDownload) {
            add(
                MenuAction(
                    label = stringResource(Res.string.common_download),
                    icon = Icons.Filled.Download
                ) { viewModel.downloadSelected() }
            )
        }
        add(
            MenuAction(
                label = stringResource(Res.string.common_add_favorites),
                icon = Icons.Filled.StarOutline
            ) { viewModel.favoriteSelected() }
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
                label = stringResource(Res.string.common_move_trash),
                icon = Icons.Filled.DeleteOutline
            ) { viewModel.trashSelected() }
        )
    }

    TopAppBar(
        colors = liftedTopAppBarColors(lifted),
        title = { Text(state.selection.size.toString()) },
        navigationIcon = {
            IconButton(onClick = viewModel::clearSelection) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.common_clear)
                )
            }
        },
        actions = {
            IconButton(onClick = viewModel::selectAll) {
                Icon(
                    Icons.Filled.SelectAll,
                    contentDescription = stringResource(Res.string.common_select_all)
                )
            }
            Box {
                IconButton(onClick = { onMenuExpandedChange(true) }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(Res.string.common_actions)
                    )
                }
                ActionMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { onMenuExpandedChange(false) },
                    actions = actions
                )
            }
        }
    )
}

@Composable
private fun SearchSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 6.dp)
    )
}
