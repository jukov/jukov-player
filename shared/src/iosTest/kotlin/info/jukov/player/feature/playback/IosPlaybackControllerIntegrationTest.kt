package info.jukov.player.feature.playback

import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.feature.favorite.domain.FavoriteChange
import info.jukov.player.feature.favorite.domain.FavoriteMutator
import info.jukov.player.feature.playback.data.PersistedPlaybackState
import info.jukov.player.feature.playback.data.PlaybackStore
import info.jukov.player.feature.playback.domain.PlaybackOrigin
import info.jukov.player.feature.playback.domain.RepeatMode
import info.jukov.player.feature.track.domain.Track
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import platform.AVFAudio.AVAudioSessionInterruptionOptionShouldResume
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeEnded
import platform.AVFoundation.AVQueuePlayer
import platform.AVFoundation.isPlayable
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.MediaPlayer.MPRemoteCommandHandlerStatusNoSuchContent
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosPlaybackControllerIntegrationTest {
    @Test
    fun playerItemCompletionAdvancesQueueAndPersistsIndex() {
        val store = RecordingPlaybackStore(saved(queueSize = 2))
        val controller = controller(store)

        controller.handleCurrentItemEnded()

        assertEquals(1, controller.state.value.content?.currentIndex)
        assertEquals(1, store.current?.currentIndex)
    }

    @Test
    fun repeatOneRestartsCurrentItem() {
        val store = RecordingPlaybackStore(saved(queueSize = 2, repeatMode = RepeatMode.One))
        val controller = controller(store)

        controller.handleCurrentItemEnded()

        assertEquals(0, controller.state.value.content?.currentIndex)
        assertTrue(controller.state.value.content?.isPlaying == true)
    }

    @Test
    fun repeatAllWrapsAtEndOfQueue() {
        val store = RecordingPlaybackStore(saved(queueSize = 2, repeatMode = RepeatMode.All).copy(currentIndex = 1))
        val controller = controller(store)

        controller.handleCurrentItemEnded()

        assertEquals(0, controller.state.value.content?.currentIndex)
        assertTrue(controller.state.value.content?.isPlaying == true)
    }

    @Test
    fun shuffleKeepsQueueOrderedAndSelectsAnotherTrackOnNext() {
        val ordered = tracks(4)
        val controller = controller(RecordingPlaybackStore(saved(queueSize = ordered.size)))

        controller.toggleShuffle()
        controller.next()

        val snapshot = requireNotNull(controller.state.value.content)
        assertEquals(ordered, snapshot.queue)
        assertTrue(snapshot.isShuffleEnabled)
        assertTrue(snapshot.currentIndex in 1..ordered.lastIndex)
        assertFalse(snapshot.isPlaying)
    }

    @Test
    fun disablingShuffleDefersOrderedQueueRestoreUntilTrackTransition() {
        val player = AVQueuePlayer()
        val controller = controller(
            RecordingPlaybackStore(saved(queueSize = 2, repeatMode = RepeatMode.All)),
            player,
        )
        controller.toggleShuffle()
        controller.next()

        controller.toggleShuffle()
        controller.handleCurrentItemEnded()

        val snapshot = requireNotNull(controller.state.value.content)
        assertFalse(snapshot.isShuffleEnabled)
        assertEquals(0, snapshot.currentIndex)
        assertTrue(snapshot.isPlaying)
    }

    @Test
    fun periodicUpdateSynchronizesAnAutomaticallyAdvancedQueueItem() {
        val store = RecordingPlaybackStore(saved(queueSize = 3))
        val player = AVQueuePlayer()
        val controller = controller(store, player)

        player.advanceToNextItem()
        controller.handlePeriodicTimeUpdate()

        assertEquals(1, controller.state.value.content?.currentIndex)
        assertEquals("track-1", controller.state.value.content?.currentTrack?.id)
        assertEquals(1, store.current?.currentIndex)
    }

    @Test
    fun failedCurrentItemPublishesPlaybackFailure() {
        val controller = controller(RecordingPlaybackStore(saved(queueSize = 1)))

        controller.handleCurrentItemFailed()

        assertFalse(controller.state.value.content?.isPlaying ?: true)
        val state = assertIs<LoadableState.Failure<*>>(controller.state.value)
        assertEquals(AppError.PlaybackFailed, state.error)
    }

    @Test
    fun localAudioUsesContentTypeWhenFileExtensionIsGeneric() {
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory())
            .URLByAppendingPathComponent("offline-playback-test.audio")!!
        val data = NSData.create(
            base64EncodedString = WAV_FIXTURE_BASE64,
            options = 0u,
        )!!
        assertTrue(data.writeToURL(url, atomically = true))

        try {
            val asset = createIosPlaybackAsset(url, contentType = "audio/wav")!!

            assertTrue(asset.isPlayable())
        } finally {
            NSFileManager.defaultManager.removeItemAtURL(url, error = null)
        }
    }

    @Test
    fun contentTypeOverrideIsLimitedToLocalFiles() {
        val remoteUrl = NSURL.URLWithString("https://example.invalid/track.mp3")!!
        val localUrl = NSURL.fileURLWithPath("/tmp/track.audio")

        assertNull(createIosPlaybackAsset(remoteUrl, contentType = "audio/mpeg"))
        assertNull(createIosPlaybackAsset(localUrl, contentType = null))
    }

    @Test
    fun interruptionPausesAndResumesPlaybackIntent() {
        val controller = controller(RecordingPlaybackStore())
        controller.play(tracks(1), startIndex = 0)
        assertTrue(controller.state.value.content?.isPlaying == true)

        controller.handleAudioInterruption(AVAudioSessionInterruptionTypeBegan, options = 0u)
        assertFalse(controller.state.value.content?.isPlaying ?: true)

        controller.handleAudioInterruption(
            AVAudioSessionInterruptionTypeEnded,
            options = AVAudioSessionInterruptionOptionShouldResume,
        )
        assertTrue(controller.state.value.content?.isPlaying == true)
    }

    @Test
    fun remoteCommandsControlPlaybackAndRespectQueueBounds() {
        val controller = controller(RecordingPlaybackStore(saved(queueSize = 2)))

        assertEquals(MPRemoteCommandHandlerStatusSuccess, controller.remotePlay())
        assertTrue(controller.state.value.content?.isPlaying == true)
        assertEquals(MPRemoteCommandHandlerStatusSuccess, controller.remotePause())
        assertFalse(controller.state.value.content?.isPlaying ?: true)

        assertEquals(MPRemoteCommandHandlerStatusSuccess, controller.remoteNext())
        assertEquals(1, controller.state.value.content?.currentIndex)
        assertEquals(MPRemoteCommandHandlerStatusNoSuchContent, controller.remoteNext())
        assertEquals(MPRemoteCommandHandlerStatusSuccess, controller.remotePrevious())
        assertEquals(0, controller.state.value.content?.currentIndex)
    }

    @Test
    fun remoteFavoriteUsesFeedbackDirectionAndPersistsState() {
        val store = RecordingPlaybackStore(saved(queueSize = 1))
        val controller = controller(store)

        assertEquals(MPRemoteCommandHandlerStatusSuccess, controller.remoteFavorite(isNegative = false))
        assertTrue(controller.state.value.content?.currentTrack?.isFavorite == true)
        assertTrue(store.current?.queue?.single()?.isFavorite == true)

        assertEquals(MPRemoteCommandHandlerStatusSuccess, controller.remoteFavorite(isNegative = true))
        assertFalse(controller.state.value.content?.currentTrack?.isFavorite ?: true)
        assertFalse(store.current?.queue?.single()?.isFavorite ?: true)
    }

    @Test
    fun remoteFavoriteRejectsMissingCurrentTrack() {
        val controller = controller(RecordingPlaybackStore())

        assertEquals(
            MPRemoteCommandHandlerStatusNoSuchContent,
            controller.remoteFavorite(isNegative = false),
        )
    }

    private fun controller(
        store: PlaybackStore,
        player: AVQueuePlayer = AVQueuePlayer(),
    ) = IosPlaybackController(
        playbackStore = store,
        favoriteMutator = RecordingFavoriteMutator(),
        player = player,
        installSystemIntegrations = false,
        audioSessionActivation = { true },
    )

    private fun saved(
        queueSize: Int,
        repeatMode: RepeatMode = RepeatMode.Off,
    ) = PersistedPlaybackState(
        queue = tracks(queueSize),
        currentIndex = 0,
        origin = PlaybackOrigin.TrackList,
        repeatMode = repeatMode,
    )

    private fun tracks(count: Int): List<Track> = List(count) { index ->
        Track(
            id = "track-$index",
            title = "Track $index",
            artist = "Artist",
            albumId = null,
            artistId = null,
            trackNumber = index + 1,
            coverArtUrl = null,
            streamUrl = "https://example.invalid/track-$index.mp3",
            durationMs = 10_000,
            isFavorite = false,
        )
    }
}

private const val WAV_FIXTURE_BASE64 =
    "UklGRmYDAABXQVZFZm10IBAAAAABAAEAQB8AAIA+AAACABAATElTVBoAAABJTkZPSVNGVA0AAABMYXZmNjIuMy4xMDAAAGRhdGEgAwAAIgE/BUgKtg3BD8QPDA6PCucFfwAR+zH2f/Jj8CHwv/EN9aX5/v52BGcJOw1+D+0Peg5QC9AGggEG/AP3D/Ok8AnwUvFX9Lz4/v19A5IIpAw3D/0P4A4ADLUHgQIB/dz3rfPz8AHw8/Cs89v3Af2AArUHAAzfDv0PNw+kDJMIfgMA/r34V/RS8Qnwo/AP8wL3BfyBAc8GTwt5Du0Pfw87DWcJdwT//qb5DfW/8SHwY/B+8jL2DvuAAOMFlAoEDs0Ptw/FDTMKbAUAAJX6zvU78krwM/D78Wz1HPp///EEzgmCDZwP3w9BDvQKWwYCAYr7mfbF8oLwE/CG8bD0MPl+/voD/gjxDFwP9w+vDqkLQwcCAoP8bvdc88nwA/Ag8QD0S/h//f8CJAhTDA0P/w8ND1MMJQgAA4D9TPgA9CHxA/DJ8FzzbfeD/AECQgepC64O9w9cD/EM/gj7A3/+Mfmw9IfxE/CC8MXymfaJ+wEBWgbzCkAO3w+cD4INzgnyBID/Hfpt9fzxM/BJ8DvyzvWU+v//awUyCsUNtw/NDwUOlArkBYEAD/sz9n7yZPAh8L/xDPWl+f/+dgRnCTsNfg/tD3kOUAvQBoIBBvwC9w/zpPAJ8FLxVvS9+P79fQOSCKQMNw/9D+AOAAy1B4ECAf3b963z8/AB8PPwrfPb9wD9gAK1B/8L3w79DzcPpAySCH4D//29+Ff0UvEJ8KPwD/MC9wX8gQHPBlALeQ7tD38POw1oCXcE//6m+Q31v/Eh8GPwfvIy9g77gADjBZQKBQ7ND7YPxQ0yCmwFAQCV+s71PPJJ8DPw+/Fs9Rz6f//xBM4JgQ2cD98PQQ70ClsGAgGK+5r2xfKC8BPwh/Gw9DD5fv76A/0I8QxdD/cPrg6pC0MHAQKD/G73XPPJ8APwIPEA9Ev4f/3/AiQIUwwND/8PDQ9TDCUIAAOA/Uv4APQg8QPwyfBc8273gvwBAkMHqQuuDvcPXQ/xDP4I+wN//jH5sfSH8RPwgfDF8pn2ifsBAVoG8wpBDt8PnQ+BDc8J8ASC/xn6cvXz8T/wO/BQ8q311vo="

private class RecordingFavoriteMutator : FavoriteMutator {
    override val pending: StateFlow<Set<String>> = MutableStateFlow(emptySet())
    override val changes: SharedFlow<FavoriteChange> = MutableSharedFlow()

    override suspend fun set(
        track: Track,
        isFavorite: Boolean,
        updateFavorite: (Boolean) -> Unit,
    ) {
        updateFavorite(isFavorite)
    }
}

private class RecordingPlaybackStore(
    saved: PersistedPlaybackState? = null,
) : PlaybackStore {
    var current: PersistedPlaybackState? = saved
        private set

    override fun read(): PersistedPlaybackState? = current

    override fun write(queue: List<Track>, currentIndex: Int, origin: PlaybackOrigin) {
        current = PersistedPlaybackState(queue, currentIndex, origin)
    }

    override fun writePlaybackState(state: PersistedPlaybackState) {
        current = state
    }

    override fun updateCurrentIndex(currentIndex: Int) {
        current = current?.copy(currentIndex = currentIndex)
    }

    override fun clear() {
        current = null
    }
}
