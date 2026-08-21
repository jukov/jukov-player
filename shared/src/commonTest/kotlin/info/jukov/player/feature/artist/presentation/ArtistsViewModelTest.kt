package info.jukov.player.feature.artist.presentation

import com.russhwolf.settings.MapSettings
import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.ArtistSortCriterion
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.domain.Page
import info.jukov.player.core.domain.SettingsSortPreferences
import info.jukov.player.core.domain.SortDirection
import info.jukov.player.core.domain.SortOption
import info.jukov.player.core.presentation.LoadingOrigin
import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.artist.domain.Artist
import info.jukov.player.feature.artist.domain.ArtistsRepository
import info.jukov.player.feature.artist.domain.GetArtistsUseCase
import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.feature.auth.domain.AuthState
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
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertFalse
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistsViewModelTest {
    @AfterTest
    fun resetMainDispatcher() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun initialLoadRefreshFailureAndSortPreserveDisplayedArtists() = runTest {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val initial = MutableSharedFlow<LoadableState<List<Artist>>>(replay = 1)
        val refresh = MutableSharedFlow<LoadableState<List<Artist>>>(replay = 1)
        val repository = RecordingArtistsRepository(initial, refresh)
        val viewModel = viewModel(repository, TestAuthRepository(loggedInState()))
        runCurrent()

        assertEquals(listOf(false), repository.forceRefreshRequests)
        assertEquals(LoadingOrigin.Initial, viewModel.loadingOrigin.value)

        val artists = listOf(artist("z", "Zulu"), artist("a", "Alpha"))
        initial.emit(LoadableState.Content(artists))
        runCurrent()
        assertEquals(listOf("a", "z"), viewModel.state.value.content?.map(Artist::id))

        viewModel.updateSort(SortOption(ArtistSortCriterion.Name, SortDirection.Descending))
        assertEquals(listOf("z", "a"), viewModel.state.value.content?.map(Artist::id))

        viewModel.refresh()
        runCurrent()
        refresh.emit(LoadableState.Loading(content = null))
        runCurrent()
        assertEquals(LoadingOrigin.PullToRefresh, viewModel.loadingOrigin.value)
        assertEquals(listOf("z", "a"), viewModel.state.value.content?.map(Artist::id))

        refresh.emit(LoadableState.Failure(AppError.ArtistsLoadFailed, content = null))
        runCurrent()
        val failure = assertIs<LoadableState.Failure<List<Artist>>>(viewModel.state.value)
        assertEquals(listOf("z", "a"), failure.content?.map(Artist::id))
        assertEquals(null, viewModel.loadingOrigin.value)
        assertEquals(listOf(false, true), repository.forceRefreshRequests)
    }

    @Test
    fun refreshedRequestCancelsStaleCollector() = runTest {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val initial = MutableSharedFlow<LoadableState<List<Artist>>>(replay = 1)
        val refresh = MutableSharedFlow<LoadableState<List<Artist>>>(replay = 1)
        val viewModel = viewModel(
            RecordingArtistsRepository(initial, refresh),
            TestAuthRepository(loggedInState()),
        )
        runCurrent()
        initial.emit(LoadableState.Content(listOf(artist("initial", "Initial"))))
        runCurrent()

        viewModel.retry()
        runCurrent()
        refresh.emit(LoadableState.Content(listOf(artist("fresh", "Fresh"))))
        runCurrent()
        initial.emit(LoadableState.Content(listOf(artist("stale", "Stale"))))
        advanceUntilIdle()

        assertEquals(listOf("fresh"), viewModel.state.value.content?.map(Artist::id))
    }

    @Test
    fun logoutCancelsLoadClosesSearchAndClearsContent() = runTest {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val source = MutableSharedFlow<LoadableState<List<Artist>>>(replay = 1)
        val authRepository = TestAuthRepository(loggedInState())
        val viewModel = viewModel(RecordingArtistsRepository(source), authRepository)
        runCurrent()
        source.emit(LoadableState.Content(listOf(artist("artist", "Artist"))))
        runCurrent()
        viewModel.openSearch()

        authRepository.state.value = AuthState.LoggedOut
        runCurrent()
        source.emit(LoadableState.Content(listOf(artist("stale", "Stale"))))
        advanceUntilIdle()

        assertEquals(LoadableState.Content(emptyList()), viewModel.state.value)
        assertEquals(null, viewModel.loadingOrigin.value)
        assertFalse(viewModel.searchActive.value)
        assertEquals("", viewModel.searchQuery.value)
    }

    private fun kotlinx.coroutines.test.TestScope.viewModel(
        repository: ArtistsRepository,
        authRepository: AuthRepository,
    ) = ArtistsViewModel(
        getArtistsUseCase = GetArtistsUseCase(repository),
        authRepository = authRepository,
        favoriteDelegate = FavoriteDelegate(ArtistsFavoritesRepository()),
        search = SearchUseCase(ArtistsSearchRepository()),
        sortPreferences = SettingsSortPreferences(MapSettings()),
        getTracksUseCase = GetTracksUseCase(ArtistsTracksRepository()),
        downloadDelegate = DownloadDelegate(RecordingDownloadsRepository(), backgroundScope),
    )

    private fun artist(id: String, name: String) = Artist(
        id = id,
        name = name,
        albumCount = 1,
        coverArtId = null,
    )

    private fun loggedInState() = AuthState.LoggedIn(
        AuthSession("https://music.test", "user", "token", "salt"),
    )
}

private class RecordingArtistsRepository(
    vararg sources: Flow<LoadableState<List<Artist>>>,
) : ArtistsRepository {
    private val sources = ArrayDeque(sources.toList())
    val forceRefreshRequests = mutableListOf<Boolean>()

    override fun getArtists(forceRefresh: Boolean): Flow<LoadableState<List<Artist>>> {
        forceRefreshRequests += forceRefresh
        return sources.removeFirst()
    }
}

private class TestAuthRepository(initial: AuthState) : AuthRepository {
    val state = MutableStateFlow(initial)
    override val authState = state

    override suspend fun login(
        serverUrl: String,
        username: String,
        password: String,
    ): Result<AuthSession> = error("Not used")

    override suspend fun logout() {
        state.value = AuthState.LoggedOut
    }
}

private class ArtistsFavoritesRepository : FavoritesRepository {
    override val changes = MutableSharedFlow<FavoriteChange>()
    override fun getFavorites(forceRefresh: Boolean): Flow<LoadableState<Favorites>> = emptyFlow()
    override suspend fun setFavorite(target: FavoriteTarget, isFavorite: Boolean) = Result.success(Unit)
    override suspend fun setFavorites(targets: List<FavoriteTarget>, isFavorite: Boolean) =
        Result.success(Unit)
}

private class ArtistsTracksRepository : TracksRepository {
    override fun getTracks(
        filter: TracksFilter,
        forceRefresh: Boolean,
    ): Flow<LoadableState<List<Track>>> = emptyFlow()

    override suspend fun getTracksPage(offset: Int, size: Int, forceRefresh: Boolean) =
        Page<Track>(emptyList(), hasMore = false)
}

private class ArtistsSearchRepository : SearchRepository {
    override suspend fun artists(query: String, offset: Int, size: Int) =
        SearchPage<Artist>(emptyList(), nextOffset = 0, hasMore = false)

    override suspend fun albums(query: String, offset: Int, size: Int, artistId: String?) =
        SearchPage<Album>(emptyList(), nextOffset = 0, hasMore = false)

    override suspend fun tracks(query: String, offset: Int, size: Int, artistId: String?) =
        SearchPage<Track>(emptyList(), nextOffset = 0, hasMore = false)

    override suspend fun library(query: String, offsets: SearchOffsets, size: Int) =
        LibrarySearchPage(emptyList(), SearchOffsets(), hasMore = false)
}
