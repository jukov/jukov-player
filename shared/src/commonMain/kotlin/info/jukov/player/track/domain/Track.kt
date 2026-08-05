package info.jukov.player.track.domain

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val albumId: String?,
    val artistId: String?,
    val trackNumber: Int?,
    val coverArtId: String? = null,
    val coverArtUrl: String?,
    val streamUrl: String? = null,
    val durationMs: Long = 0,
    val contentType: String? = null,
    val isFavorite: Boolean,
)
