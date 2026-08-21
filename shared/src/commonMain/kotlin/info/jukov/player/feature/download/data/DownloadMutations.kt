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

internal suspend fun downloadTrackMutation(
    coordinator: DownloadMutationCoordinator,
    accountKey: () -> String?,
    dao: CacheDao,
    platform: OfflinePlatform,
    track: Track,
) {
    coordinator.run {
        val key = accountKey() ?: return@run
        storeTrack(dao, key, track, DOWNLOAD_OWNER_TRACK, track.id, position = 0)
        platform.enqueue(key)
    }
}

internal suspend fun downloadAlbumMutation(
    coordinator: DownloadMutationCoordinator,
    session: () -> AuthSession?,
    dao: CacheDao,
    tracksApi: TracksApi,
    platform: OfflinePlatform,
    album: Album,
) {
    fetchAndCommitIfCurrentAccount(
        coordinator = coordinator,
        session = session,
        fetch = { requestedSession ->
            tracksApi.getTracks(requestedSession, TracksFilter.ByAlbum(album.id))
        },
        commit = { currentSession, tracks ->
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
                    dao = dao,
                    key = currentSession.accountKey,
                    track = track,
                    ownerType = DOWNLOAD_OWNER_ALBUM,
                    ownerId = album.id,
                    position = index,
                )
            }
            platform.enqueue(currentSession.accountKey)
        },
    )
}

internal suspend fun removeTracksMutation(
    coordinator: DownloadMutationCoordinator,
    accountKey: () -> String?,
    dao: CacheDao,
    platform: OfflinePlatform,
    trackIds: List<String>,
) {
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

internal suspend fun cancelAlbumMutation(
    coordinator: DownloadMutationCoordinator,
    accountKey: () -> String?,
    dao: CacheDao,
    platform: OfflinePlatform,
    albumId: String,
) {
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

internal suspend fun retryTrackMutation(
    coordinator: DownloadMutationCoordinator,
    accountKey: () -> String?,
    dao: CacheDao,
    platform: OfflinePlatform,
    trackId: String,
) {
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

internal suspend fun retryAllFailedMutation(
    coordinator: DownloadMutationCoordinator,
    accountKey: () -> String?,
    dao: CacheDao,
    platform: OfflinePlatform,
) {
    coordinator.run {
        val key = accountKey() ?: return@run
        if (dao.retryAllFailedTracks(key) > 0) {
            platform.enqueue(key)
        }
    }
}

internal suspend fun clearCurrentAccountMutation(
    coordinator: DownloadMutationCoordinator,
    accountKey: () -> String?,
    dao: CacheDao,
    platform: OfflinePlatform,
) {
    coordinator.invalidateAndRun {
        val key = accountKey() ?: return@invalidateAndRun
        withContext(NonCancellable) {
            platform.cancelAccount(key)
            platform.deleteAccount(key)
            dao.clearOfflineAccount(key)
        }
    }
}

internal suspend fun <T> fetchAndCommitIfCurrentAccount(
    coordinator: DownloadMutationCoordinator,
    session: () -> AuthSession?,
    fetch: suspend (AuthSession) -> T,
    commit: suspend (AuthSession, T) -> Unit,
): Boolean {
    val (generation, requestedSession) = coordinator.snapshot(session) ?: return false
    val fetched = fetch(requestedSession)
    var committed = false
    coordinator.runIfCurrent(generation) {
        val currentSession = matchingAccountSession(requestedSession, session())
            ?: return@runIfCurrent
        commit(currentSession, fetched)
        committed = true
    }
    return committed
}

private suspend fun storeTrack(
    dao: CacheDao,
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
