package info.jukov.player.feature.track.presentation

import com.russhwolf.settings.MapSettings
import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.domain.Page
import info.jukov.player.core.domain.SettingsSortPreferences
import info.jukov.player.core.domain.SortDirection
import info.jukov.player.core.domain.SortOption
import info.jukov.player.core.domain.TrackSortCriterion
import info.jukov.player.core.presentation.LoadingOrigin
import info.jukov.player.feature.album.domain.Album
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
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

@OptIn(ExperimentalCoroutinesApi::class)
class TracksViewModelTest {
    @AfterTest
    fun resetMainDispatcher() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun artistLoadRefreshFailureAndSortPreserveDisplayedTracks() = runTest {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val tracks = listOf(
            track("z", title = "Zulu", artist = "Beta"),
            track("a", title = "Alpha", artist = "Alpha"),
        )
        val refresh = MutableSharedFlow<LoadableState<List<Track>>>(replay = 1)
        val repository = RecordingTracksViewModelRepository(
            flowOf(LoadableState.Content(tracks)),
            refresh,
        )
        val viewModel = viewModel(repository)

        viewModel.load(TracksFilter.ByArtist("artist"))
        advanceUntilIdle()
        assertEquals(listOf("a", "z"), viewModel.state.value.content?.map(Track::id))

        val sort = SortOption(TrackSortCriterion.Artist, SortDirection.Descending)
        viewModel.updateSort(sort)
        assertEquals(listOf("z", "a"), viewModel.state.value.content?.map(Track::id))

        viewModel.refresh()
        runCurrent()
        refresh.emit(LoadableState.Loading(content = null))
        runCurrent()
        assertEquals(LoadingOrigin.PullToRefresh, viewModel.loadingOrigin.value)
        assertEquals(listOf("z", "a"), viewModel.state.value.content?.map(Track::id))

        refresh.emit(LoadableState.Failure(AppError.TracksLoadFailed, content = null))
        runCurrent()
        val failure = assertIs<LoadableState.Failure<List<Track>>>(viewModel.state.value)
        assertEquals(listOf("z", "a"), failure.content?.map(Track::id))
        assertEquals(null, viewModel.loadingOrigin.value)
        assertEquals(listOf(false, true), repository.forceRefreshRequests)
    }

    @Test
    fun changingFilterCancelsStaleCollector() = runTest {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val old = MutableSharedFlow<LoadableState<List<Track>>>(replay = 1)
        val fresh = MutableSharedFlow<LoadableState<List<Track>>>(replay = 1)
        val repository = RecordingTracksViewModelRepository(old, fresh)
        val viewModel = viewModel(repository)
        viewModel.load(TracksFilter.ByAlbum("old"))
        runCurrent()
        old.emit(LoadableState.Content(listOf(track("old"))))
        runCurrent()

        viewModel.load(TracksFilter.ByAlbum("new"))
        runCurrent()
        fresh.emit(LoadableState.Content(listOf(track("fresh"))))
        runCurrent()
        old.emit(LoadableState.Content(listOf(track("stale"))))
        advanceUntilIdle()

        assertEquals(listOf("fresh"), viewModel.state.value.content?.map(Track::id))
    }

    private fun kotlinx.coroutines.test.TestScope.viewModel(repository: TracksRepository) =
        TracksViewModel(
            getTracksUseCase = GetTracksUseCase(repository),
            favoriteDelegate = FavoriteDelegate(TracksFavoritesRepository()),
            downloadDelegate = DownloadDelegate(RecordingDownloadsRepository(), backgroundScope),
            search = SearchUseCase(TracksSearchRepository()),
            sortPreferences = SettingsSortPreferences(MapSettings()),
        )

    private fun track(
        id: String,
        title: String = "Track $id",
        artist: String = "Artist",
    ) = Track(
        id = id,
        title = title,
        artist = artist,
        albumId = null,
        artistId = null,
        trackNumber = null,
        coverArtUrl = null,
        isFavorite = false,
    )
}

private class RecordingTracksViewModelRepository(
    vararg sources: Flow<LoadableState<List<Track>>>,
) : TracksRepository {
    private val sources = ArrayDeque(sources.toList())
    val forceRefreshRequests = mutableListOf<Boolean>()

    override fun getTracks(
        filter: TracksFilter,
        forceRefresh: Boolean,
    ): Flow<LoadableState<List<Track>>> {
        forceRefreshRequests += forceRefresh
        return sources.removeFirst()
    }

    override suspend fun getTracksPage(offset: Int, size: Int, forceRefresh: Boolean) =
        Page<Track>(emptyList(), hasMore = false)
}

private class TracksFavoritesRepository : FavoritesRepository {
    override val changes = MutableSharedFlow<FavoriteChange>()
    override fun getFavorites(forceRefresh: Boolean): Flow<LoadableState<Favorites>> = emptyFlow()
    override suspend fun setFavorite(target: FavoriteTarget, isFavorite: Boolean) = Result.success(Unit)
    override suspend fun setFavorites(targets: List<FavoriteTarget>, isFavorite: Boolean) =
        Result.success(Unit)
}

private class TracksSearchRepository : SearchRepository {
    override suspend fun artists(query: String, offset: Int, size: Int) =
        SearchPage<Artist>(emptyList(), nextOffset = 0, hasMore = false)

    override suspend fun albums(query: String, offset: Int, size: Int, artistId: String?) =
        SearchPage<Album>(emptyList(), nextOffset = 0, hasMore = false)

    override suspend fun tracks(query: String, offset: Int, size: Int, artistId: String?) =
        SearchPage<Track>(emptyList(), nextOffset = 0, hasMore = false)

    override suspend fun library(query: String, offsets: SearchOffsets, size: Int) =
        LibrarySearchPage(emptyList(), SearchOffsets(), hasMore = false)
}
