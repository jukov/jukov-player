package info.jukov.player.feature.favorite.presentation

import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.domain.Page
import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.download.presentation.DownloadDelegate
import info.jukov.player.feature.download.presentation.RecordingDownloadsRepository
import info.jukov.player.feature.favorite.domain.FavoriteChange
import info.jukov.player.feature.favorite.domain.FavoriteTarget
import info.jukov.player.feature.favorite.domain.Favorites
import info.jukov.player.feature.favorite.domain.FavoritesRepository
import info.jukov.player.feature.track.domain.GetTracksUseCase
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.track.domain.TracksFilter
import info.jukov.player.feature.track.domain.TracksRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {
    @AfterTest
    fun resetMainDispatcher() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun toggleFavoriteAlbumsUpdatesChangedAlbumsAndClearsPending() = runTest {
        setMainDispatcher()
        val repository = ViewModelFavoritesRepository()
        val albums = listOf(album("new", isFavorite = false), album("existing", isFavorite = true))
        val viewModel = viewModel(repository, albums = albums)
        viewModel.load()
        advanceUntilIdle()

        viewModel.toggleFavoriteAlbums(albums)
        advanceUntilIdle()

        assertEquals(listOf(FavoriteTarget.Album("new")) to true, repository.bulkCalls.single())
        assertTrue(viewModel.state.value.content?.albums.orEmpty().all(Album::isFavorite))
        assertTrue(viewModel.pending.value.isEmpty())
    }

    @Test
    fun toggleFavoriteAlbumsReportsFailureAndKeepsOriginalState() = runTest {
        setMainDispatcher()
        val repository = ViewModelFavoritesRepository(
            result = Result.failure(IllegalStateException("failed")),
        )
        val album = album("album", isFavorite = false)
        val viewModel = viewModel(repository, albums = listOf(album))
        viewModel.load()
        advanceUntilIdle()
        var message: AppError? = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            message = viewModel.messages.first()
        }

        viewModel.toggleFavoriteAlbums(listOf(album))
        advanceUntilIdle()

        assertEquals(AppError.FavoriteUpdateFailed, message)
        assertEquals(false, viewModel.state.value.content?.albums?.single()?.isFavorite)
        assertTrue(viewModel.pending.value.isEmpty())
    }

    @Test
    fun downloadAlbumsDispatchesEverySelectedAlbum() = runTest {
        setMainDispatcher()
        val downloadsRepository = RecordingDownloadsRepository()
        val albums = listOf(album("first"), album("second"))
        val viewModel = viewModel(
            repository = ViewModelFavoritesRepository(),
            downloadsRepository = downloadsRepository,
        )

        viewModel.downloadAlbums(albums)
        advanceUntilIdle()

        assertEquals(albums, downloadsRepository.downloadedAlbums)
    }

    @Test
    fun addAlbumsToQueueAggregatesTracksInAlbumAndTrackOrder() = runTest {
        setMainDispatcher()
        val firstTracks = listOf(track("first-1"), track("first-2"))
        val secondTracks = listOf(track("second-1"))
        val tracksRepository = RecordingTracksRepository(
            mapOf("first" to firstTracks, "second" to secondTracks),
        )
        val viewModel = viewModel(
            repository = ViewModelFavoritesRepository(),
            tracksRepository = tracksRepository,
        )
        var queued = emptyList<Track>()

        viewModel.addAlbumsToQueue(listOf(album("first"), album("second"))) { queued = it }
        advanceUntilIdle()

        assertEquals(firstTracks + secondTracks, queued)
        assertEquals(listOf("first", "second"), tracksRepository.requestedAlbumIds)
    }

    private fun kotlinx.coroutines.test.TestScope.setMainDispatcher() {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
    }

    private fun kotlinx.coroutines.test.TestScope.viewModel(
        repository: ViewModelFavoritesRepository,
        albums: List<Album> = emptyList(),
        downloadsRepository: RecordingDownloadsRepository = RecordingDownloadsRepository(),
        tracksRepository: RecordingTracksRepository = RecordingTracksRepository(emptyMap()),
    ) = FavoritesViewModel(
        repository = repository.apply { favorites = Favorites(albums = albums) },
        downloadDelegate = DownloadDelegate(downloadsRepository, backgroundScope),
        getTracksUseCase = GetTracksUseCase(tracksRepository),
    )

    private fun album(id: String, isFavorite: Boolean = false) = Album(
        id = id,
        name = "Album $id",
        artist = "Artist",
        artistId = null,
        coverArtUrl = null,
        isFavorite = isFavorite,
    )

    private fun track(id: String) = Track(
        id = id,
        title = "Track $id",
        artist = "Artist",
        albumId = null,
        artistId = null,
        trackNumber = null,
        coverArtUrl = null,
        isFavorite = false,
    )
}

private class ViewModelFavoritesRepository(
    private val result: Result<Unit> = Result.success(Unit),
) : FavoritesRepository {
    override val changes = MutableSharedFlow<FavoriteChange>()
    var favorites = Favorites()
    val bulkCalls = mutableListOf<Pair<List<FavoriteTarget>, Boolean>>()

    override fun getFavorites(forceRefresh: Boolean): Flow<LoadableState<Favorites>> =
        flowOf(LoadableState.Content(favorites))

    override suspend fun setFavorite(target: FavoriteTarget, isFavorite: Boolean): Result<Unit> =
        setFavorites(listOf(target), isFavorite)

    override suspend fun setFavorites(
        targets: List<FavoriteTarget>,
        isFavorite: Boolean,
    ): Result<Unit> {
        bulkCalls += targets to isFavorite
        return result
    }
}

private class RecordingTracksRepository(
    private val tracksByAlbum: Map<String, List<Track>>,
) : TracksRepository {
    val requestedAlbumIds = mutableListOf<String>()

    override fun getTracks(
        filter: TracksFilter,
        forceRefresh: Boolean,
    ): Flow<LoadableState<List<Track>>> {
        val albumId = (filter as TracksFilter.ByAlbum).albumId
        requestedAlbumIds += albumId
        return flowOf(LoadableState.Content(tracksByAlbum[albumId].orEmpty()))
    }

    override suspend fun getTracksPage(
        offset: Int,
        size: Int,
        forceRefresh: Boolean,
    ): Page<Track> = Page(emptyList(), hasMore = false)
}
