package info.jukov.player.feature.artist.data

import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.data.cache.CacheDao
import info.jukov.player.core.data.cache.CacheKeys
import info.jukov.player.core.data.cache.LibraryCachePolicy
import info.jukov.player.core.data.cache.cachedLoadableFlow
import info.jukov.player.core.data.cache.toDomain
import info.jukov.player.core.data.cache.toEntity

import info.jukov.player.feature.artist.domain.Artist
import info.jukov.player.feature.artist.domain.ArtistsRepository
import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.feature.auth.domain.AuthState
import info.jukov.player.feature.auth.domain.accountKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

class DefaultArtistsRepository(
    private val api: ArtistsApi,
    private val authRepository: AuthRepository,
    private val dao: CacheDao,
    private val policy: LibraryCachePolicy,
) : ArtistsRepository {
    override fun getArtists(forceRefresh: Boolean): Flow<LoadableState<List<Artist>>> {
        val session = (authRepository.authState.value as? AuthState.LoggedIn)?.session
            ?: return flowOf(LoadableState.Failure(AppError.AuthenticationRequired))
        return cachedLoadableFlow(
            session, CacheKeys.ARTISTS, forceRefresh, AppError.ArtistsLoadFailed, policy, dao,
            observe = { dao.observeArtists(session.accountKey, CacheKeys.ARTISTS).map { items -> items.map { it.toDomain() } } },
            refresh = {
                val artists = api.getArtists(session)
                dao.replaceArtists(session.accountKey, CacheKeys.ARTISTS, artists.map { it.toEntity(session.accountKey) }, Clock.System.now().toEpochMilliseconds())
            },
        )
    }
}
