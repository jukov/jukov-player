package info.jukov.player.feature.search.presentation

import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.domain.toAppError
import info.jukov.player.core.domain.updateItem
import info.jukov.player.feature.search.domain.SearchPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PagedSearchDelegate<T>(
    private val scope: CoroutineScope,
    private val loadPage: suspend (query: String, offset: Int, size: Int) -> SearchPage<T>,
) {
    private val _active = MutableStateFlow(false)
    val active = _active.asStateFlow()
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()
    private val _state = MutableStateFlow<LoadableState<List<T>>>(LoadableState.Content(emptyList()))
    val state = _state.asStateFlow()
    private val _hasMore = MutableStateFlow(false)
    val hasMore = _hasMore.asStateFlow()

    private var offset = 0
    private var job: Job? = null

    fun open() {
        _active.value = true
    }

    fun updateQuery(value: String) {
        _query.value = value
        job?.cancel()
        offset = 0
        _hasMore.value = false
        if (value.trim().length < MIN_QUERY_LENGTH) {
            _state.value = LoadableState.Content(emptyList())
        } else {
            job = scope.launch {
                delay(DEBOUNCE_MS)
                requestPage(append = false)
            }
        }
    }

    fun loadMore() {
        if (_hasMore.value && job?.isActive != true) {
            job = scope.launch { requestPage(append = true) }
        }
    }

    fun retry() {
        job?.cancel()
        job = scope.launch {
            requestPage(append = _state.value.content?.isNotEmpty() == true)
        }
    }

    fun close() {
        job?.cancel()
        _active.value = false
        _query.value = ""
        offset = 0
        _hasMore.value = false
        _state.value = LoadableState.Content(emptyList())
    }

    fun updateItem(predicate: (T) -> Boolean, transform: (T) -> T) {
        _state.updateItem(predicate, transform)
    }

    private suspend fun requestPage(append: Boolean) {
        val activeQuery = _query.value.trim()
        if (!_active.value || activeQuery.length < MIN_QUERY_LENGTH) {
            return
        }
        val old = if (append) _state.value.content.orEmpty() else emptyList()
        _state.value = LoadableState.Loading(old.ifEmpty { null })
        try {
            val page = loadPage(activeQuery, if (append) offset else 0, PAGE_SIZE)
            if (!_active.value || _query.value.trim() != activeQuery) {
                return
            }
            offset = page.nextOffset
            _hasMore.value = page.hasMore
            _state.value = LoadableState.Content(old + page.items)
        } catch (error: Throwable) {
            if (error is CancellationException) {
                throw error
            }
            _state.value = LoadableState.Failure(
                error = error.toAppError(AppError.SearchFailed),
                content = old.ifEmpty { null },
            )
        }
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 2
        const val PAGE_SIZE = 100
        const val DEBOUNCE_MS = 350L
    }
}
