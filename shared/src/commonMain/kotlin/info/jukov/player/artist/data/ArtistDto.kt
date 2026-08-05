package info.jukov.player.artist.data

import info.jukov.player.artist.domain.Artist
import kotlinx.serialization.Serializable

@Serializable
internal data class ArtistDto(
    val id: String,
    val name: String,
    val coverArt: String? = null,
    val albumCount: Int = 0,
    val starred: String? = null,
) {
    fun toDomain() = Artist(
        id = id,
        name = name,
        albumCount = albumCount,
        coverArtId = coverArt,
        isFavorite = starred != null,
    )
}
