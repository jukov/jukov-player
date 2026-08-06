package info.jukov.player.feature.track.data

import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.feature.auth.domain.AuthState
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.track.domain.TracksFilter
import info.jukov.player.feature.track.domain.TracksRepository

class DefaultTracksRepository(
    private val api: TracksApi,
    private val authRepository: AuthRepository,
) : TracksRepository {
    override suspend fun getTracks(filter: TracksFilter): Result<List<Track>> = runCatching {
        val session = (authRepository.authState.value as? AuthState.LoggedIn)?.session
            ?: error("Сначала войдите в систему")
        api.getTracks(session, filter)
    }
}
