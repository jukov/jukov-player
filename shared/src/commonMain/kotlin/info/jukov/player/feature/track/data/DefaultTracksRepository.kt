package info.jukov.player.feature.track.data

import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.data.cache.*

import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.feature.auth.domain.AuthState
import info.jukov.player.feature.auth.domain.accountKey
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.track.domain.TracksFilter
import info.jukov.player.feature.track.domain.TracksRepository
import info.jukov.player.subsonic.data.SubsonicApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import info.jukov.player.core.domain.AppException
import info.jukov.player.core.domain.Page

class DefaultTracksRepository(
    private val api: TracksApi,
    private val authRepository: AuthRepository,
    private val dao: CacheDao,
    private val policy: LibraryCachePolicy,
    private val client: SubsonicApiClient,
) : TracksRepository {
    override suspend fun getTracksPage(offset: Int, size: Int, forceRefresh: Boolean): Page<Track> {
        val session = (authRepository.authState.value as? AuthState.LoggedIn)?.session
            ?: throw AppException(AppError.AuthenticationRequired)
        val queryKey = CacheKeys.tracksAll()
        val cacheIsFresh = !forceRefresh && policy.isFresh(session, queryKey)
        if (cacheIsFresh) {
            val cached = dao.trackPage(session.accountKey, queryKey, offset, size + 1)
            return Page(
                items = cached.take(size).map { it.toDomain(session, client) },
                hasMore = cached.size > size,
            )
        }
        val page = api.getTracksPage(session, offset, size)
        dao.storeTrackPage(
            accountKey = session.accountKey,
            queryKey = queryKey,
            items = page.items.map { it.toEntity(session.accountKey) },
            offset = offset,
            isLastPage = !page.hasMore,
            now = Clock.System.now().toEpochMilliseconds(),
        )
        return page
    }

    override fun getTracks(filter: TracksFilter, forceRefresh: Boolean): Flow<LoadableState<List<Track>>> {
        val session = (authRepository.authState.value as? AuthState.LoggedIn)?.session
            ?: return flowOf(LoadableState.Failure(AppError.AuthenticationRequired))
        val queryKey = when (filter) {
            TracksFilter.All -> CacheKeys.tracksAll()
            is TracksFilter.ByArtist -> CacheKeys.artistTracks(filter.artistId)
            is TracksFilter.ByAlbum -> CacheKeys.albumTracks(filter.albumId)
        }
        return cachedLoadableFlow(
            session, queryKey, forceRefresh, AppError.TracksLoadFailed, policy, dao,
            observe = { dao.observeTracks(session.accountKey, queryKey).map { items -> items.map { it.toDomain(session, client) } } },
            refresh = { dao.replaceTracks(session.accountKey, queryKey, api.getTracks(session, filter).map { it.toEntity(session.accountKey) }, Clock.System.now().toEpochMilliseconds()) },
        )
    }
}
