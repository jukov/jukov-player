package info.jukov.player.feature.playback.domain

import info.jukov.player.feature.track.domain.Track
import kotlinx.serialization.Serializable

@Serializable
sealed interface PlaybackOrigin {
    @Serializable
    data object TrackList : PlaybackOrigin

    @Serializable
    data class Album(val albumId: String) : PlaybackOrigin

    @Serializable
    data class Artist(val artistId: String) : PlaybackOrigin
}

data class PlaybackSnapshot(
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isPlaying: Boolean = false,
    val origin: PlaybackOrigin = PlaybackOrigin.TrackList,
) {
    val currentTrack: Track? get() = queue.getOrNull(currentIndex)
    val hasPrevious: Boolean get() = currentIndex > 0 || positionMs > 0
    val hasNext: Boolean get() = currentIndex in 0..<queue.lastIndex
}
