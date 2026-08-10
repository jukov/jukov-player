package info.jukov.player.feature.playback.domain

import info.jukov.player.feature.playback.data.PersistedPlaybackState
import info.jukov.player.feature.playback.data.PlaybackStore

fun PlaybackStore.updateTrackFavorite(trackId: String, isFavorite: Boolean) {
    val saved = read() ?: return
    val updatedQueue = saved.queue.map { track ->
        if (track.id == trackId) {
            track.copy(isFavorite = isFavorite)
        } else {
            track
        }
    }
    if (updatedQueue != saved.queue) {
        write(updatedQueue, saved.currentIndex, saved.origin)
    }
}

fun PersistedPlaybackState.trackById(trackId: String?): info.jukov.player.feature.track.domain.Track? =
    trackId?.let { id -> queue.firstOrNull { it.id == id } }
