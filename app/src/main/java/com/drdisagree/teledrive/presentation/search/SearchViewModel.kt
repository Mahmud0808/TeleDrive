package com.drdisagree.teledrive.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drdisagree.teledrive.domain.model.FileQuerySpec
import com.drdisagree.teledrive.domain.model.DriveFile
import com.drdisagree.teledrive.domain.model.DriveFolder
import com.drdisagree.teledrive.domain.model.FileCategory
import com.drdisagree.teledrive.domain.model.FileSortField
import com.drdisagree.teledrive.domain.model.SortDirection
import com.drdisagree.teledrive.domain.repository.FileRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlin.time.Duration.Companion.milliseconds

data class SearchFilters(
    val category: FileCategory? = null,
    val backedUpOnly: Boolean = false,
    val notBackedUpOnly: Boolean = false,
    val minSizeMb: Int? = null,
    val sortField: FileSortField = FileSortField.DATE_MODIFIED,
    val sortDirection: SortDirection = SortDirection.DESCENDING
)

data class SearchUiState(
    val query: String = "",
    val filters: SearchFilters = SearchFilters(),
    val results: List<DriveFile> = emptyList(),
    val folders: List<DriveFolder> = emptyList(),
    val searching: Boolean = false,
    val searched: Boolean = false
)

/**
 * Local-metadata search with debounced input. No remote calls happen while
 * typing; everything queries the Room index.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    private val fileRepository: FileRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filters = MutableStateFlow(SearchFilters())
    private val searching = MutableStateFlow(false)

    private val results = combine(
        query.debounce(250.milliseconds).distinctUntilChanged(),
        filters
    ) { text, filterValues -> text to filterValues }
        .flatMapLatest { (text, filterValues) ->
            if (text.isBlank() && filterValues.category == null &&
                !filterValues.backedUpOnly && !filterValues.notBackedUpOnly &&
                filterValues.minSizeMb == null
            ) {
                flowOf(emptyList())
            } else {
                searching.update { true }
                fileRepository.observeFiles(
                    FileQuerySpec(
                        nameQuery = text.takeIf { it.isNotBlank() },
                        categories = filterValues.category?.let { listOf(it) } ?: emptyList(),
                        backedUpOnly = filterValues.backedUpOnly,
                        notBackedUpOnly = filterValues.notBackedUpOnly,
                        minSizeBytes = filterValues.minSizeMb?.let { it.toLong() * 1024 * 1024 },
                        sortField = filterValues.sortField,
                        sortDirection = filterValues.sortDirection
                    )
                ).map { list ->
                    searching.update { false }
                    list.take(500)
                }
            }
        }

    private val folderResults = combine(
        query.debounce(250.milliseconds).distinctUntilChanged(),
        filters
    ) { text, filterValues -> text to filterValues }
        .flatMapLatest { (text, filterValues) ->
            val fileOnlyFilterActive = filterValues.category != null ||
                    filterValues.backedUpOnly ||
                    filterValues.notBackedUpOnly ||
                    filterValues.minSizeMb != null
            if (text.isBlank() || fileOnlyFilterActive) {
                flowOf(emptyList())
            } else {
                fileRepository.searchFolders(text)
            }
        }

    val uiState: StateFlow<SearchUiState> = combine(
        query,
        filters,
        results,
        searching,
        folderResults
    ) { text, filterValues, resultList, isSearching, folderList ->
        SearchUiState(
            query = text,
            filters = filterValues,
            results = resultList,
            folders = folderList,
            searching = isSearching,
            searched = text.isNotBlank() || filterValues != SearchFilters()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun setQuery(value: String) = query.update { value }

    fun setCategory(category: FileCategory?) = filters.update { it.copy(category = category) }

    fun setBackedUpOnly(value: Boolean) = filters.update {
        it.copy(backedUpOnly = value, notBackedUpOnly = if (value) false else it.notBackedUpOnly)
    }

    fun setNotBackedUpOnly(value: Boolean) = filters.update {
        it.copy(notBackedUpOnly = value, backedUpOnly = if (value) false else it.backedUpOnly)
    }

    fun setSort(field: FileSortField, direction: SortDirection) = filters.update {
        it.copy(sortField = field, sortDirection = direction)
    }
}
