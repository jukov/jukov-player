package info.jukov.player.track.domain

interface TracksRepository {
    suspend fun getTracks(filter: TracksFilter): Result<List<Track>>
}
