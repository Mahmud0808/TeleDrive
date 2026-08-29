package com.drdisagree.teledrive.presentation.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drdisagree.teledrive.resources.Res
import com.drdisagree.teledrive.resources.app_backed_up
import com.drdisagree.teledrive.resources.app_no_results
import com.drdisagree.teledrive.resources.app_not_backed_up
import com.drdisagree.teledrive.resources.app_nothing_matches_search
import com.drdisagree.teledrive.resources.app_search_drive
import com.drdisagree.teledrive.resources.app_search_files
import com.drdisagree.teledrive.resources.app_search_name_filter_type
import com.drdisagree.teledrive.resources.common_back
import com.drdisagree.teledrive.resources.common_clear
import com.drdisagree.teledrive.resources.search_section_files
import com.drdisagree.teledrive.resources.search_section_folders
import com.drdisagree.teledrive.domain.model.FileCategory
import com.drdisagree.teledrive.presentation.components.EmptyState
import com.drdisagree.teledrive.presentation.components.FileListItem
import com.drdisagree.teledrive.presentation.components.FolderRow
import com.drdisagree.teledrive.presentation.components.LoadingState
import com.drdisagree.teledrive.presentation.components.label
import com.drdisagree.teledrive.presentation.components.liftedTopAppBarColors
import com.drdisagree.teledrive.presentation.components.rememberToolbarLift
import com.drdisagree.teledrive.presentation.preview.PreviewSequence

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenFile: (String, PreviewSequence) -> Unit,
    onOpenFolder: (String) -> Unit,
    viewModel: SearchViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val lifted by rememberToolbarLift(listState)

    Scaffold(
        topBar = {
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
                                onClick = { onOpenFolder(folder.id) },
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
                            selected = false,
                            selectionMode = false,
                            onClick = {
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
                            },
                            onLongClick = { },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
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
