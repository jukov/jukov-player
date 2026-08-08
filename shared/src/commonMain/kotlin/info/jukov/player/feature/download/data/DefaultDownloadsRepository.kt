package info.jukov.player.feature.download.data

import info.jukov.player.core.data.cache.*
import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.feature.auth.domain.AuthState
import info.jukov.player.feature.auth.domain.accountKey
import info.jukov.player.feature.download.domain.*
import info.jukov.player.feature.track.data.TracksApi
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.track.domain.TracksFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultDownloadsRepository(
    private val authRepository: AuthRepository,
    private val dao: CacheDao,
    private val tracksApi: TracksApi,
    private val platform: OfflinePlatform,
) : DownloadsRepository {
    override fun observeLibrary(): Flow<OfflineLibrary> = authRepository.authState.flatMapLatest { state ->
        val accountKey = (state as? AuthState.LoggedIn)?.session?.accountKey
            ?: return@flatMapLatest flowOf(OfflineLibrary())
        observeLibrary(accountKey, query = null)
    }

    override fun searchLibrary(query: String): Flow<OfflineLibrary> = authRepository.authState.flatMapLatest { state ->
        val accountKey = (state as? AuthState.LoggedIn)?.session?.accountKey
            ?: return@flatMapLatest flowOf(OfflineLibrary())
        observeLibrary(accountKey, query.trim())
    }

    private fun observeLibrary(accountKey: String, query: String?) : Flow<OfflineLibrary> {
        val metadata = combine(
            dao.observeAccountTracks(accountKey),
            dao.observeAccountAlbums(accountKey),
        ) { tracks, albums -> tracks to albums }
        val downloads = if (query == null) {
            dao.observeOfflineTracks(accountKey).map { it to it }
        } else {
            combine(
                dao.observeOfflineTracksSearch(accountKey, query),
                dao.observeOfflineTracks(accountKey),
            ) { visible, all -> visible to all }
        }
        return combine(
            downloads,
            query?.let { dao.observeOfflineAlbumsSearch(accountKey, it) } ?: dao.observeOfflineAlbums(accountKey),
            dao.observeDownloadOwnerships(accountKey),
            dao.observeOfflineArtworks(accountKey),
            metadata,
        ) { (visibleDownloads, allDownloads), offlineAlbums, ownerships, artworks, (tracks, albums) ->
            val tracksById = tracks.associateBy(TrackEntity::id)
            val albumsById = albums.associateBy(AlbumEntity::id)
            val artworkPaths = artworks.filter { it.state == DownloadState.Completed.name }
                .associate { it.coverArtId to it.relativePath }
            val allOfflineTracks = allDownloads.mapNotNull { download ->
                val track = tracksById[download.trackId] ?: return@mapNotNull null
                download.trackId to download.toOfflineTrack(
                    track, accountKey, platform, artworkPaths[track.coverArtId],
                )
            }.toMap()
            val visibleTrackIds = visibleDownloads.mapTo(hashSetOf()) { it.trackId }
            OfflineLibrary(
                tracks = allOfflineTracks.filterKeys { it in visibleTrackIds }.values.toList(),
                albums = offlineAlbums.mapNotNull { offlineAlbum ->
                    val album = albumsById[offlineAlbum.albumId] ?: return@mapNotNull null
                    val albumTracks = ownerships.asSequence()
                        .filter { it.ownerType == OWNER_ALBUM && it.ownerId == offlineAlbum.albumId }
                        .sortedBy { it.position }
                        .mapNotNull { allOfflineTracks[it.trackId] }
                        .toList()
                    OfflineAlbum(
                        album.toDomain(accountKey, platform, artworkPaths[album.coverArtId]),
                        albumTracks,
                        offlineAlbum.trackCount,
                    )
                },
            )
        }
    }

    override fun observeTrackStatuses(): Flow<Map<String, DownloadStatus>> =
        authRepository.authState.flatMapLatest { state ->
            val accountKey = (state as? AuthState.LoggedIn)?.session?.accountKey
                ?: return@flatMapLatest flowOf(emptyMap())
            dao.observeOfflineTracks(accountKey).map { tracks ->
                tracks.associate { it.trackId to it.toStatus() }
            }
        }

    override fun observeAlbumTracks(albumId: String): Flow<List<OfflineTrack>> {
        return observeLibrary().map { library ->
            library.albums.firstOrNull { it.album.id == albumId }?.tracks.orEmpty()
        }
    }

    override suspend fun downloadTrack(track: Track) {
        val key = accountKey() ?: return
        storeTrack(key, track, OWNER_TRACK, track.id, position = 0)
        platform.enqueue(key)
    }

    override suspend fun downloadAlbum(album: Album) {
        val session = session() ?: return
        val tracks = tracksApi.getTracks(session, TracksFilter.ByAlbum(album.id))
        val now = Clock.System.now().toEpochMilliseconds()
        dao.upsertAlbums(listOf(album.toEntity(session.accountKey)))
        dao.upsertTracks(tracks.map { it.toEntity(session.accountKey) })
        dao.upsertOfflineAlbum(
            OfflineAlbumEntity(
                session.accountKey, album.id, tracks.size, now,
            ),
        )
        tracks.forEachIndexed { index, track ->
            storeTrack(session.accountKey, track, OWNER_ALBUM, album.id, index)
        }
        platform.enqueue(session.accountKey)
    }

    override suspend fun cancelTrack(trackId: String) {
        val key = accountKey() ?: return
        platform.cancelTrack(key, trackId)
        dao.deleteTrackOwnerships(key, trackId)
        removeTrackIfUnowned(key, trackId)
    }

    override suspend fun cancelAlbum(albumId: String) {
        val key = accountKey() ?: return
        val tracks = dao.offlineAlbumTracks(key, albumId)
        dao.deleteAlbumOwnerships(key, albumId)
        dao.deleteOfflineAlbum(key, albumId)
        tracks.forEach { track ->
            if (dao.ownershipCount(key, track.trackId) == 0) {
                platform.cancelTrack(key, track.trackId)
                removeTrackIfUnowned(key, track.trackId)
            }
        }
    }

    override suspend fun retryTrack(trackId: String) {
        val key = accountKey() ?: return
        val track = dao.offlineTrack(key, trackId) ?: return
        dao.updateOfflineTrackState(
            key, trackId, DownloadState.Queued.name, track.downloadedBytes,
            track.expectedSize, track.relativePath, null, null,
        )
        platform.enqueue(key)
    }

    override suspend fun clearCurrentAccount() {
        val key = accountKey() ?: return
        platform.cancelAccount(key)
        platform.deleteAccount(key)
        dao.clearOfflineAccount(key)
    }

    override suspend fun reconcile() {
        val key = accountKey() ?: return
        val tracks = dao.allOfflineTracks(key)
        var hasPending = false
        platform.cleanupStaleParts(key, tracks.mapTo(mutableSetOf()) { it.trackId })
        tracks.forEach { track ->
            val path = track.relativePath
            when {
                track.state == DownloadState.Completed.name &&
                    (path == null || !platform.exists(key, path)) -> dao.updateOfflineTrackState(
                        key, track.trackId, DownloadState.Failed.name, 0, track.expectedSize,
                        null, "Local file is missing", null,
                    )
                track.state == DownloadState.Queued.name || track.state == DownloadState.Downloading.name -> {
                    hasPending = true
                }
            }
        }
        if (hasPending) platform.recover(key)
    }

    override suspend fun localTrackUri(trackId: String): String? {
        return localTrackUris(listOf(trackId))[trackId]
    }

    override suspend fun localTrackUris(trackIds: List<String>): Map<String, String> {
        val key = accountKey() ?: return emptyMap()
        if (trackIds.isEmpty()) return emptyMap()
        return dao.offlineTracks(key, trackIds).mapNotNull { item ->
            val path = item.relativePath ?: return@mapNotNull null
            if (item.state == DownloadState.Completed.name && platform.exists(key, path)) {
                item.trackId to platform.fileUri(key, path)
            } else null
        }.toMap()
    }

    override suspend fun localArtworkUri(coverArtId: String?): String? {
        val id = coverArtId ?: return null
        return localArtworkUris(listOf(id))[id]
    }

    override suspend fun localArtworkUris(coverArtIds: List<String>): Map<String, String> {
        val key = accountKey() ?: return emptyMap()
        if (coverArtIds.isEmpty()) return emptyMap()
        return dao.offlineArtworks(key, coverArtIds).mapNotNull { item ->
            val path = item.relativePath ?: return@mapNotNull null
            if (item.state == DownloadState.Completed.name && platform.exists(key, path)) {
                item.coverArtId to platform.fileUri(key, path)
            } else null
        }.toMap()
    }

    private suspend fun storeTrack(
        key: String,
        track: Track,
        ownerType: String,
        ownerId: String,
        position: Int,
    ) {
        val existing = dao.offlineTrack(key, track.id)
        dao.upsertOfflineTrack(
            OfflineTrackEntity(
                key,
                track.id,
                existing?.relativePath,
                existing?.expectedSize,
                existing?.downloadedBytes ?: 0,
                existing?.state ?: DownloadState.Queued.name,
                existing?.error,
                existing?.requestedAtMs ?: Clock.System.now().toEpochMilliseconds(),
                existing?.completedAtMs,
            ),
        )
        dao.upsertDownloadOwnership(
            listOf(DownloadOwnershipEntity(key, ownerType, ownerId, track.id, position)),
        )
    }

    private suspend fun removeTrackIfUnowned(key: String, trackId: String) {
        if (dao.ownershipCount(key, trackId) != 0) return
        val download = dao.offlineTrack(key, trackId) ?: return
        val track = dao.track(key, trackId)
        platform.deleteTrack(key, download.relativePath)
        dao.deleteOfflineTrack(key, trackId)
        track?.coverArtId?.let { coverId ->
            if (dao.artworkReferenceCount(key, coverId) == 0) {
                val artwork = dao.offlineArtwork(key, coverId)
                platform.deleteArtwork(key, artwork?.relativePath)
                dao.deleteOfflineArtwork(key, coverId)
            }
        }
    }

    private fun session() = (authRepository.authState.value as? AuthState.LoggedIn)?.session
    private fun accountKey() = session()?.accountKey

    private companion object {
        const val OWNER_TRACK = "track"
        const val OWNER_ALBUM = "album"
    }
}

private fun OfflineTrackEntity.toStatus() = DownloadStatus(
    DownloadState.valueOf(state), downloadedBytes, expectedSize, error,
)

private fun OfflineTrackEntity.toOfflineTrack(
    metadata: TrackEntity,
    key: String,
    platform: OfflinePlatform,
    artworkPath: String? = null,
) = OfflineTrack(
    track = Track(
        id = trackId, title = metadata.title, artist = metadata.artist,
        album = metadata.album, albumId = metadata.albumId,
        artistId = metadata.artistId, trackNumber = metadata.trackNumber,
        year = metadata.year,
        coverArtId = metadata.coverArtId,
        coverArtUrl = artworkPath?.let { platform.fileUri(key, it) },
        streamUrl = relativePath?.let { platform.fileUri(key, it) },
        durationMs = metadata.durationMs, contentType = metadata.contentType,
        isFavorite = metadata.isFavorite,
    ),
    status = toStatus(),
)

private fun AlbumEntity.toDomain(key: String, platform: OfflinePlatform, artworkPath: String?) = Album(
    id = id, name = name, artist = artist, artistId = artistId, coverArtId = coverArtId,
    coverArtUrl = artworkPath?.let { platform.fileUri(key, it) }, isFavorite = isFavorite,
)
