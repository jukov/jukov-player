package info.jukov.player.feature.track.data

internal fun List<TrackDto>.withSharedAlbumCoverArt(): List<TrackDto> {
    val coverArtByAlbum = mutableMapOf<String, String>()
    run {
        this@withSharedAlbumCoverArt.forEach { track ->
            val albumId = track.albumId ?: return@forEach
            val coverArt = track.coverArt ?: return@forEach
            if (albumId !in coverArtByAlbum) coverArtByAlbum[albumId] = coverArt
        }
    }
    return map { track ->
        val albumCoverArt = track.albumId?.let(coverArtByAlbum::get)
        if (albumCoverArt == null || albumCoverArt == track.coverArt) track
        else track.copy(coverArt = albumCoverArt)
    }
}
