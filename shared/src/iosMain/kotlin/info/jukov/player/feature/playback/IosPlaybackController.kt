package info.jukov.player.feature.playback

import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.feature.playback.data.PlaybackStore
import info.jukov.player.feature.playback.domain.PlaybackController
import info.jukov.player.feature.playback.domain.PlaybackControllerFactory
import info.jukov.player.feature.playback.domain.PlaybackSnapshot
import info.jukov.player.feature.track.domain.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object IosPlaybackControllerFactory : PlaybackControllerFactory {
    override fun create(playbackStore: PlaybackStore): PlaybackController = IosPlaybackController
}

private object IosPlaybackController : PlaybackController {
    override val state: StateFlow<LoadableState<PlaybackSnapshot>> = MutableStateFlow(
        LoadableState.Failure(AppError.IosPlaybackNotImplemented, PlaybackSnapshot()),
    )

    override fun play(tracks: List<Track>, startIndex: Int) = Unit
    override fun playPause() = Unit
    override fun next() = Unit
    override fun previous() = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun stopAndClear() = Unit
}
