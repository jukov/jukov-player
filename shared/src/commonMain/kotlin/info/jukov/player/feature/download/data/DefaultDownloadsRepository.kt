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
    dao: CacheDao,
    tracksApi: TracksApi,
    platform: OfflinePlatform,
    client: SubsonicApiClient,
) : DownloadsRepository {
    private val library = OfflineLibraryDataSource(dao, OfflineLibraryBuilder(platform, client))
    private val mutations = DownloadMutations(
        dao = dao,
        tracksApi = tracksApi,
        platform = platform,
        session = ::session,
    )
    private val reconciler = DownloadReconciler(dao, platform)
    private val localMedia = OfflineLocalMediaResolver(dao, platform, ::accountKey)

    override fun observeLibrary(): Flow<OfflineLibrary> = authRepository.authState.flatMapLatest { state ->
        val session = (state as? AuthState.LoggedIn)?.session
            ?: return@flatMapLatest flowOf(OfflineLibrary())
        library.observe(session, query = null)
    }

    override fun searchLibrary(query: String): Flow<OfflineLibrary> =
        authRepository.authState.flatMapLatest { state ->
            val session = (state as? AuthState.LoggedIn)?.session
                ?: return@flatMapLatest flowOf(OfflineLibrary())
            library.observe(session, query.trim())
        }

    override fun observeTrackStatuses(): Flow<Map<String, DownloadStatus>> =
        authRepository.authState.flatMapLatest { state ->
            val accountKey = (state as? AuthState.LoggedIn)?.session?.accountKey
                ?: return@flatMapLatest flowOf(emptyMap())
            library.observeTrackStatuses(accountKey)
        }

    override fun observeAlbumTracks(albumId: String): Flow<List<OfflineTrack>> =
        observeLibrary().map { offlineLibrary ->
            offlineLibrary.albums.firstOrNull { it.album.id == albumId }?.tracks.orEmpty()
        }

    override fun observeFailureSummary(): Flow<DownloadFailureSummary> =
        authRepository.authState.flatMapLatest { state ->
            val accountKey = (state as? AuthState.LoggedIn)?.session?.accountKey
                ?: return@flatMapLatest flowOf(DownloadFailureSummary())
            library.observeFailureSummary(accountKey)
        }

    override suspend fun downloadTrack(track: Track) = mutations.downloadTrack(track)

    override suspend fun downloadAlbum(album: Album) = mutations.downloadAlbum(album)

    override suspend fun cancelTrack(trackId: String) = mutations.removeTracks(listOf(trackId))

    override suspend fun removeTracks(trackIds: List<String>) = mutations.removeTracks(trackIds)

    override suspend fun cancelAlbum(albumId: String) = mutations.cancelAlbum(albumId)

    override suspend fun retryTrack(trackId: String) = mutations.retryTrack(trackId)

    override suspend fun retryAllFailed() = mutations.retryAllFailed()

    override suspend fun clearCurrentAccount() = mutations.clearCurrentAccount()

    override suspend fun reconcile() {
        mutations.runSerialized {
            val key = accountKey() ?: return@runSerialized
            reconciler.reconcile(key)
        }
    }

    override suspend fun localTrackUri(trackId: String): String? = localMedia.localTrackUri(trackId)

    override suspend fun localTrackUris(trackIds: List<String>): Map<String, String> =
        localMedia.localTrackUris(trackIds)

    override suspend fun localArtworkUri(coverArtId: String?): String? =
        localMedia.localArtworkUri(coverArtId)

    override suspend fun localArtworkUris(coverArtIds: List<String>): Map<String, String> =
        localMedia.localArtworkUris(coverArtIds)

    private fun session() = (authRepository.authState.value as? AuthState.LoggedIn)?.session

    private fun accountKey() = session()?.accountKey
}
