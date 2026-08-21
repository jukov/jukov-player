package info.jukov.player.feature.download.data

import info.jukov.player.core.data.cache.CacheDao
import info.jukov.player.core.data.cache.OfflineArtworkEntity
import info.jukov.player.core.data.cache.OfflineTrackEntity
import info.jukov.player.feature.download.domain.DownloadState
import info.jukov.player.feature.download.domain.OfflinePlatform

internal class OfflineLocalMediaResolver(
    private val dao: CacheDao,
    private val platform: OfflinePlatform,
    private val accountKey: () -> String?,
) {
    suspend fun localTrackUri(trackId: String): String? = localTrackUris(listOf(trackId))[trackId]

    suspend fun localTrackUris(trackIds: List<String>): Map<String, String> {
        val key = accountKey() ?: return emptyMap()
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

    suspend fun localArtworkUri(coverArtId: String?): String? {
        val id = coverArtId ?: return null
        return localArtworkUris(listOf(id))[id]
    }

    suspend fun localArtworkUris(coverArtIds: List<String>): Map<String, String> {
        val key = accountKey() ?: return emptyMap()
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
