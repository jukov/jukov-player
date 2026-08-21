package info.jukov.player.feature.search.presentation

import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.feature.search.domain.SearchPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PagedSearchDelegateTest {
    @Test
    fun queryWaitsForDebounceAndPublishesFirstPage() = runTest {
        val requests = mutableListOf<SearchRequest>()
        val result = CompletableDeferred<SearchPage<String>>()
        val delegate = PagedSearchDelegate<String>(this) { query, offset, size ->
            requests += SearchRequest(query, offset, size)
            result.await()
        }
        delegate.open()

        delegate.updateQuery("  jazz  ")
        advanceTimeBy(349)
        assertTrue(requests.isEmpty())
        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf(SearchRequest("jazz", 0, 100)), requests)
        assertEquals(LoadableState.Loading(content = null), delegate.state.value)

        result.complete(SearchPage(listOf("first"), nextOffset = 1, hasMore = true))
        advanceUntilIdle()

        assertEquals(LoadableState.Content(listOf("first")), delegate.state.value)
        assertTrue(delegate.hasMore.value)
    }

    @Test
    fun retryAfterInitialFailureRestartsTheFirstPage() = runTest {
        var attempts = 0
        val delegate = PagedSearchDelegate<String>(this) { _, offset, _ ->
            attempts += 1
            if (attempts == 1) {
                throw IllegalStateException("offline")
            }
            SearchPage(listOf("recovered"), nextOffset = offset + 1, hasMore = false)
        }
        delegate.open()
        delegate.updateQuery("rock")
        advanceUntilIdle()

        val failure = assertIs<LoadableState.Failure<List<String>>>(delegate.state.value)
        assertEquals(AppError.SearchFailed, failure.error)
        assertEquals(null, failure.content)

        delegate.retry()
        advanceUntilIdle()

        assertEquals(2, attempts)
        assertEquals(LoadableState.Content(listOf("recovered")), delegate.state.value)
    }

    @Test
    fun loadMoreAppendsAndFailureKeepsPreviouslyLoadedContent() = runTest {
        var request = 0
        val failedPage = CompletableDeferred<Unit>()
        val delegate = PagedSearchDelegate<String>(this) { _, offset, _ ->
            request += 1
            when (request) {
                1 -> SearchPage(listOf("first"), nextOffset = 1, hasMore = true)
                2 -> SearchPage(listOf("second"), nextOffset = 2, hasMore = true)
                else -> {
                    failedPage.await()
                    throw IllegalStateException("offline")
                }
            }.also {
                assertEquals(request - 1, offset)
            }
        }
        delegate.open()
        delegate.updateQuery("pop")
        advanceUntilIdle()

        delegate.loadMore()
        advanceUntilIdle()
        assertEquals(LoadableState.Content(listOf("first", "second")), delegate.state.value)

        delegate.loadMore()
        runCurrent()
        assertEquals(
            LoadableState.Loading(listOf("first", "second")),
            delegate.state.value,
        )

        failedPage.complete(Unit)
        advanceUntilIdle()

        val failure = assertIs<LoadableState.Failure<List<String>>>(delegate.state.value)
        assertEquals(listOf("first", "second"), failure.content)
        assertEquals(AppError.SearchFailed, failure.error)
    }

    @Test
    fun replacingQueryCancelsStaleRequestAndOnlyPublishesLatestResults() = runTest {
        val oldResult = CompletableDeferred<SearchPage<String>>()
        val requests = mutableListOf<String>()
        val delegate = PagedSearchDelegate<String>(this) { query, _, _ ->
            requests += query
            if (query == "old") {
                oldResult.await()
            } else {
                SearchPage(listOf("new result"), nextOffset = 1, hasMore = false)
            }
        }
        delegate.open()
        delegate.updateQuery("old")
        advanceTimeBy(350)
        runCurrent()

        delegate.updateQuery("new")
        advanceUntilIdle()
        oldResult.complete(SearchPage(listOf("stale"), nextOffset = 1, hasMore = false))
        advanceUntilIdle()

        assertEquals(listOf("old", "new"), requests)
        assertEquals(LoadableState.Content(listOf("new result")), delegate.state.value)
        assertFalse(delegate.hasMore.value)
    }

    private data class SearchRequest(val query: String, val offset: Int, val size: Int)
}
