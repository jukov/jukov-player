package info.jukov.player.feature.playback

import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.feature.playback.data.PersistedPlaybackState
import info.jukov.player.feature.playback.data.PlaybackStore
import info.jukov.player.feature.playback.domain.PlaybackOrigin
import info.jukov.player.feature.track.domain.Track
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVQueuePlayer
import platform.AVFAudio.AVAudioSessionInterruptionOptionShouldResume
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeEnded
import platform.MediaPlayer.MPRemoteCommandHandlerStatusNoSuchContent
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
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

    private fun controller(
        store: PlaybackStore,
        player: AVQueuePlayer = AVQueuePlayer(),
    ) = IosPlaybackController(
        playbackStore = store,
        player = player,
        installSystemIntegrations = false,
        audioSessionActivation = { true },
    )

    private fun saved(queueSize: Int) = PersistedPlaybackState(
        queue = tracks(queueSize),
        currentIndex = 0,
        origin = PlaybackOrigin.TrackList,
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

private class RecordingPlaybackStore(
    saved: PersistedPlaybackState? = null,
) : PlaybackStore {
    var current: PersistedPlaybackState? = saved
        private set

    override fun read(): PersistedPlaybackState? = current

    override fun write(queue: List<Track>, currentIndex: Int, origin: PlaybackOrigin) {
        current = PersistedPlaybackState(queue, currentIndex, origin)
    }

    override fun updateCurrentIndex(currentIndex: Int) {
        current = current?.copy(currentIndex = currentIndex)
    }

    override fun clear() {
        current = null
    }
}
