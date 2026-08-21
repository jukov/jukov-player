package info.jukov.player.feature.download.data

import info.jukov.player.core.data.cache.CacheDao
import info.jukov.player.core.data.cache.DownloadOwnershipEntity
import info.jukov.player.core.data.cache.OfflineAlbumEntity
import info.jukov.player.core.data.cache.OfflineTrackEntity
import info.jukov.player.core.data.cache.toEntity
import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.feature.auth.domain.accountKey
import info.jukov.player.feature.download.domain.DownloadState
import info.jukov.player.feature.download.domain.OfflinePlatform
import info.jukov.player.feature.track.data.TracksApi
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.track.domain.TracksFilter
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock

internal class DownloadMutations(
    private val dao: CacheDao,
    private val tracksApi: TracksApi,
    private val platform: OfflinePlatform,
    private val session: () -> AuthSession?,
) {
    private val coordinator = DownloadMutationCoordinator()

    suspend fun downloadTrack(track: Track) {
        coordinator.run {
            val key = accountKey() ?: return@run
            storeTrack(key, track, DOWNLOAD_OWNER_TRACK, track.id, position = 0)
            platform.enqueue(key)
        }
    }

    suspend fun downloadAlbum(album: Album) {
        val (generation, requestedSession) = coordinator.snapshot(session) ?: return
        val tracks = tracksApi.getTracks(requestedSession, TracksFilter.ByAlbum(album.id))
        coordinator.runIfCurrent(generation) {
            val currentSession = matchingAccountSession(requestedSession, session())
                ?: return@runIfCurrent
            val now = Clock.System.now().toEpochMilliseconds()
            dao.upsertAlbums(listOf(album.toEntity(currentSession.accountKey)))
            dao.upsertTracks(tracks.map { it.toEntity(currentSession.accountKey) })
            dao.upsertOfflineAlbum(
                OfflineAlbumEntity(
                    accountKey = currentSession.accountKey,
                    albumId = album.id,
                    trackCount = tracks.size,
                    requestedAtMs = now,
                ),
            )
            tracks.forEachIndexed { index, track ->
                storeTrack(
                    key = currentSession.accountKey,
                    track = track,
                    ownerType = DOWNLOAD_OWNER_ALBUM,
                    ownerId = album.id,
                    position = index,
                )
            }
            platform.enqueue(currentSession.accountKey)
        }
    }

    suspend fun removeTracks(trackIds: List<String>) {
        coordinator.invalidateAndRun {
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

    suspend fun cancelAlbum(albumId: String) {
        coordinator.invalidateAndRun {
            val key = accountKey() ?: return@invalidateAndRun
            withContext(NonCancellable) {
                val tracks = dao.offlineAlbumTracks(key, albumId)
                val ownershipCounts = tracks.associate { track ->
                    track.trackId to dao.ownershipCount(key, track.trackId)
                }
                val unownedTrackIds = trackIdsWithoutOtherOwners(tracks, ownershipCounts)
                platform.cancelTracks(key, unownedTrackIds)
                val removed = dao.removeOfflineAlbumDownload(key, albumId, unownedTrackIds)
                platform.deleteTracks(key, removed.trackPaths)
                platform.deleteArtworks(key, removed.artworkPaths)
            }
        }
    }

    suspend fun retryTrack(trackId: String) {
        coordinator.run {
            val key = accountKey() ?: return@run
            val track = dao.offlineTrack(key, trackId) ?: return@run
            dao.updateOfflineTrackState(
                accountKey = key,
                trackId = trackId,
                state = DownloadState.Queued.name,
                downloadedBytes = track.downloadedBytes,
                expectedSize = track.expectedSize,
                relativePath = track.relativePath,
                error = null,
                completedAtMs = null,
            )
            platform.enqueue(key)
        }
    }

    suspend fun retryAllFailed() {
        coordinator.run {
            val key = accountKey() ?: return@run
            if (dao.retryAllFailedTracks(key) > 0) {
                platform.enqueue(key)
            }
        }
    }

    suspend fun clearCurrentAccount() {
        coordinator.invalidateAndRun {
            val key = accountKey() ?: return@invalidateAndRun
            withContext(NonCancellable) {
                platform.cancelAccount(key)
                platform.deleteAccount(key)
                dao.clearOfflineAccount(key)
            }
        }
    }

    suspend fun runSerialized(block: suspend () -> Unit) {
        coordinator.run(block)
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
            existing ?: OfflineTrackEntity(
                accountKey = key,
                trackId = track.id,
                relativePath = null,
                expectedSize = null,
                downloadedBytes = 0,
                state = DownloadState.Queued.name,
                error = null,
                requestedAtMs = Clock.System.now().toEpochMilliseconds(),
                completedAtMs = null,
            ),
        )
        dao.upsertDownloadOwnership(
            listOf(
                DownloadOwnershipEntity(
                    accountKey = key,
                    ownerType = ownerType,
                    ownerId = ownerId,
                    trackId = track.id,
                    position = position,
                ),
            ),
        )
    }

    private fun accountKey() = session()?.accountKey
}

internal fun matchingAccountSession(
    requestedSession: AuthSession,
    currentSession: AuthSession?,
): AuthSession? = currentSession?.takeIf { it.accountKey == requestedSession.accountKey }

internal fun trackIdsWithoutOtherOwners(
    tracks: List<OfflineTrackEntity>,
    ownershipCounts: Map<String, Int>,
): List<String> = tracks.mapNotNull { track ->
    track.trackId.takeIf { ownershipCounts[it] == 1 }
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
