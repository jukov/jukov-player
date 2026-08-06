package info.jukov.player.feature.favorite.data

import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.AppException

import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.feature.auth.domain.AuthState
import info.jukov.player.feature.favorite.domain.FavoriteChange
import info.jukov.player.feature.favorite.domain.FavoriteTarget
import info.jukov.player.feature.favorite.domain.Favorites
import info.jukov.player.feature.favorite.domain.FavoritesRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class DefaultFavoritesRepository(
    private val api: FavoritesApi,
    private val authRepository: AuthRepository,
) : FavoritesRepository {
    private val _changes = MutableSharedFlow<FavoriteChange>(extraBufferCapacity = 16)
    override val changes = _changes.asSharedFlow()

    override suspend fun getFavorites(): Result<Favorites> = withSession { api.getFavorites(it) }

    override suspend fun setFavorite(target: FavoriteTarget, isFavorite: Boolean): Result<Unit> =
        withSession { session -> api.setFavorite(session, target, isFavorite) }
            .onSuccess { _changes.emit(FavoriteChange(target, isFavorite)) }

    private suspend fun <T> withSession(block: suspend (AuthSession) -> T): Result<T> = runCatching {
        val session = (authRepository.authState.value as? AuthState.LoggedIn)?.session
            ?: throw AppException(AppError.AuthenticationRequired)
        block(session)
    }
}
