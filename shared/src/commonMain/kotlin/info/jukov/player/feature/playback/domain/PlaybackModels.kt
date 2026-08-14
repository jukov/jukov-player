package info.jukov.player.feature.playback.domain

import info.jukov.player.feature.track.domain.Track
import kotlinx.serialization.Serializable

@Serializable
enum class RepeatMode {
    Off,
    All,
    One,
    ;

    fun next(): RepeatMode = when (this) {
        Off -> All
        All -> One
        One -> Off
    }
}

@Serializable
sealed interface PlaybackOrigin {
    @Serializable
    data object TrackList : PlaybackOrigin

    @Serializable
    data class Album(val albumId: String) : PlaybackOrigin

    @Serializable
    data class Artist(val artistId: String) : PlaybackOrigin

    @Serializable
    data class Playlist(val playlistId: String) : PlaybackOrigin
}

data class PlaybackSnapshot(
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val origin: PlaybackOrigin = PlaybackOrigin.TrackList,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
) {
    val currentTrack: Track? get() = queue.getOrNull(currentIndex)
    val hasPrevious: Boolean get() = currentIndex > 0 || repeatMode == RepeatMode.All && queue.isNotEmpty()
    val hasNext: Boolean get() = currentIndex in 0..<queue.lastIndex || repeatMode == RepeatMode.All && queue.isNotEmpty()
}
