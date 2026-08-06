package info.jukov.player.feature.track.domain

interface TracksRepository {
    suspend fun getTracks(filter: TracksFilter): Result<List<Track>>
}
