package info.jukov.player.artist.domain

class GetArtistsUseCase(private val repository: ArtistsRepository) {
    suspend operator fun invoke(): Result<List<Artist>> = repository.getArtists()
}
