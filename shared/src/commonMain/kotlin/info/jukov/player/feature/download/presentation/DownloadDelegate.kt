package info.jukov.player.feature.download.presentation

import info.jukov.player.di.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.download.domain.DownloadStatus
import info.jukov.player.feature.download.domain.DownloadsRepository
import info.jukov.player.feature.track.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Inject
@SingleIn(AppScope::class)
class DownloadDelegate(
    private val repository: DownloadsRepository,
    appScope: CoroutineScope,
) {
    val library = repository.observeLibrary().stateIn(
        appScope, SharingStarted.Eagerly, info.jukov.player.feature.download.domain.OfflineLibrary(),
    )
    val trackStatuses = library.map { value -> value.tracks.associate { it.track.id to it.status } }
        .stateIn(appScope, SharingStarted.Eagerly, emptyMap())
    val albumStatuses = library.map { value -> value.albums.associate { it.album.id to it.status } }
        .stateIn(appScope, SharingStarted.Eagerly, emptyMap())
    val artworkUris = library.map { value ->
        buildMap {
            value.tracks.forEach { item ->
                val id = item.track.coverArtId
                val uri = item.track.coverArtUrl
                if (id != null && uri != null) put(id, uri)
            }
            value.albums.forEach { item ->
                val id = item.album.coverArtId
                val uri = item.album.coverArtUrl
                if (id != null && uri != null) put(id, uri)
            }
        }
    }.stateIn(appScope, SharingStarted.Eagerly, emptyMap())

    suspend fun download(track: Track) = repository.downloadTrack(track)
    suspend fun download(album: Album) = repository.downloadAlbum(album)
    suspend fun cancelTrack(id: String) = repository.cancelTrack(id)
    suspend fun cancelAlbum(id: String) = repository.cancelAlbum(id)
    suspend fun retry(id: String) = repository.retryTrack(id)
}
