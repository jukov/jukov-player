package info.jukov.player.feature.playback.domain

import info.jukov.player.core.domain.LoadableState
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.playback.data.PlaybackStore
import info.jukov.player.feature.favorite.domain.FavoriteMutator
import kotlinx.coroutines.flow.StateFlow

interface PlaybackController {
    val state: StateFlow<LoadableState<PlaybackSnapshot>>

    fun play(tracks: List<Track>, startIndex: Int = 0)
    fun play(
        tracks: List<Track>,
        startIndex: Int,
        origin: PlaybackOrigin,
    ) = play(tracks, startIndex)
    fun addToQueue(tracks: List<Track>) = Unit
    fun playPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun playAt(index: Int) = Unit
    fun moveQueueItem(fromIndex: Int, toIndex: Int) = Unit
    fun moveQueueItemsToTop(indices: Set<Int>) = Unit
    fun removeQueueItems(indices: Set<Int>) = Unit
    fun toggleShuffle() = Unit
    fun cycleRepeatMode() = Unit
    fun stopAndClear()
}

fun interface PlaybackControllerFactory {
    fun create(
        playbackStore: PlaybackStore,
        favoriteMutator: FavoriteMutator,
    ): PlaybackController
}
