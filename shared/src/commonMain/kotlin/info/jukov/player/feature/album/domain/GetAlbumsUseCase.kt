package info.jukov.player.feature.album.domain

class GetAlbumsUseCase(private val repository: AlbumsRepository) {
    operator fun invoke(artistId: String?, forceRefresh: Boolean = false) =
        repository.getAlbums(artistId, forceRefresh)

    suspend fun page(offset: Int, size: Int, sort: info.jukov.player.core.domain.SortOption<info.jukov.player.core.domain.AlbumSortCriterion>, forceRefresh: Boolean = false) =
        repository.getAlbumsPage(offset, size, sort, forceRefresh)
}
