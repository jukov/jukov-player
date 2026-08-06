package info.jukov.player.feature.track.domain

sealed interface TracksFilter {
    data object All : TracksFilter
    data class ByArtist(val artistId: String) : TracksFilter
    data class ByAlbum(val albumId: String) : TracksFilter
}
