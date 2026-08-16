package info.jukov.player.feature.download.data

import info.jukov.player.core.data.cache.*
import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.feature.auth.domain.AuthState
import info.jukov.player.feature.auth.domain.accountKey
import info.jukov.player.feature.download.domain.*
import info.jukov.player.feature.track.data.TracksApi
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.track.domain.TracksFilter
import info.jukov.player.subsonic.data.SubsonicApiClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultDownloadsRepository(
    private val authRepository: AuthRepository,
    private val dao: CacheDao,
    private val tracksApi: TracksApi,
    private val platform: OfflinePlatform,
    private val client: SubsonicApiClient,
) : DownloadsRepository {
    private val mutations = DownloadMutationCoordinator()
    override fun observeLibrary(): Flow<OfflineLibrary> = authRepository.authState.flatMapLatest { state ->
        val session = (state as? AuthState.LoggedIn)?.session
            ?: return@flatMapLatest flowOf(OfflineLibrary())
        observeLibrary(session, query = null)
    }

    override fun searchLibrary(query: String): Flow<OfflineLibrary> = authRepository.authState.flatMapLatest { state ->
        val session = (state as? AuthState.LoggedIn)?.session
            ?: return@flatMapLatest flowOf(OfflineLibrary())
        observeLibrary(session, query.trim())
    }

    private fun observeLibrary(session: AuthSession, query: String?) : Flow<OfflineLibrary> {
        val accountKey = session.accountKey
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
                    track, accountKey, platform, client, session, artworkPaths[track.coverArtId],
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
                        album.toDomain(
                            accountKey, platform, client, session, artworkPaths[album.coverArtId],
                        ),
                        albumTracks,
                        offlineAlbum.trackCount,
                        offlineAlbum.requestedAtMs,
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

    override fun observeAlbumStatuses(): Flow<Map<String, DownloadStatus>> =
        authRepository.authState.flatMapLatest { state ->
            val accountKey = (state as? AuthState.LoggedIn)?.session?.accountKey
                ?: return@flatMapLatest flowOf(emptyMap())
            combine(
                dao.observeOfflineAlbums(accountKey),
                dao.observeDownloadOwnerships(accountKey),
                dao.observeOfflineTracks(accountKey),
            ) { albums, ownerships, tracks ->
                val statusesByTrackId = tracks.associate { it.trackId to it.toStatus() }
                val trackIdsByAlbumId = ownerships.asSequence()
                    .filter { it.ownerType == OWNER_ALBUM }
                    .groupBy(DownloadOwnershipEntity::ownerId, DownloadOwnershipEntity::trackId)
                albums.associate { album ->
                    val statuses = trackIdsByAlbumId[album.albumId].orEmpty()
                        .mapNotNull(statusesByTrackId::get)
                    album.albumId to aggregateDownloadStatus(album.trackCount, statuses)
                }
            }
        }

    override fun observeAlbumTracks(albumId: String): Flow<List<OfflineTrack>> {
        return observeLibrary().map { library ->
            library.albums.firstOrNull { it.album.id == albumId }?.tracks.orEmpty()
        }
    }

    override suspend fun downloadTrack(track: Track) {
        mutations.run {
            val key = accountKey() ?: return@run
            storeTrack(key, track, OWNER_TRACK, track.id, position = 0)
            queueMissingTrack(key, track.id)
            platform.enqueue(key)
        }
    }

    override suspend fun requeueTrack(track: Track) {
        mutations.run {
            val key = accountKey() ?: return@run
            if (dao.ownershipCount(key, track.id) == 0) {
                storeTrack(key, track, OWNER_TRACK, track.id, position = 0)
            } else {
                storeDownload(key, track)
            }
            queueMissingTrack(key, track.id)
            platform.enqueue(key)
        }
    }

    override suspend fun downloadAlbum(album: Album) {
        val (generation, requestedSession) = mutations.snapshot(::session) ?: return
        val tracks = tracksApi.getTracks(requestedSession, TracksFilter.ByAlbum(album.id))
        mutations.runIfCurrent(generation) {
            val session = session()?.takeIf { it.accountKey == requestedSession.accountKey }
                ?: return@runIfCurrent
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
                queueMissingTrack(session.accountKey, track.id)
            }
            platform.enqueue(session.accountKey)
        }
    }

    override suspend fun cancelTrack(trackId: String) {
        removeTracks(listOf(trackId))
    }

    override suspend fun removeTracks(trackIds: List<String>) {
        mutations.invalidateAndRun {
            val key = accountKey() ?: return@invalidateAndRun
            if (trackIds.isEmpty()) {
                return@invalidateAndRun
            }
            withContext(NonCancellable) {
                platform.cancelTracks(key, trackIds)
                val removed = dao.removeOfflineTracks(key, trackIds)
                platform.deleteTracks(key, removed.trackPaths)
                platform.deleteArtworks(key, removed.artworkPaths)
            }
        }
    }

    override suspend fun cancelAlbum(albumId: String) {
        mutations.invalidateAndRun {
            val key = accountKey() ?: return@invalidateAndRun
            withContext(NonCancellable) {
                val tracks = dao.offlineAlbumTracks(key, albumId)
                val unownedTrackIds = tracks.mapNotNull { track ->
                    track.trackId.takeIf { dao.ownershipCount(key, it) == 1 }
                }
                platform.cancelTracks(key, unownedTrackIds)
                val removed = dao.removeOfflineAlbumDownload(key, albumId, unownedTrackIds)
                platform.deleteTracks(key, removed.trackPaths)
                platform.deleteArtworks(key, removed.artworkPaths)
            }
        }
    }

    override suspend fun retryTrack(trackId: String) {
        mutations.run {
            val key = accountKey() ?: return@run
            val track = dao.offlineTrack(key, trackId) ?: return@run
            dao.updateOfflineTrackState(
                key, trackId, DownloadState.Queued.name, track.downloadedBytes,
                track.expectedSize, track.relativePath, null, null,
            )
            platform.enqueue(key)
        }
    }

    override suspend fun clearCurrentAccount() {
        mutations.invalidateAndRun {
            val key = accountKey() ?: return@invalidateAndRun
            withContext(NonCancellable) {
                platform.cancelAccount(key)
                platform.deleteAccount(key)
                dao.clearOfflineAccount(key)
            }
        }
    }

    override suspend fun reconcile() {
        mutations.run {
            val key = accountKey() ?: return@run
            val session = session() ?: return@run
            val albums = dao.allOfflineAlbums(key)
            val initialDownloads = dao.allOfflineTracks(key)
            val ownerships = dao.downloadOwnerships(key)
            val requestedAtMs = Clock.System.now().toEpochMilliseconds()
            val standaloneRepairs = missingStandaloneDownloadRecords(
                downloads = initialDownloads,
                ownerships = ownerships,
                requestedAtMs = requestedAtMs,
            )
            standaloneRepairs.forEach { repair ->
                if (repair.createDownload) {
                    dao.upsertOfflineTrack(
                        OfflineTrackEntity(
                            accountKey = key,
                            trackId = repair.trackId,
                            relativePath = null,
                            expectedSize = null,
                            downloadedBytes = 0,
                            state = DownloadState.Queued.name,
                            error = null,
                            requestedAtMs = repair.requestedAtMs,
                            completedAtMs = null,
                        ),
                    )
                }
                if (repair.createOwnership) {
                    dao.upsertDownloadOwnership(
                        listOf(
                            DownloadOwnershipEntity(
                                key, OWNER_TRACK, repair.trackId, repair.trackId, position = 0,
                            ),
                        ),
                    )
                }
            }
            val albumsToReconcile = albums + missingOfflineAlbums(
                accountKey = key,
                albums = albums,
                ownerships = ownerships,
                requestedAtMs = requestedAtMs,
            )
            val albumRepairs = albumsToReconcile.filter { album ->
                albumNeedsDownloadRepair(album, initialDownloads, ownerships)
            }.flatMap { album ->
                val metadataTracks = try {
                    tracksApi.getTracks(session, TracksFilter.ByAlbum(album.albumId))
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    emptyList()
                }
                if (metadataTracks.isEmpty()) {
                    emptyList()
                } else {
                    val repairedAlbum = if (metadataTracks.size != album.trackCount) {
                        album.copy(trackCount = metadataTracks.size).also {
                            dao.upsertOfflineAlbum(it)
                        }
                    } else {
                        album
                    }
                    dao.upsertTracks(metadataTracks.map { it.toEntity(key) })
                    missingAlbumDownloadRecords(
                        album = repairedAlbum,
                        orderedTrackIds = metadataTracks.map(Track::id),
                        downloads = initialDownloads,
                        ownerships = ownerships,
                    )
                }
            }
            albumRepairs.forEach { repair ->
                if (repair.createDownload) {
                    dao.upsertOfflineTrack(
                        OfflineTrackEntity(
                            accountKey = key,
                            trackId = repair.trackId,
                            relativePath = null,
                            expectedSize = null,
                            downloadedBytes = 0,
                            state = DownloadState.Queued.name,
                            error = null,
                            requestedAtMs = repair.requestedAtMs,
                            completedAtMs = null,
                        ),
                    )
                }
                if (repair.createOwnership) {
                    dao.upsertDownloadOwnership(
                        listOf(
                            DownloadOwnershipEntity(
                                key, OWNER_ALBUM, repair.albumId, repair.trackId, repair.position,
                            ),
                        ),
                    )
                }
            }
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
            val repairedRecords = standaloneRepairs.isNotEmpty() || albumRepairs.isNotEmpty()
            if (repairedRecords && hasPending) {
                platform.enqueue(key)
            } else if (hasPending) {
                platform.recover(key)
            }
        }
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
        storeDownload(key, track)
        dao.upsertDownloadOwnership(
            listOf(DownloadOwnershipEntity(key, ownerType, ownerId, track.id, position)),
        )
    }

    private suspend fun storeDownload(key: String, track: Track) {
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
    }

    private suspend fun queueMissingTrack(key: String, trackId: String) {
        val download = dao.offlineTrack(key, trackId) ?: return
        val path = download.relativePath
        if (download.state == DownloadState.Completed.name && path != null &&
            platform.exists(key, path)
        ) {
            return
        }
        dao.updateOfflineTrackState(
            key, trackId, DownloadState.Queued.name, 0, download.expectedSize,
            null, null, null,
        )
    }

    private fun session() = (authRepository.authState.value as? AuthState.LoggedIn)?.session
    private fun accountKey() = session()?.accountKey

    private companion object {
        const val OWNER_TRACK = "track"
        const val OWNER_ALBUM = "album"
    }
}

internal data class AlbumDownloadRepair(
    val albumId: String,
    val trackId: String,
    val position: Int,
    val requestedAtMs: Long,
    val createDownload: Boolean,
    val createOwnership: Boolean,
)

internal data class StandaloneDownloadRepair(
    val trackId: String,
    val requestedAtMs: Long,
    val createDownload: Boolean,
    val createOwnership: Boolean,
)

internal fun missingStandaloneDownloadRecords(
    downloads: List<OfflineTrackEntity>,
    ownerships: List<DownloadOwnershipEntity>,
    requestedAtMs: Long,
): List<StandaloneDownloadRepair> {
    val downloadsByTrackId = downloads.associateBy(OfflineTrackEntity::trackId)
    val ownershipsByTrackId = ownerships.groupBy(DownloadOwnershipEntity::trackId)
    val missingDownloads = ownerships.asSequence()
        .filter { it.ownerType == "track" && it.trackId !in downloadsByTrackId }
        .distinctBy(DownloadOwnershipEntity::trackId)
        .map { ownership ->
            StandaloneDownloadRepair(
                trackId = ownership.trackId,
                requestedAtMs = requestedAtMs,
                createDownload = true,
                createOwnership = false,
            )
        }
    val missingOwnerships = downloads.asSequence()
        .filter { ownershipsByTrackId[it.trackId].isNullOrEmpty() }
        .map { download ->
            StandaloneDownloadRepair(
                trackId = download.trackId,
                requestedAtMs = download.requestedAtMs,
                createDownload = false,
                createOwnership = true,
            )
        }
    return (missingDownloads + missingOwnerships).toList()
}

internal fun missingOfflineAlbums(
    accountKey: String,
    albums: List<OfflineAlbumEntity>,
    ownerships: List<DownloadOwnershipEntity>,
    requestedAtMs: Long,
): List<OfflineAlbumEntity> {
    val albumIds = albums.mapTo(hashSetOf(), OfflineAlbumEntity::albumId)
    return ownerships.asSequence()
        .filter { it.ownerType == "album" && it.ownerId !in albumIds }
        .distinctBy(DownloadOwnershipEntity::ownerId)
        .map { ownership ->
            OfflineAlbumEntity(
                accountKey = accountKey,
                albumId = ownership.ownerId,
                trackCount = 0,
                requestedAtMs = requestedAtMs,
            )
        }
        .toList()
}

internal fun missingAlbumDownloadRecords(
    album: OfflineAlbumEntity,
    orderedTrackIds: List<String>,
    downloads: List<OfflineTrackEntity>,
    ownerships: List<DownloadOwnershipEntity>,
): List<AlbumDownloadRepair> {
    val downloadsByTrackId = downloads.associateBy(OfflineTrackEntity::trackId)
    val albumOwnerships = ownerships.asSequence().filter {
        it.ownerType == "album" && it.ownerId == album.albumId
    }.associateBy(DownloadOwnershipEntity::trackId)
    if (orderedTrackIds.size != album.trackCount) {
        return emptyList()
    }
    return orderedTrackIds.mapIndexedNotNull { position, trackId ->
        val createDownload = trackId !in downloadsByTrackId
        val createOwnership = albumOwnerships[trackId]?.position != position
        if (!createDownload && !createOwnership) {
            null
        } else {
            AlbumDownloadRepair(
                albumId = album.albumId,
                trackId = trackId,
                position = position,
                requestedAtMs = album.requestedAtMs,
                createDownload = createDownload,
                createOwnership = createOwnership,
            )
        }
    }
}

internal fun albumNeedsDownloadRepair(
    album: OfflineAlbumEntity,
    downloads: List<OfflineTrackEntity>,
    ownerships: List<DownloadOwnershipEntity>,
): Boolean {
    val downloadIds = downloads.mapTo(hashSetOf(), OfflineTrackEntity::trackId)
    val ownedTrackIds = ownerships.asSequence()
        .filter { it.ownerType == "album" && it.ownerId == album.albumId }
        .mapTo(hashSetOf(), DownloadOwnershipEntity::trackId)
    return album.trackCount <= 0 || ownedTrackIds.size != album.trackCount ||
        ownedTrackIds.any { it !in downloadIds }
}

internal class DownloadMutationCoordinator {
    private val mutex = Mutex()
    private var generation = 0L

    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock {
        block()
    }

    suspend fun <T : Any> snapshot(value: () -> T?): Pair<Long, T>? = mutex.withLock {
        value()?.let { generation to it }
    }

    suspend fun runIfCurrent(expectedGeneration: Long, block: suspend () -> Unit): Boolean =
        mutex.withLock {
            if (generation != expectedGeneration) {
                false
            } else {
                block()
                true
            }
        }

    suspend fun <T> invalidateAndRun(block: suspend () -> T): T = mutex.withLock {
        generation++
        block()
    }
}

private fun OfflineTrackEntity.toStatus() = DownloadStatus(
    DownloadState.valueOf(state), downloadedBytes, expectedSize, error,
)

private fun OfflineTrackEntity.toOfflineTrack(
    metadata: TrackEntity,
    key: String,
    platform: OfflinePlatform,
    client: SubsonicApiClient,
    session: AuthSession,
    artworkPath: String? = null,
) = OfflineTrack(
    track = Track(
        id = trackId, title = metadata.title, artist = metadata.artist,
        album = metadata.album, albumId = metadata.albumId,
        artistId = metadata.artistId, trackNumber = metadata.trackNumber,
        year = metadata.year,
        coverArtId = metadata.coverArtId,
        coverArtUrl = artworkPath?.let { platform.fileUri(key, it) }
            ?: metadata.coverArtId?.let {
                client.buildUrl("getCoverArt", session, mapOf("id" to it))
            },
        streamUrl = relativePath?.let { platform.fileUri(key, it) },
        durationMs = metadata.durationMs, contentType = metadata.contentType,
        isFavorite = metadata.isFavorite,
    ),
    status = toStatus(),
    requestedAtMs = requestedAtMs,
)

private fun AlbumEntity.toDomain(
    key: String,
    platform: OfflinePlatform,
    client: SubsonicApiClient,
    session: AuthSession,
    artworkPath: String?,
) = Album(
    id = id, name = name, artist = artist, artistId = artistId, coverArtId = coverArtId,
    coverArtUrl = artworkPath?.let { platform.fileUri(key, it) }
        ?: coverArtId?.let { client.buildUrl("getCoverArt", session, mapOf("id" to it)) },
    isFavorite = isFavorite, year = year,
)
