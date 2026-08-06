package info.jukov.player.feature.artist.domain

data class Artist(
    val id: String,
    val name: String,
    val albumCount: Int,
    val coverArtId: String?,
    val isFavorite: Boolean = false,
)
