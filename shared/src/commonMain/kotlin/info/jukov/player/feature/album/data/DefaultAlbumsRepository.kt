package info.jukov.player.feature.album.data

import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.data.cache.*

import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.album.domain.AlbumsRepository
import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.feature.auth.domain.AuthState
import info.jukov.player.feature.auth.domain.accountKey
import info.jukov.player.subsonic.data.SubsonicApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

class DefaultAlbumsRepository(
    private val api: AlbumsApi,
    private val authRepository: AuthRepository,
    private val dao: CacheDao,
    private val policy: LibraryCachePolicy,
    private val client: SubsonicApiClient,
) : AlbumsRepository {
    override fun getAlbums(artistId: String?, forceRefresh: Boolean): Flow<LoadableState<List<Album>>> {
        val session = (authRepository.authState.value as? AuthState.LoggedIn)?.session
            ?: return flowOf(LoadableState.Failure(AppError.AuthenticationRequired))
        val queryKey = artistId?.let(CacheKeys::artistAlbums) ?: CacheKeys.ALBUMS
        return cachedLoadableFlow(
            session, queryKey, forceRefresh, AppError.AlbumsLoadFailed, policy, dao,
            observe = { dao.observeAlbums(session.accountKey, queryKey).map { items -> items.map { it.toDomain(session, client) } } },
            refresh = { dao.replaceAlbums(session.accountKey, queryKey, api.getAlbums(session, artistId).map { it.toEntity(session.accountKey) }, Clock.System.now().toEpochMilliseconds()) },
        )
    }
}
