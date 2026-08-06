package info.jukov.player.feature.artist.domain

class GetArtistsUseCase(private val repository: ArtistsRepository) {
    suspend operator fun invoke(): Result<List<Artist>> = repository.getArtists()
}
