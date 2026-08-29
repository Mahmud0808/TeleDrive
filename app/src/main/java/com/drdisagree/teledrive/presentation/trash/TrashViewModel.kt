package com.drdisagree.teledrive.presentation.trash

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drdisagree.teledrive.R
import com.drdisagree.teledrive.core.common.AppResult
import com.drdisagree.teledrive.domain.model.TrashItem
import com.drdisagree.teledrive.domain.repository.SettingsRepository
import com.drdisagree.teledrive.domain.repository.TrashRepository
import com.drdisagree.teledrive.presentation.common.toUserMessage
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TrashUiState(
    val items: List<TrashItem> = emptyList(),
    val rows: List<TrashRow> = emptyList(),
    val selection: Set<String> = emptySet(),
    val autoClearDays: Int = 30,
    val working: String? = null,
    val loading: Boolean = true
) {
    val selectionMode: Boolean get() = selection.isNotEmpty()
}

class TrashViewModel(
    private val context: Context,
    private val trashRepository: TrashRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val selection = MutableStateFlow<Set<String>>(emptySet())
    private val working = MutableStateFlow<String?>(null)
    private val expanded = MutableStateFlow<Set<String>>(emptySet())
    private val cache = MutableStateFlow(TrashTreeCache())
    private var rangeBase: Set<String>? = null

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private val trashTree = trashRepository.observeTrash().map { items ->
        val folderIds = items.filterIsInstance<TrashItem.Folder>().map { it.folder.id }
        items to trashRepository.trashedChildCounts(folderIds)
    }

    val uiState: StateFlow<TrashUiState> = combine(
        trashTree,
        combine(selection, working) { selected, busy -> selected to busy },
        settingsRepository.preferences,
        expanded,
        cache
    ) { tree, selectionAndWork, prefs, expandedIds, loaded ->
        val (selected, busy) = selectionAndWork
        val (items, rootCounts) = tree
        val childMap = loaded.children
        val childCounts = rootCounts + loaded.counts
        TrashUiState(
            items = items,
            rows = flatten(items, expandedIds, childMap, childCounts, depth = 0),
            selection = selected,
            working = busy,
            autoClearDays = prefs.trashAutoClearDays,
            loading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrashUiState())

    fun toggleExpanded(folderId: String) {
        val opening = folderId !in expanded.value
        if (opening) {
            expanded.update { it + folderId }
            if (folderId !in cache.value.children) {
                viewModelScope.launch {
                    val loaded = trashRepository.trashedChildren(folderId)
                    val nestedFolderIds = loaded
                        .filterIsInstance<TrashItem.Folder>()
                        .map { it.folder.id }
                    val nestedCounts = trashRepository.trashedChildCounts(nestedFolderIds)
                    cache.update { current ->
                        current.copy(
                            children = current.children + (folderId to loaded),
                            counts = current.counts + nestedCounts
                        )
                    }
                }
            }
        } else {
            val closing = descendantFolderIds(folderId) + folderId
            expanded.update { it - closing }
        }
    }

    /** Collapsing a folder collapses whatever was open inside it. */
    private fun descendantFolderIds(folderId: String): Set<String> {
        val result = mutableSetOf<String>()
        var frontier = listOf(folderId)
        var guard = 0
        while (frontier.isNotEmpty() && guard++ < MAX_TREE_DEPTH) {
            frontier = frontier.flatMap { parent ->
                cache.value.children[parent]
                    .orEmpty()
                    .filterIsInstance<TrashItem.Folder>()
                    .map { it.folder.id }
            }
            result += frontier
        }
        return result
    }

    private fun flatten(
        items: List<TrashItem>,
        expandedIds: Set<String>,
        childMap: Map<String, List<TrashItem>>,
        childCounts: Map<String, Int>,
        depth: Int
    ): List<TrashRow> = items.flatMap { item ->
        val loaded = childMap[item.id]
        val hasChildren = when {
            loaded != null -> loaded.isNotEmpty()
            item is TrashItem.Folder -> (childCounts[item.id] ?: 0) > 0
            else -> false
        }
        val isOpen = hasChildren && item.id in expandedIds
        val row = TrashRow(
            item = item,
            depth = depth,
            expandable = hasChildren,
            expanded = isOpen
        )
        if (isOpen) {
            listOf(row) + flatten(
                loaded.orEmpty(),
                expandedIds,
                childMap,
                childCounts,
                depth + 1
            )
        } else {
            listOf(row)
        }
    }

    fun toggleSelection(id: String) = selection.update {
        if (id in it) it - id else it + id
    }

    fun clearSelection() = selection.update { emptySet() }

    /** Selects every row currently in the tree, including expanded children. */
    fun selectAll() = selection.update {
        uiState.value.rows.filter { row -> row.selectable }.map { row -> row.item.id }.toSet()
    }

    fun startRangeSelection() {
        rangeBase = selection.value
    }

    fun extendRangeSelection(ids: List<String>) {
        val base = rangeBase ?: return
        selection.update { base + ids }
    }

    fun endRangeSelection() {
        rangeBase = null
    }

    fun restoreSelected() {
        val (fileIds, folderIds) = partitionSelection()
        val total = fileIds.size + folderIds.size
        clearSelection()
        viewModelScope.launch {
            working.value = "Restoring $total item${if (total == 1) "" else "s"}…"
            var failed = 0
            withContext(NonCancellable) {
                if (fileIds.isNotEmpty() &&
                    trashRepository.restoreFiles(fileIds) is AppResult.Failure
                ) {
                    failed += fileIds.size
                }
                folderIds.forEach { folderId ->
                    if (trashRepository.restoreFolder(folderId) is AppResult.Failure) failed++
                }
            }
            working.value = null
            _messages.tryEmit(
                if (failed == 0) "Restored $total" else "Restored ${total - failed}, $failed failed"
            )
        }
    }

    fun deleteSelectedForever() {
        val (fileIds, folderIds) = partitionSelection()
        val total = fileIds.size + folderIds.size
        clearSelection()
        viewModelScope.launch {
            working.value = "Deleting $total item${if (total == 1) "" else "s"}…"
            var failed = 0
            var lastError: String? = null
            withContext(NonCancellable) {
                if (fileIds.isNotEmpty()) {
                    val result = trashRepository.deleteFilesPermanently(fileIds)
                    if (result is AppResult.Failure) {
                        failed += fileIds.size
                        lastError = result.error.toUserMessage(context)
                    }
                }
                folderIds.forEach { folderId ->
                    val result = trashRepository.deleteFolderPermanently(folderId)
                    if (result is AppResult.Failure) {
                        failed++
                        lastError = result.error.toUserMessage(context)
                    }
                }
            }
            working.value = null
            _messages.tryEmit(
                when {
                    failed == 0 -> "Deleted $total permanently"
                    else -> lastError ?: "$failed of $total could not be deleted"
                }
            )
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            working.value = "Emptying trash…"
            val result = withContext(NonCancellable) { trashRepository.emptyTrash() }
            working.value = null
            when (result) {
                is AppResult.Success -> _messages.tryEmit(context.getString(R.string.message_trash_emptied))
                is AppResult.Failure -> _messages.tryEmit(result.error.toUserMessage(context))
            }
        }
    }

    private fun partitionSelection(): Pair<List<String>, List<String>> {
        val selected = selection.value
        val items = uiState.value.items
        val fileIds = items.filterIsInstance<TrashItem.File>()
            .map { it.file.id }
            .filter { it in selected }
        val folderIds = items.filterIsInstance<TrashItem.Folder>()
            .map { it.folder.id }
            .filter { it in selected }
        return fileIds to folderIds
    }

    private data class TrashTreeCache(
        val children: Map<String, List<TrashItem>> = emptyMap(),
        val counts: Map<String, Int> = emptyMap()
    )

    private companion object {
        const val MAX_TREE_DEPTH = 64
    }
}
