package info.jukov.player.feature.album.domain

class GetAlbumsUseCase(private val repository: AlbumsRepository) {
    suspend operator fun invoke(artistId: String?): Result<List<Album>> =
        repository.getAlbums(artistId)
}
