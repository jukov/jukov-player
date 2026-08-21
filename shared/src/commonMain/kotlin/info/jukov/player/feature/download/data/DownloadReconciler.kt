package info.jukov.player.feature.download.data

import info.jukov.player.core.data.cache.AlbumEntity
import info.jukov.player.core.data.cache.CacheDao
import info.jukov.player.core.data.cache.DownloadOwnershipEntity
import info.jukov.player.core.data.cache.OfflineAlbumEntity
import info.jukov.player.core.data.cache.OfflineTrackEntity
import info.jukov.player.core.data.cache.TrackEntity
import info.jukov.player.feature.download.domain.DownloadErrorKind
import info.jukov.player.feature.download.domain.DownloadState
import info.jukov.player.feature.download.domain.OfflinePlatform
import info.jukov.player.feature.download.domain.downloadRetryDelayMs
import kotlin.time.Clock

internal data class DownloadTrackStateUpdate(
    val state: DownloadState,
    val downloadedBytes: Long,
    val expectedSize: Long?,
    val relativePath: String?,
    val error: String?,
    val errorKind: DownloadErrorKind,
    val retryCount: Int,
    val nextRetryAtMs: Long?,
)

internal data class TrackReconciliationDecision(
    val update: DownloadTrackStateUpdate? = null,
    val hasPending: Boolean = false,
)

internal fun reconcileTrackState(
    track: OfflineTrackEntity,
    localFileExists: Boolean,
    nowMs: Long,
): TrackReconciliationDecision {
    if (track.state == DownloadState.Failed.name && track.errorKind == null) {
        val kind = when {
            track.error?.startsWith("HTTP ") == true -> DownloadErrorKind.Http
            track.error == "Authentication required" -> DownloadErrorKind.Authentication
            track.error?.contains("file", ignoreCase = true) == true -> DownloadErrorKind.Local
            else -> DownloadErrorKind.Network
        }
        val isNetworkFailure = kind == DownloadErrorKind.Network
        val retryCount = if (isNetworkFailure) {
            1
        } else {
            track.retryCount
        }
        return TrackReconciliationDecision(
            update = DownloadTrackStateUpdate(
                state = if (isNetworkFailure) {
                    DownloadState.Queued
                } else {
                    DownloadState.Failed
                },
                downloadedBytes = track.downloadedBytes,
                expectedSize = track.expectedSize,
                relativePath = track.relativePath,
                error = track.error,
                errorKind = kind,
                retryCount = retryCount,
                nextRetryAtMs = if (isNetworkFailure) {
                    nowMs + downloadRetryDelayMs(retryCount)
                } else {
                    null
                },
            ),
            hasPending = isNetworkFailure,
        )
    }
    if (track.state == DownloadState.Completed.name && !localFileExists) {
        return TrackReconciliationDecision(
            update = DownloadTrackStateUpdate(
                state = DownloadState.Failed,
                downloadedBytes = 0,
                expectedSize = track.expectedSize,
                relativePath = null,
                error = "Local file is missing",
                errorKind = DownloadErrorKind.Local,
                retryCount = track.retryCount,
                nextRetryAtMs = null,
            ),
        )
    }
    return TrackReconciliationDecision(
        hasPending = track.state == DownloadState.Queued.name ||
            track.state == DownloadState.Downloading.name,
    )
}

internal fun reconcileTrackStates(
    tracks: List<OfflineTrackEntity>,
    localFileExists: (String) -> Boolean,
    nowMs: Long,
): List<Pair<OfflineTrackEntity, TrackReconciliationDecision>> = tracks.map { track ->
    val exists = if (track.state == DownloadState.Completed.name) {
        track.relativePath?.let(localFileExists) == true
    } else {
        true
    }
    track to reconcileTrackState(track, exists, nowMs)
}

internal suspend fun reconcileDownloads(
    dao: CacheDao,
    platform: OfflinePlatform,
    accountKey: String,
) {
    var tracks = dao.allOfflineTracks(accountKey)
    var ownerships = dao.allDownloadOwnerships(accountKey)
    val metadataById = dao.tracks(accountKey, ownerships.map { it.trackId }.distinct())
        .associateBy(TrackEntity::id)
    val now = Clock.System.now().toEpochMilliseconds()
    restoreMissingDownloadsOrRemoveInvalidOwnerships(
        dao = dao,
        accountKey = accountKey,
        ownerships = ownerships,
        tracks = tracks,
        metadataById = metadataById,
        now = now,
    )
    ownerships = dao.allDownloadOwnerships(accountKey)
    val invalidAlbumTrackIds = reconcileAlbums(dao, accountKey, ownerships, now)
    ownerships = dao.allDownloadOwnerships(accountKey)
    removeEmptyAlbums(dao, accountKey, ownerships)
    removeInvalidAlbumTracks(dao, platform, accountKey, invalidAlbumTrackIds, ownerships, tracks)
    tracks = dao.allOfflineTracks(accountKey)
    ownerships = dao.allDownloadOwnerships(accountKey)
    restoreTrackOwnerships(dao, accountKey, tracks, ownerships)
    reconcileFilesAndStates(dao, platform, accountKey, dao.allOfflineTracks(accountKey), now)
}

private suspend fun restoreMissingDownloadsOrRemoveInvalidOwnerships(
    dao: CacheDao,
    accountKey: String,
    ownerships: List<DownloadOwnershipEntity>,
    tracks: List<OfflineTrackEntity>,
    metadataById: Map<String, TrackEntity>,
    now: Long,
) {
    ownerships.forEach { ownership ->
        if (tracks.none { it.trackId == ownership.trackId }) {
            if (metadataById[ownership.trackId] == null) {
                dao.deleteDownloadOwnership(
                    accountKey = accountKey,
                    ownerType = ownership.ownerType,
                    ownerId = ownership.ownerId,
                    trackId = ownership.trackId,
                )
            } else {
                dao.upsertOfflineTrack(
                    OfflineTrackEntity(
                        accountKey = accountKey,
                        trackId = ownership.trackId,
                        relativePath = null,
                        expectedSize = null,
                        downloadedBytes = 0,
                        state = DownloadState.Queued.name,
                        error = null,
                        requestedAtMs = now,
                        completedAtMs = null,
                    ),
                )
            }
        }
    }
}

private suspend fun reconcileAlbums(
    dao: CacheDao,
    accountKey: String,
    ownerships: List<DownloadOwnershipEntity>,
    now: Long,
): Set<String> {
    val offlineAlbums = dao.allOfflineAlbums(accountKey).associateBy(OfflineAlbumEntity::albumId)
    val albums = dao.allAccountAlbums(accountKey).associateBy(AlbumEntity::id)
    val invalidAlbumTrackIds = mutableSetOf<String>()
    ownerships.filter { it.ownerType == DOWNLOAD_OWNER_ALBUM }.groupBy { it.ownerId }
        .forEach { (albumId, albumOwnerships) ->
            val album = albums[albumId]
            if (album == null) {
                albumOwnerships.forEach { ownership ->
                    invalidAlbumTrackIds += ownership.trackId
                    dao.deleteDownloadOwnership(
                        accountKey = accountKey,
                        ownerType = ownership.ownerType,
                        ownerId = ownership.ownerId,
                        trackId = ownership.trackId,
                    )
                }
            } else {
                val current = offlineAlbums[albumId]
                if (current == null || current.trackCount != albumOwnerships.size) {
                    dao.upsertOfflineAlbum(
                        OfflineAlbumEntity(
                            accountKey = accountKey,
                            albumId = albumId,
                            trackCount = albumOwnerships.size,
                            requestedAtMs = current?.requestedAtMs ?: now,
                        ),
                    )
                }
            }
        }
    return invalidAlbumTrackIds
}

private suspend fun removeEmptyAlbums(
    dao: CacheDao,
    accountKey: String,
    ownerships: List<DownloadOwnershipEntity>,
) {
    val albumIdsWithOwnership = ownerships.asSequence()
        .filter { it.ownerType == DOWNLOAD_OWNER_ALBUM }
        .map { it.ownerId }
        .toSet()
    dao.allOfflineAlbums(accountKey).map(OfflineAlbumEntity::albumId)
        .filter { it !in albumIdsWithOwnership }
        .forEach { dao.deleteOfflineAlbum(accountKey, it) }
}

private suspend fun removeInvalidAlbumTracks(
    dao: CacheDao,
    platform: OfflinePlatform,
    accountKey: String,
    invalidAlbumTrackIds: Set<String>,
    ownerships: List<DownloadOwnershipEntity>,
    tracks: List<OfflineTrackEntity>,
) {
    invalidAlbumTrackIds.filter { trackId ->
        ownerships.none { it.trackId == trackId }
    }.forEach { trackId ->
        tracks.firstOrNull { it.trackId == trackId }?.let { track ->
            dao.deleteOfflineTrack(accountKey, trackId)
            platform.deleteTrack(accountKey, track.relativePath)
        }
    }
}

private suspend fun restoreTrackOwnerships(
    dao: CacheDao,
    accountKey: String,
    tracks: List<OfflineTrackEntity>,
    ownerships: List<DownloadOwnershipEntity>,
) {
    tracks.filter { track -> ownerships.none { it.trackId == track.trackId } }
        .forEach { track ->
            dao.upsertDownloadOwnership(
                listOf(
                    DownloadOwnershipEntity(
                        accountKey = accountKey,
                        ownerType = DOWNLOAD_OWNER_TRACK,
                        ownerId = track.trackId,
                        trackId = track.trackId,
                        position = 0,
                    ),
                ),
            )
        }
}

private suspend fun reconcileFilesAndStates(
    dao: CacheDao,
    platform: OfflinePlatform,
    accountKey: String,
    tracks: List<OfflineTrackEntity>,
    now: Long,
) {
    platform.cleanupStaleParts(accountKey, tracks.mapTo(mutableSetOf()) { it.trackId })
    val reconciled = reconcileTrackStates(
        tracks = tracks,
        localFileExists = { platform.exists(accountKey, it) },
        nowMs = now,
    )
    reconciled.forEach { (track, decision) ->
        decision.update?.let { update ->
            dao.updateOfflineTrackState(
                accountKey = accountKey,
                trackId = track.trackId,
                state = update.state.name,
                downloadedBytes = update.downloadedBytes,
                expectedSize = update.expectedSize,
                relativePath = update.relativePath,
                error = update.error,
                completedAtMs = null,
                errorKind = update.errorKind.name,
                retryCount = update.retryCount,
                nextRetryAtMs = update.nextRetryAtMs,
            )
        }
    }
    if (reconciled.any { it.second.hasPending }) {
        platform.recover(accountKey)
    }
}
