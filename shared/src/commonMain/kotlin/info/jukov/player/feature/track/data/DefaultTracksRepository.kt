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

class DefaultTracksRepository(
    private val api: TracksApi,
    private val authRepository: AuthRepository,
    private val dao: CacheDao,
    private val policy: LibraryCachePolicy,
    private val client: SubsonicApiClient,
) : TracksRepository {
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
