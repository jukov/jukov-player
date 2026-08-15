package info.jukov.player.feature.playback.presentation

import info.jukov.player.core.domain.LoadableState
import info.jukov.player.feature.favorite.domain.FavoriteChange
import info.jukov.player.feature.favorite.domain.FavoriteTarget
import info.jukov.player.feature.favorite.domain.Favorites
import info.jukov.player.feature.favorite.domain.FavoritesRepository
import info.jukov.player.feature.favorite.presentation.FavoriteDelegate
import info.jukov.player.feature.playback.domain.PlaybackController
import info.jukov.player.feature.playback.domain.PlaybackOrigin
import info.jukov.player.feature.playback.domain.PlaybackQueueResolver
import info.jukov.player.feature.playback.domain.PlaybackSnapshot
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.download.presentation.DownloadDelegate
import info.jukov.player.feature.download.presentation.RecordingDownloadsRepository
import info.jukov.player.feature.download.domain.DownloadState
import info.jukov.player.feature.download.domain.DownloadStatus
import info.jukov.player.feature.download.domain.OfflineLibrary
import info.jukov.player.feature.download.domain.OfflineTrack
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    @Test
    fun secondDownloadClickCancelsActiveCurrentTrackDownload() = runTest {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = RecordingDownloadsRepository(
            OfflineLibrary(
                tracks = listOf(OfflineTrack(TRACK, DownloadStatus(DownloadState.Downloading))),
            ),
        )
        val controller = FakePlaybackController().apply {
            state.value = LoadableState.Content(PlaybackSnapshot(queue = listOf(TRACK), currentIndex = 0))
        }
        val viewModel = PlayerViewModel(
            controller = controller,
            favoriteDelegate = FavoriteDelegate(FakeFavoritesRepository()),
            queueResolver = DeferredResolver(),
            downloadDelegate = DownloadDelegate(repository, backgroundScope),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        runCurrent()

        viewModel.toggleCurrentTrackDownload()
        advanceUntilIdle()

        assertEquals(listOf(TRACK.id), repository.cancelledTrackIds)
        assertTrue(repository.downloadedTracks.isEmpty())
    }

    @Test
    fun downloadsCurrentTrack() = runTest {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = RecordingDownloadsRepository()
        val controller = FakePlaybackController().apply {
            state.value = LoadableState.Content(PlaybackSnapshot(queue = listOf(TRACK), currentIndex = 0))
        }
        val viewModel = PlayerViewModel(
            controller = controller,
            favoriteDelegate = FavoriteDelegate(FakeFavoritesRepository()),
            queueResolver = DeferredResolver(),
            downloadDelegate = DownloadDelegate(repository, backgroundScope),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        viewModel.toggleCurrentTrackDownload()
        advanceUntilIdle()

        assertEquals(listOf(TRACK), repository.downloadedTracks)
    }

    @AfterTest
    fun resetMainDispatcher() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun publishesSelectedTrackWhileQueueIsResolving() = runTest {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val resolver = DeferredResolver()
        val controller = FakePlaybackController()
        val viewModel = playerViewModel(controller, resolver)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        viewModel.play(listOf(TRACK), startIndex = 0)
        runCurrent()

        assertTrue(viewModel.state.value.content?.isLoading == true)
        assertEquals(TRACK.id, viewModel.state.value.content?.currentTrack?.id)
        assertEquals(TRACK.id, viewModel.state.value.content?.loadingTrackId)

        resolver.result.complete(listOf(TRACK))
        advanceUntilIdle()
        assertEquals(listOf(TRACK), controller.playedTracks)
    }

    @Test
    fun secondTapCancelsPendingStartAndReturnsToPausedState() = runTest {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val resolver = DeferredResolver()
        val controller = FakePlaybackController()
        val viewModel = playerViewModel(controller, resolver)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        viewModel.play(listOf(TRACK), startIndex = 0)
        viewModel.play(listOf(TRACK), startIndex = 0)
        runCurrent()

        assertFalse(viewModel.state.value.content?.isLoading == true)
        assertTrue(controller.playedTracks.isEmpty())
        assertEquals(0, controller.playPauseCalls)
    }

    @Test
    fun forwardsPlaybackModeCommands() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val controller = FakePlaybackController()
        val viewModel = playerViewModel(controller, DeferredResolver())

        viewModel.toggleShuffle()
        viewModel.cycleRepeatMode()

        assertEquals(1, controller.shuffleCalls)
        assertEquals(1, controller.repeatCalls)
    }

    private fun playerViewModel(
        controller: PlaybackController,
        resolver: PlaybackQueueResolver,
    ) = PlayerViewModel(
        controller = controller,
        favoriteDelegate = FavoriteDelegate(FakeFavoritesRepository()),
        queueResolver = resolver,
        downloadDelegate = DownloadDelegate(
            RecordingDownloadsRepository(),
            CoroutineScope(Dispatchers.Unconfined),
        ),
    )

    private class DeferredResolver : PlaybackQueueResolver {
        val result = CompletableDeferred<List<Track>>()
        override suspend fun resolve(tracks: List<Track>): List<Track> = result.await()
    }

    private class FakePlaybackController : PlaybackController {
        override val state = MutableStateFlow<LoadableState<PlaybackSnapshot>>(
            LoadableState.Content(PlaybackSnapshot()),
        )
        var playedTracks = emptyList<Track>()
        var playPauseCalls = 0
        var shuffleCalls = 0
        var repeatCalls = 0

        override fun play(tracks: List<Track>, startIndex: Int) {
            playedTracks = tracks
        }

        override fun play(tracks: List<Track>, startIndex: Int, origin: PlaybackOrigin) {
            playedTracks = tracks
        }

        override fun playPause() {
            playPauseCalls++
        }

        override fun next() = Unit
        override fun previous() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun toggleShuffle() {
            shuffleCalls++
        }
        override fun cycleRepeatMode() {
            repeatCalls++
        }
        override fun stopAndClear() = Unit
    }

    private class FakeFavoritesRepository : FavoritesRepository {
        override val changes = MutableSharedFlow<FavoriteChange>()
        override fun getFavorites(forceRefresh: Boolean): Flow<LoadableState<Favorites>> = emptyFlow()
        override suspend fun setFavorite(target: FavoriteTarget, isFavorite: Boolean) = Result.success(Unit)
        override suspend fun setFavorites(targets: List<FavoriteTarget>, isFavorite: Boolean) = Result.success(Unit)
    }

    private companion object {
        val TRACK = Track(
            id = "track-1",
            title = "Track",
            artist = "Artist",
            albumId = null,
            artistId = null,
            trackNumber = null,
            coverArtUrl = null,
            streamUrl = "https://example.test/track.mp3",
            isFavorite = false,
        )
    }
}
