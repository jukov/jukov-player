package info.jukov.player.feature.album.domain

interface AlbumsRepository {
    suspend fun getAlbums(artistId: String?): Result<List<Album>>
}
