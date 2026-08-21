package info.jukov.player.feature.download.data

import info.jukov.player.core.data.cache.CacheDao
import info.jukov.player.core.data.cache.OfflineArtworkEntity
import info.jukov.player.core.data.cache.OfflineTrackEntity
import info.jukov.player.feature.download.domain.DownloadState
import info.jukov.player.feature.download.domain.OfflinePlatform

internal suspend fun resolveLocalTrackUris(
    dao: CacheDao,
    platform: OfflinePlatform,
    accountKey: String?,
    trackIds: List<String>,
): Map<String, String> {
    val key = accountKey ?: return emptyMap()
    if (trackIds.isEmpty()) {
        return emptyMap()
    }
    return resolveLocalMediaUris(
        items = dao.offlineTracks(key, trackIds),
        id = OfflineTrackEntity::trackId,
        relativePath = OfflineTrackEntity::relativePath,
        state = OfflineTrackEntity::state,
        exists = { platform.exists(key, it) },
        fileUri = { platform.fileUri(key, it) },
    )
}

internal suspend fun resolveLocalArtworkUris(
    dao: CacheDao,
    platform: OfflinePlatform,
    accountKey: String?,
    coverArtIds: List<String>,
): Map<String, String> {
    val key = accountKey ?: return emptyMap()
    if (coverArtIds.isEmpty()) {
        return emptyMap()
    }
    return resolveLocalMediaUris(
        items = dao.offlineArtworks(key, coverArtIds),
        id = OfflineArtworkEntity::coverArtId,
        relativePath = OfflineArtworkEntity::relativePath,
        state = OfflineArtworkEntity::state,
        exists = { platform.exists(key, it) },
        fileUri = { platform.fileUri(key, it) },
    )
}

internal fun <T> resolveLocalMediaUris(
    items: List<T>,
    id: (T) -> String,
    relativePath: (T) -> String?,
    state: (T) -> String,
    exists: (String) -> Boolean,
    fileUri: (String) -> String,
): Map<String, String> = items.mapNotNull { item ->
    val path = relativePath(item) ?: return@mapNotNull null
    if (state(item) == DownloadState.Completed.name && exists(path)) {
        id(item) to fileUri(path)
    } else {
        null
    }
}.toMap()
