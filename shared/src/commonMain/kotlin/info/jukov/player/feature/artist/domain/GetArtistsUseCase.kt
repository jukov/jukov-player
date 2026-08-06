package info.jukov.player.feature.artist.domain

class GetArtistsUseCase(private val repository: ArtistsRepository) {
    operator fun invoke(forceRefresh: Boolean = false) = repository.getArtists(forceRefresh)
}
