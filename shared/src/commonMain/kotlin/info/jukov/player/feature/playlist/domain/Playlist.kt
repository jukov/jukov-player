package info.jukov.player.feature.playlist.domain

import info.jukov.player.feature.track.domain.Track

data class Playlist(
    val id: String,
    val name: String,
    val owner: String? = null,
    val songCount: Int = 0,
    val durationSeconds: Long = 0,
    val readOnly: Boolean = false,
    val isPublic: Boolean = false,
    val tracks: List<Track> = emptyList(),
) {
    fun isEditableBy(username: String): Boolean =
        !readOnly && (owner == null || owner.equals(username, ignoreCase = true))
}
