package info.jukov.player.artist.data

import info.jukov.player.artist.domain.Artist
import kotlinx.serialization.Serializable

@Serializable
internal data class ArtistDto(
    val id: String,
    val name: String,
    val coverArt: String? = null,
    val albumCount: Int = 0,
) {
    fun toDomain() = Artist(id, name, albumCount, coverArt)
}
