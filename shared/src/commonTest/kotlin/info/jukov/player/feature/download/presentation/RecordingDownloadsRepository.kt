package info.jukov.player.feature.download.presentation

import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.download.domain.DownloadStatus
import info.jukov.player.feature.download.domain.DownloadsRepository
import info.jukov.player.feature.download.domain.OfflineLibrary
import info.jukov.player.feature.download.domain.OfflineTrack
import info.jukov.player.feature.track.domain.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

internal class RecordingDownloadsRepository(
    library: OfflineLibrary = OfflineLibrary(),
    private val onDownloadTrack: suspend (Track) -> Unit = {},
    private val onDownloadAlbum: suspend (Album) -> Unit = {},
) : DownloadsRepository {
    private val libraryFlow = MutableStateFlow(library)
    val downloadedAlbums = mutableListOf<Album>()
    val downloadedTracks = mutableListOf<Track>()
    val cancelledAlbumIds = mutableListOf<String>()
    val cancelledTrackIds = mutableListOf<String>()

    override fun observeLibrary(): Flow<OfflineLibrary> = libraryFlow
    override fun searchLibrary(query: String): Flow<OfflineLibrary> = libraryFlow
    override fun observeTrackStatuses(): Flow<Map<String, DownloadStatus>> = flowOf(emptyMap())
    override fun observeAlbumTracks(albumId: String): Flow<List<OfflineTrack>> = emptyFlow()
    override suspend fun downloadTrack(track: Track) {
        downloadedTracks += track
        onDownloadTrack(track)
    }
    override suspend fun downloadAlbum(album: Album) {
        downloadedAlbums += album
        onDownloadAlbum(album)
    }
    override suspend fun cancelTrack(trackId: String) {
        cancelledTrackIds += trackId
    }
    override suspend fun removeTracks(trackIds: List<String>) = Unit
    override suspend fun cancelAlbum(albumId: String) {
        cancelledAlbumIds += albumId
    }
    override suspend fun retryTrack(trackId: String) = Unit
    override suspend fun clearCurrentAccount() = Unit
    override suspend fun reconcile() = Unit
    override suspend fun localTrackUri(trackId: String): String? = null
    override suspend fun localTrackUris(trackIds: List<String>): Map<String, String> = emptyMap()
    override suspend fun localArtworkUri(coverArtId: String?): String? = null
    override suspend fun localArtworkUris(coverArtIds: List<String>): Map<String, String> = emptyMap()
}
