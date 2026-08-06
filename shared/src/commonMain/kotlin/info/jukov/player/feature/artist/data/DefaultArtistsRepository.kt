package info.jukov.player.feature.artist.data

import info.jukov.player.feature.artist.domain.Artist
import info.jukov.player.feature.artist.domain.ArtistsRepository
import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.feature.auth.domain.AuthState

class DefaultArtistsRepository(
    private val api: ArtistsApi,
    private val authRepository: AuthRepository,
) : ArtistsRepository {
    override suspend fun getArtists(): Result<List<Artist>> = runCatching {
        val session = (authRepository.authState.value as? AuthState.LoggedIn)?.session
            ?: error("Пользователь не авторизован")
        api.getArtists(session)
    }
}
