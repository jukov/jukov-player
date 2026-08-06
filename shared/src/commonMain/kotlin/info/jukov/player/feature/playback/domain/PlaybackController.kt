package info.jukov.player.feature.playback.domain

import info.jukov.player.core.presentation.LoadableState
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.playback.data.PlaybackStore
import kotlinx.coroutines.flow.StateFlow

interface PlaybackController {
    val state: StateFlow<LoadableState<PlaybackSnapshot>>

    fun play(tracks: List<Track>, startIndex: Int = 0)
    fun playPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun stopAndClear()
}

fun interface PlaybackControllerFactory {
    fun create(playbackStore: PlaybackStore): PlaybackController
}
