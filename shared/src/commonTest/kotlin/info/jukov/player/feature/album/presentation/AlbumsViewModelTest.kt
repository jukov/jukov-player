package info.jukov.player.feature.album.presentation

import com.russhwolf.settings.MapSettings
import info.jukov.player.core.domain.AlbumSortCriterion
import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.domain.Page
import info.jukov.player.core.domain.SettingsSortPreferences
import info.jukov.player.core.domain.SortDirection
import info.jukov.player.core.domain.SortOption
import info.jukov.player.core.presentation.LoadingOrigin
import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.album.domain.AlbumsRepository
import info.jukov.player.feature.album.domain.GetAlbumsUseCase
import info.jukov.player.feature.artist.domain.Artist
import info.jukov.player.feature.download.presentation.DownloadDelegate
import info.jukov.player.feature.download.presentation.RecordingDownloadsRepository
import info.jukov.player.feature.favorite.domain.FavoriteChange
import info.jukov.player.feature.favorite.domain.FavoriteTarget
import info.jukov.player.feature.favorite.domain.Favorites
import info.jukov.player.feature.favorite.domain.FavoritesRepository
import info.jukov.player.feature.favorite.presentation.FavoriteDelegate
import info.jukov.player.feature.search.domain.LibrarySearchPage
import info.jukov.player.feature.search.domain.SearchOffsets
import info.jukov.player.feature.search.domain.SearchPage
import info.jukov.player.feature.search.domain.SearchRepository
import info.jukov.player.feature.search.domain.SearchUseCase
import info.jukov.player.feature.track.domain.GetTracksUseCase
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.track.domain.TracksFilter
import info.jukov.player.feature.track.domain.TracksRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumsViewModelTest {
    @AfterTest
    fun resetMainDispatcher() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun paginationAppendsAndFailurePreservesDisplayedAlbums() = runTest {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val secondPage = CompletableDeferred<Page<Album>>()
        val failedPage = CompletableDeferred<Unit>()
        val repository = RecordingAlbumsRepository(
            pageResponses = ArrayDeque(
                listOf(
                    { Page(listOf(album("first")), hasMore = true) },
                    { secondPage.await() },
                    {
                        failedPage.await()
                        throw IllegalStateException("offline")
                    },
                ),
            ),
        )
        val viewModel = viewModel(repository)
        viewModel.load(artistId = null)
        advanceUntilIdle()

        assertEquals(LoadableState.Content(listOf(album("first"))), viewModel.state.value)
        assertTrue(viewModel.hasMore.value)

        viewModel.loadMore()
        runCurrent()
        assertEquals(LoadableState.Loading(listOf(album("first"))), viewModel.state.value)
        assertEquals(LoadingOrigin.Pagination, viewModel.loadingOrigin.value)
        secondPage.complete(Page(listOf(album("second")), hasMore = true))
        advanceUntilIdle()
        assertEquals(listOf("first", "second"), viewModel.state.value.content?.map(Album::id))

        viewModel.loadMore()
        runCurrent()
        failedPage.complete(Unit)
        advanceUntilIdle()

        val failure = assertIs<LoadableState.Failure<List<Album>>>(viewModel.state.value)
        assertEquals(AppError.AlbumsLoadFailed, failure.error)
        assertEquals(listOf("first", "second"), failure.content?.map(Album::id))
        assertEquals(null, viewModel.loadingOrigin.value)
        assertEquals(listOf(0, 1, 2), repository.pageRequests.map(PageRequest::offset))
    }

    @Test
    fun refreshPreservesContentAndSortCancelsItsStaleRequest() = runTest {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val staleRefresh = CompletableDeferred<Page<Album>>()
        val repository = RecordingAlbumsRepository(
            pageResponses = ArrayDeque(
                listOf(
                    { Page(listOf(album("old")), hasMore = true) },
                    { staleRefresh.await() },
                    { Page(listOf(album("sorted", year = 2024)), hasMore = false) },
                ),
            ),
        )
        val viewModel = viewModel(repository)
        viewModel.load(artistId = null)
        advanceUntilIdle()

        viewModel.refresh()
        runCurrent()
        assertEquals(LoadableState.Loading(listOf(album("old"))), viewModel.state.value)
        assertEquals(LoadingOrigin.PullToRefresh, viewModel.loadingOrigin.value)

        val sort = SortOption(AlbumSortCriterion.Year, SortDirection.Descending)
        viewModel.updateSort(sort)
        advanceUntilIdle()
        staleRefresh.complete(Page(listOf(album("stale")), hasMore = false))
        advanceUntilIdle()

        assertEquals(listOf("sorted"), viewModel.state.value.content?.map(Album::id))
        assertEquals(sort, viewModel.sort.value)
        assertEquals(listOf(false, true, false), repository.pageRequests.map(PageRequest::forceRefresh))
        assertEquals(LoadingOrigin.Sorting, repository.pageRequests.last().originObserved)
    }

    private fun kotlinx.coroutines.test.TestScope.viewModel(repository: AlbumsRepository) =
        AlbumsViewModel(
            getAlbumsUseCase = GetAlbumsUseCase(repository),
            favoriteDelegate = FavoriteDelegate(AlbumsFavoritesRepository()),
            downloadDelegate = DownloadDelegate(RecordingDownloadsRepository(), backgroundScope),
            getTracksUseCase = GetTracksUseCase(AlbumsTracksRepository()),
            search = SearchUseCase(AlbumsSearchRepository()),
            sortPreferences = SettingsSortPreferences(MapSettings()),
        ).also { albumsViewModel = it }

    private fun album(id: String, year: Int? = null) = Album(
        id = id,
        name = "Album $id",
        artist = "Artist",
        artistId = null,
        coverArtUrl = null,
        year = year,
    )

    private var albumsViewModel: AlbumsViewModel? = null

    private inner class RecordingAlbumsRepository(
        private val pageResponses: ArrayDeque<suspend () -> Page<Album>>,
    ) : AlbumsRepository {
        val pageRequests = mutableListOf<PageRequest>()

        override fun getAlbums(
            artistId: String?,
            forceRefresh: Boolean,
        ): Flow<LoadableState<List<Album>>> = emptyFlow()

        override suspend fun getAlbumsPage(
            offset: Int,
            size: Int,
            sort: SortOption<AlbumSortCriterion>,
            forceRefresh: Boolean,
        ): Page<Album> {
            pageRequests += PageRequest(
                offset = offset,
                size = size,
                sort = sort,
                forceRefresh = forceRefresh,
                originObserved = albumsViewModel?.loadingOrigin?.value,
            )
            return pageResponses.removeFirst().invoke()
        }
    }
}

private data class PageRequest(
    val offset: Int,
    val size: Int,
    val sort: SortOption<AlbumSortCriterion>,
    val forceRefresh: Boolean,
    val originObserved: LoadingOrigin?,
)

private class AlbumsFavoritesRepository : FavoritesRepository {
    override val changes = MutableSharedFlow<FavoriteChange>()
    override fun getFavorites(forceRefresh: Boolean): Flow<LoadableState<Favorites>> = emptyFlow()
    override suspend fun setFavorite(target: FavoriteTarget, isFavorite: Boolean) = Result.success(Unit)
    override suspend fun setFavorites(targets: List<FavoriteTarget>, isFavorite: Boolean) =
        Result.success(Unit)
}

private class AlbumsTracksRepository : TracksRepository {
    override fun getTracks(
        filter: TracksFilter,
        forceRefresh: Boolean,
    ): Flow<LoadableState<List<Track>>> = emptyFlow()

    override suspend fun getTracksPage(offset: Int, size: Int, forceRefresh: Boolean) =
        Page<Track>(emptyList(), hasMore = false)
}

private class AlbumsSearchRepository : SearchRepository {
    override suspend fun artists(query: String, offset: Int, size: Int) =
        SearchPage<Artist>(emptyList(), nextOffset = 0, hasMore = false)

    override suspend fun albums(query: String, offset: Int, size: Int, artistId: String?) =
        SearchPage<Album>(emptyList(), nextOffset = 0, hasMore = false)

    override suspend fun tracks(query: String, offset: Int, size: Int, artistId: String?) =
        SearchPage<Track>(emptyList(), nextOffset = 0, hasMore = false)

    override suspend fun library(query: String, offsets: SearchOffsets, size: Int) =
        LibrarySearchPage(emptyList(), SearchOffsets(), hasMore = false)
}
