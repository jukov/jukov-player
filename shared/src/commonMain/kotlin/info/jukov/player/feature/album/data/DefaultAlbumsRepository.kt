package info.jukov.player.feature.album.data

import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.AppException

import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.album.domain.AlbumsRepository
import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.feature.auth.domain.AuthState

class DefaultAlbumsRepository(
    private val api: AlbumsApi,
    private val authRepository: AuthRepository,
) : AlbumsRepository {
    override suspend fun getAlbums(artistId: String?): Result<List<Album>> = runCatching {
        val session = (authRepository.authState.value as? AuthState.LoggedIn)?.session
            ?: throw AppException(AppError.AuthenticationRequired)
        api.getAlbums(session, artistId)
    }
}
