package info.jukov.player.album.data

import kotlinx.serialization.Serializable

@Serializable
internal data class AlbumDto(
    val id: String,
    val name: String,
    val artist: String = "",
    val artistId: String? = null,
    val coverArt: String? = null,
    val starred: String? = null,
)
