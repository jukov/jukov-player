package info.jukov.player.favorite.domain

import info.jukov.player.album.domain.Album
import info.jukov.player.artist.domain.Artist
import info.jukov.player.track.domain.Track

data class Favorites(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
)

sealed interface FavoriteTarget {
    val id: String

    data class Track(override val id: String) : FavoriteTarget
    data class Album(override val id: String) : FavoriteTarget
    data class Artist(override val id: String) : FavoriteTarget
}

data class FavoriteChange(val target: FavoriteTarget, val isFavorite: Boolean)
