package info.jukov.player.feature.album.domain

data class Album(
    val id: String,
    val name: String,
    val artist: String,
    val artistId: String?,
    val coverArtId: String? = null,
    val coverArtUrl: String?,
    val isFavorite: Boolean = false,
)
