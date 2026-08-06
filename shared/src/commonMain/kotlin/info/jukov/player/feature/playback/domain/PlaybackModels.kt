package info.jukov.player.feature.playback.domain

import info.jukov.player.feature.track.domain.Track

data class PlaybackSnapshot(
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isPlaying: Boolean = false,
) {
    val currentTrack: Track? get() = queue.getOrNull(currentIndex)
    val hasPrevious: Boolean get() = currentIndex > 0 || positionMs > 0
    val hasNext: Boolean get() = currentIndex in 0..<queue.lastIndex
}
