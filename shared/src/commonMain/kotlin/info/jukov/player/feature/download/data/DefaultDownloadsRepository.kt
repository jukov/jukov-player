package info.jukov.player.feature.download.data

import info.jukov.player.core.data.cache.CacheDao
import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.feature.auth.domain.AuthState
import info.jukov.player.feature.auth.domain.accountKey
import info.jukov.player.feature.download.domain.DownloadFailureSummary
import info.jukov.player.feature.download.domain.DownloadStatus
import info.jukov.player.feature.download.domain.DownloadsRepository
import info.jukov.player.feature.download.domain.OfflineLibrary
import info.jukov.player.feature.download.domain.OfflinePlatform
import info.jukov.player.feature.download.domain.OfflineTrack
import info.jukov.player.feature.track.data.TracksApi
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.subsonic.data.SubsonicApiClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultDownloadsRepository(
    private val authRepository: AuthRepository,
    private val dao: CacheDao,
    private val tracksApi: TracksApi,
    private val platform: OfflinePlatform,
    private val client: SubsonicApiClient,
) : DownloadsRepository {
    private val mutationCoordinator = DownloadMutationCoordinator()

    override fun observeLibrary(): Flow<OfflineLibrary> = authRepository.authState.flatMapLatest { state ->
        val session = (state as? AuthState.LoggedIn)?.session
            ?: return@flatMapLatest flowOf(OfflineLibrary())
        observeOfflineLibrary(dao, platform, client, session, query = null)
    }

    override fun searchLibrary(query: String): Flow<OfflineLibrary> =
        authRepository.authState.flatMapLatest { state ->
            val session = (state as? AuthState.LoggedIn)?.session
                ?: return@flatMapLatest flowOf(OfflineLibrary())
            observeOfflineLibrary(dao, platform, client, session, query.trim())
        }

    override fun observeTrackStatuses(): Flow<Map<String, DownloadStatus>> =
        authRepository.authState.flatMapLatest { state ->
            val accountKey = (state as? AuthState.LoggedIn)?.session?.accountKey
                ?: return@flatMapLatest flowOf(emptyMap())
            observeDownloadStatuses(dao, accountKey)
        }

    override fun observeAlbumTracks(albumId: String): Flow<List<OfflineTrack>> =
        observeLibrary().map { offlineLibrary ->
            offlineLibrary.albums.firstOrNull { it.album.id == albumId }?.tracks.orEmpty()
        }

    override fun observeFailureSummary(): Flow<DownloadFailureSummary> =
        authRepository.authState.flatMapLatest { state ->
            val accountKey = (state as? AuthState.LoggedIn)?.session?.accountKey
                ?: return@flatMapLatest flowOf(DownloadFailureSummary())
            observeDownloadFailureSummary(dao, accountKey)
        }

    override suspend fun downloadTrack(track: Track) = downloadTrackMutation(
        coordinator = mutationCoordinator,
        accountKey = ::accountKey,
        dao = dao,
        platform = platform,
        track = track,
    )

    override suspend fun downloadAlbum(album: Album) = downloadAlbumMutation(
        coordinator = mutationCoordinator,
        session = ::session,
        dao = dao,
        tracksApi = tracksApi,
        platform = platform,
        album = album,
    )

    override suspend fun cancelTrack(trackId: String) = removeTracksMutation(
        coordinator = mutationCoordinator,
        accountKey = ::accountKey,
        dao = dao,
        platform = platform,
        trackIds = listOf(trackId),
    )

    override suspend fun removeTracks(trackIds: List<String>) = removeTracksMutation(
        coordinator = mutationCoordinator,
        accountKey = ::accountKey,
        dao = dao,
        platform = platform,
        trackIds = trackIds,
    )

    override suspend fun cancelAlbum(albumId: String) = cancelAlbumMutation(
        coordinator = mutationCoordinator,
        accountKey = ::accountKey,
        dao = dao,
        platform = platform,
        albumId = albumId,
    )

    override suspend fun retryTrack(trackId: String) = retryTrackMutation(
        coordinator = mutationCoordinator,
        accountKey = ::accountKey,
        dao = dao,
        platform = platform,
        trackId = trackId,
    )

    override suspend fun retryAllFailed() = retryAllFailedMutation(
        coordinator = mutationCoordinator,
        accountKey = ::accountKey,
        dao = dao,
        platform = platform,
    )

    override suspend fun clearCurrentAccount() = clearCurrentAccountMutation(
        coordinator = mutationCoordinator,
        accountKey = ::accountKey,
        dao = dao,
        platform = platform,
    )

    override suspend fun reconcile() {
        mutationCoordinator.run {
            val key = accountKey() ?: return@run
            reconcileDownloads(dao, platform, key)
        }
    }

    override suspend fun localTrackUri(trackId: String): String? = localTrackUris(listOf(trackId))[trackId]

    override suspend fun localTrackUris(trackIds: List<String>): Map<String, String> =
        resolveLocalTrackUris(dao, platform, accountKey(), trackIds)

    override suspend fun localArtworkUri(coverArtId: String?): String? {
        val id = coverArtId ?: return null
        return localArtworkUris(listOf(id))[id]
    }

    override suspend fun localArtworkUris(coverArtIds: List<String>): Map<String, String> =
        resolveLocalArtworkUris(dao, platform, accountKey(), coverArtIds)

    private fun session() = (authRepository.authState.value as? AuthState.LoggedIn)?.session

    private fun accountKey() = session()?.accountKey
}
