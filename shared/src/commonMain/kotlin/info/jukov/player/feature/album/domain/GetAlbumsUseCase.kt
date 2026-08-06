package info.jukov.player.feature.album.domain

class GetAlbumsUseCase(private val repository: AlbumsRepository) {
    operator fun invoke(artistId: String?, forceRefresh: Boolean = false) =
        repository.getAlbums(artistId, forceRefresh)
}
