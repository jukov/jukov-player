package info.jukov.player.feature.library.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.domain.toAppError
import info.jukov.player.feature.search.domain.LibrarySearchItem
import info.jukov.player.feature.search.domain.SearchOffsets
import info.jukov.player.feature.search.domain.SearchUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LibraryViewModel(private val search: SearchUseCase) : ViewModel() {
    private val _searchActive = MutableStateFlow(false)
    val searchActive = _searchActive.asStateFlow()
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()
    private val _results = MutableStateFlow<LoadableState<List<LibrarySearchItem>>>(LoadableState.Content(emptyList()))
    val results = _results.asStateFlow()
    private val _hasMore = MutableStateFlow(false)
    val hasMore = _hasMore.asStateFlow()
    private var offsets = SearchOffsets()
    private var searchJob: Job? = null

    fun openSearch() { _searchActive.value = true }

    fun updateSearchQuery(value: String) {
        _query.value = value
        searchJob?.cancel()
        offsets = SearchOffsets()
        _hasMore.value = false
        if (value.trim().length < MIN_QUERY) {
            _results.value = LoadableState.Content(emptyList())
            return
        }
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            loadPage(append = false)
        }
    }

    fun loadMoreSearch() {
        if (_hasMore.value && searchJob?.isActive != true) {
            searchJob = viewModelScope.launch { loadPage(append = true) }
        }
    }

    fun retrySearch() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch { loadPage(append = _results.value.content?.isNotEmpty() == true) }
    }

    fun closeSearch() {
        searchJob?.cancel()
        _searchActive.value = false
        _query.value = ""
        offsets = SearchOffsets()
        _hasMore.value = false
        _results.value = LoadableState.Content(emptyList())
    }

    private suspend fun loadPage(append: Boolean) {
        val activeQuery = _query.value.trim()
        if (!_searchActive.value || activeQuery.length < MIN_QUERY) return
        val old = if (append) _results.value.content.orEmpty() else emptyList()
        _results.value = LoadableState.Loading(old.ifEmpty { null })
        try {
            val page = search.library(activeQuery, if (append) offsets else SearchOffsets(), PAGE_SIZE)
            if (!_searchActive.value || _query.value.trim() != activeQuery) return
            offsets = page.nextOffsets
            _hasMore.value = page.hasMore
            _results.value = LoadableState.Content(old + page.items)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            _results.value = LoadableState.Failure(error.toAppError(AppError.SearchFailed), old.ifEmpty { null })
        }
    }

    private companion object {
        const val MIN_QUERY = 2
        const val PAGE_SIZE = 100
        const val DEBOUNCE_MS = 350L
    }
}
