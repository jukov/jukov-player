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
import kotlinx.coroutines.flow.update
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
            launchRequest(append = false, debounce = true)
        }
    }

    fun loadMore() {
        if (_hasMore.value && job?.isActive != true) {
            launchRequest(append = true, debounce = false)
        }
    }

    fun retry() {
        launchRequest(
            append = _state.value.content?.isNotEmpty() == true,
            debounce = false,
        )
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

    private fun launchRequest(append: Boolean, debounce: Boolean) {
        job?.cancel()
        job = scope.launch {
            if (debounce) {
                delay(DEBOUNCE_MS)
            }
            requestPage(append)
        }
    }

    private suspend fun requestPage(append: Boolean) {
        val activeQuery = _query.value.trim()
        if (!_active.value || activeQuery.length < MIN_QUERY_LENGTH) {
            return
        }
        val old = if (append) {
            _state.value.content.orEmpty()
        } else {
            emptyList()
        }
        _state.update { current ->
            val displayed = if (append) {
                current.content.orEmpty()
            } else {
                emptyList()
            }
            LoadableState.Loading(displayed.ifEmpty { null })
        }
        try {
            val requestOffset = if (append) {
                offset
            } else {
                0
            }
            val page = loadPage(activeQuery, requestOffset, PAGE_SIZE)
            if (!_active.value || _query.value.trim() != activeQuery) {
                return
            }
            offset = page.nextOffset
            _hasMore.value = page.hasMore
            _state.update { LoadableState.Content(old + page.items) }
        } catch (error: Throwable) {
            if (error is CancellationException) {
                throw error
            }
            if (_active.value && _query.value.trim() == activeQuery) {
                _state.update { current ->
                    LoadableState.Failure(
                        error = error.toAppError(AppError.SearchFailed),
                        content = current.content ?: old.ifEmpty { null },
                    )
                }
            }
        }
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 2
        const val PAGE_SIZE = 100
        const val DEBOUNCE_MS = 350L
    }
}
