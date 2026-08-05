package info.jukov.player.album.domain

class GetAlbumsUseCase(private val repository: AlbumsRepository) {
    suspend operator fun invoke(artistId: String?): Result<List<Album>> =
        repository.getAlbums(artistId)
}
