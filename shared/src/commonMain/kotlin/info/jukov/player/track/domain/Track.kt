package info.jukov.player.track.domain

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val albumId: String?,
    val artistId: String?,
    val trackNumber: Int?,
    val coverArtUrl: String?,
    val isStarred: Boolean,
)
