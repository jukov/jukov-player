package info.jukov.player.album.domain

interface AlbumsRepository {
    suspend fun getAlbums(artistId: String?): Result<List<Album>>
}
