package info.jukov.player.feature.download.presentation

import info.jukov.player.di.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.download.domain.DownloadStatus
import info.jukov.player.feature.download.domain.DownloadTarget
import info.jukov.player.feature.download.domain.DownloadsRepository
import info.jukov.player.feature.track.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Inject
@SingleIn(AppScope::class)
class DownloadDelegate(
    private val repository: DownloadsRepository,
    appScope: CoroutineScope,
) {
    private val pendingMutex = Mutex()
    private val pending = mutableSetOf<DownloadTarget>()
    val library = repository.observeLibrary().stateIn(
        appScope, SharingStarted.Eagerly, info.jukov.player.feature.download.domain.OfflineLibrary(),
    )
    val trackStatuses = repository.observeTrackStatuses()
        .stateIn(appScope, SharingStarted.Eagerly, emptyMap())
    val albumStatuses = repository.observeAlbumStatuses()
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

    suspend fun download(track: Track): Result<Unit> = downloadOnce(DownloadTarget.Track(track.id)) {
        repository.downloadTrack(track)
    }
    suspend fun download(album: Album): Result<Unit> = downloadOnce(DownloadTarget.Album(album.id)) {
        repository.downloadAlbum(album)
    }
    suspend fun cancelTrack(id: String) = repository.cancelTrack(id)
    suspend fun cancelAlbum(id: String) = repository.cancelAlbum(id)
    suspend fun retry(id: String) = repository.retryTrack(id)

    private suspend fun downloadOnce(
        target: DownloadTarget,
        request: suspend () -> Unit,
    ): Result<Unit> {
        val accepted = pendingMutex.withLock { pending.add(target) }
        if (!accepted) {
            return Result.success(Unit)
        }
        return try {
            request()
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        } finally {
            withContext(NonCancellable) {
                pendingMutex.withLock { pending.remove(target) }
            }
        }
    }
}
