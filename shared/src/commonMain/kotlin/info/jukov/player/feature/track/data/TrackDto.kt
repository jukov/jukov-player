package info.jukov.player.feature.track.data

import kotlinx.serialization.Serializable

@Serializable
internal data class TrackDto(
    val id: String,
    val title: String,
    val artist: String = "",
    val album: String? = null,
    val albumId: String? = null,
    val artistId: String? = null,
    val track: Int? = null,
    val coverArt: String? = null,
    val duration: Int? = null,
    val contentType: String? = null,
    val starred: String? = null,
)
