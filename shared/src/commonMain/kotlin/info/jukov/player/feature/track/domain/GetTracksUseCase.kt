package info.jukov.player.feature.track.domain

class GetTracksUseCase(private val repository: TracksRepository) {
    suspend operator fun invoke(filter: TracksFilter): Result<List<Track>> =
        repository.getTracks(filter)
}
