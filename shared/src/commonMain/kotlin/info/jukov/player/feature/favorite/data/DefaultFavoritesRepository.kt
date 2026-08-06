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
import info.jukov.player.core.data.cache.*
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.feature.auth.domain.accountKey
import info.jukov.player.subsonic.data.SubsonicApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class DefaultFavoritesRepository(
    private val api: FavoritesApi,
    private val authRepository: AuthRepository,
    private val dao: CacheDao? = null,
    private val policy: LibraryCachePolicy? = null,
    private val client: SubsonicApiClient? = null,
) : FavoritesRepository {
    private val _changes = MutableSharedFlow<FavoriteChange>(extraBufferCapacity = 16)
    override val changes = _changes.asSharedFlow()

    override fun getFavorites(forceRefresh: Boolean): Flow<LoadableState<Favorites>> {
        val session = (authRepository.authState.value as? AuthState.LoggedIn)?.session
            ?: return flowOf(LoadableState.Failure(AppError.AuthenticationRequired))
        val dao = requireNotNull(dao)
        val policy = requireNotNull(policy)
        val client = requireNotNull(client)
        val observe = {
            combine(
                dao.observeTracks(session.accountKey, CacheKeys.FAVORITES),
                dao.observeAlbums(session.accountKey, CacheKeys.FAVORITES),
                dao.observeArtists(session.accountKey, CacheKeys.FAVORITES),
            ) { tracks, albums, artists ->
                Favorites(
                    tracks.map { it.toDomain(session, client) },
                    albums.map { it.toDomain(session, client) },
                    artists.map { it.toDomain() },
                )
            }
        }
        return cachedLoadableFlow(
            session, CacheKeys.FAVORITES, forceRefresh, AppError.FavoritesLoadFailed, policy, dao, observe,
            refresh = {
                val favorites = api.getFavorites(session)
                dao.replaceFavorites(
                    session.accountKey,
                    favorites.artists.map { it.toEntity(session.accountKey) },
                    favorites.albums.map { it.toEntity(session.accountKey) },
                    favorites.tracks.map { it.toEntity(session.accountKey) },
                    Clock.System.now().toEpochMilliseconds(),
                )
            },
        )
    }

    override suspend fun setFavorite(target: FavoriteTarget, isFavorite: Boolean): Result<Unit> =
        withSession { session -> api.setFavorite(session, target, isFavorite) }
            .onSuccess {
                val session = (authRepository.authState.value as AuthState.LoggedIn).session
                val type = when (target) {
                    is FavoriteTarget.Artist -> CacheItemType.ARTIST
                    is FavoriteTarget.Album -> CacheItemType.ALBUM
                    is FavoriteTarget.Track -> CacheItemType.TRACK
                }
                dao?.setFavorite(session.accountKey, type, target.id, isFavorite)
                _changes.emit(FavoriteChange(target, isFavorite))
            }

    private suspend fun <T> withSession(block: suspend (AuthSession) -> T): Result<T> = runCatching {
        val session = (authRepository.authState.value as? AuthState.LoggedIn)?.session
            ?: throw AppException(AppError.AuthenticationRequired)
        block(session)
    }
}
