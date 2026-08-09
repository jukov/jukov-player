package info.jukov.player.feature.download.domain

import info.jukov.player.core.data.cache.CacheDao
import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.subsonic.data.SubsonicApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

interface LocalMediaResolver {
    suspend fun localTrackUri(trackId: String): String?
    suspend fun localTrackUris(trackIds: List<String>): Map<String, String>
}

interface PersistentArtworkResolver {
    suspend fun localArtworkUri(coverArtId: String?): String?
    suspend fun localArtworkUris(coverArtIds: List<String>): Map<String, String>
}

interface DownloadsRepository : LocalMediaResolver, PersistentArtworkResolver {
    fun observeLibrary(): Flow<OfflineLibrary>
    fun searchLibrary(query: String): Flow<OfflineLibrary>
    fun observeTrackStatuses(): Flow<Map<String, DownloadStatus>>
    fun observeAlbumTracks(albumId: String): Flow<List<OfflineTrack>>
    suspend fun downloadTrack(track: Track)
    suspend fun downloadAlbum(album: Album)
    suspend fun cancelTrack(trackId: String)
    suspend fun removeTracks(trackIds: List<String>)
    suspend fun cancelAlbum(albumId: String)
    suspend fun retryTrack(trackId: String)
    suspend fun clearCurrentAccount()
    suspend fun reconcile()
}

interface OfflinePlatform {
    fun enqueue(accountKey: String)
    fun recover(accountKey: String)
    fun cancelTrack(accountKey: String, trackId: String)
    fun cancelTracks(accountKey: String, trackIds: List<String>)
    fun cancelAccount(accountKey: String)
    fun deleteTrack(accountKey: String, relativePath: String?)
    fun deleteTracks(accountKey: String, relativePaths: List<String>)
    fun deleteArtwork(accountKey: String, relativePath: String?)
    fun deleteArtworks(accountKey: String, relativePaths: List<String>)
    fun deleteAccount(accountKey: String)
    fun fileUri(accountKey: String, relativePath: String): String
    fun exists(accountKey: String, relativePath: String): Boolean
    fun cleanupStaleParts(accountKey: String, activeTrackIds: Set<String>)
}

fun interface OfflinePlatformFactory {
    fun create(
        authRepository: AuthRepository,
        dao: CacheDao,
        client: SubsonicApiClient,
        scope: CoroutineScope,
    ): OfflinePlatform
}
