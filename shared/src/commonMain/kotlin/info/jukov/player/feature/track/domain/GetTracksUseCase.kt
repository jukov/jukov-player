package info.jukov.player.feature.track.domain

class GetTracksUseCase(private val repository: TracksRepository) {
    operator fun invoke(filter: TracksFilter, forceRefresh: Boolean = false) =
        repository.getTracks(filter, forceRefresh)
}
