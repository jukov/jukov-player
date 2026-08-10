package info.jukov.player.feature.playback.domain

import info.jukov.player.feature.playback.data.PersistedPlaybackState
import info.jukov.player.feature.playback.data.PlaybackStore
import info.jukov.player.feature.track.domain.Track

fun List<Track>.withTrackFavorite(trackId: String, isFavorite: Boolean): List<Track> =
    map { track ->
        if (track.id == trackId) {
            track.copy(isFavorite = isFavorite)
        } else {
            track
        }
    }

fun PlaybackStore.updateTrackFavorite(trackId: String, isFavorite: Boolean) {
    val saved = read() ?: return
    val updatedQueue = saved.queue.withTrackFavorite(trackId, isFavorite)
    if (updatedQueue != saved.queue) {
        write(updatedQueue, saved.currentIndex, saved.origin)
    }
}

fun PersistedPlaybackState.trackById(trackId: String?): info.jukov.player.feature.track.domain.Track? =
    trackId?.let { id -> queue.firstOrNull { it.id == id } }
