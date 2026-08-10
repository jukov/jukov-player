package info.jukov.player.feature.library.presentation

import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.artist.domain.Artist
import info.jukov.player.feature.search.domain.LibrarySearchItem
import info.jukov.player.feature.search.domain.LibrarySearchPage
import info.jukov.player.feature.search.domain.SearchOffsets
import info.jukov.player.feature.search.domain.SearchPage
import info.jukov.player.feature.search.domain.SearchRepository
import info.jukov.player.feature.search.domain.SearchUseCase
import info.jukov.player.feature.track.domain.Track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    @AfterTest
    fun resetMainDispatcher() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun searchWaitsForDebounceAndPublishesPage() = runTest {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = FakeSearchRepository()
        val viewModel = LibraryViewModel(SearchUseCase(repository))
        viewModel.openSearch()

        viewModel.updateSearchQuery("te")
        advanceTimeBy(349)
        assertTrue(repository.queries.isEmpty())
        advanceTimeBy(1)
        advanceUntilIdle()

        assertEquals(listOf("te"), repository.queries)
        assertEquals(listOf(RESULT), viewModel.results.value.content)
        assertTrue(viewModel.hasMore.value)
    }

    @Test
    fun shortReplacementCancelsPendingSearchAndClearsResults() = runTest {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = FakeSearchRepository()
        val viewModel = LibraryViewModel(SearchUseCase(repository))
        viewModel.openSearch()

        viewModel.updateSearchQuery("test")
        viewModel.updateSearchQuery("t")
        advanceUntilIdle()

        assertTrue(repository.queries.isEmpty())
        assertEquals(emptyList(), viewModel.results.value.content)
        assertFalse(viewModel.hasMore.value)
    }

    @Test
    fun failedSearchPublishesMappedFailure() = runTest {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModel = LibraryViewModel(
            SearchUseCase(FakeSearchRepository(failure = IllegalStateException("offline"))),
        )
        viewModel.openSearch()

        viewModel.updateSearchQuery("test")
        advanceUntilIdle()

        val failure = assertIs<LoadableState.Failure<List<LibrarySearchItem>>>(
            viewModel.results.value,
        )
        assertEquals(AppError.SearchFailed, failure.error)
    }

    private class FakeSearchRepository(
        private val failure: Throwable? = null,
    ) : SearchRepository {
        val queries = mutableListOf<String>()

        override suspend fun library(
            query: String,
            offsets: SearchOffsets,
            size: Int,
        ): LibrarySearchPage {
            queries += query
            failure?.let { throw it }
            return LibrarySearchPage(
                items = listOf(RESULT),
                nextOffsets = SearchOffsets(tracks = 1),
                hasMore = true,
            )
        }

        override suspend fun artists(query: String, offset: Int, size: Int): SearchPage<Artist> =
            SearchPage(emptyList(), offset, hasMore = false)

        override suspend fun albums(
            query: String,
            offset: Int,
            size: Int,
            artistId: String?,
        ): SearchPage<Album> = SearchPage(emptyList(), offset, hasMore = false)

        override suspend fun tracks(
            query: String,
            offset: Int,
            size: Int,
            artistId: String?,
        ): SearchPage<Track> = SearchPage(emptyList(), offset, hasMore = false)
    }

    private companion object {
        val RESULT = LibrarySearchItem.TrackItem(
            Track(
                id = "track-1",
                title = "Test Song",
                artist = "Test Artist",
                albumId = null,
                artistId = null,
                trackNumber = null,
                coverArtUrl = null,
                streamUrl = "https://music.test/stream",
                isFavorite = false,
            ),
        )
    }
}
