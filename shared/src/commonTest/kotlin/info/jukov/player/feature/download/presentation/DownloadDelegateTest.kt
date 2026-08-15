package info.jukov.player.feature.download.presentation

import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.download.domain.DownloadState
import info.jukov.player.feature.download.domain.DownloadStatus
import info.jukov.player.feature.track.domain.Track
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadDelegateTest {
    @Test
    fun statusesComeFromDownloadRecordsEvenWhenLibraryIsMissingItems() = runTest {
        val trackStatus = DownloadStatus(DownloadState.Completed, 100, 100)
        val albumStatus = DownloadStatus(DownloadState.Downloading, 50, 100)
        val repository = RecordingDownloadsRepository(
            trackStatuses = mapOf("track" to trackStatus),
            albumStatuses = mapOf("album" to albumStatus),
        )

        val delegate = DownloadDelegate(repository, backgroundScope)
        runCurrent()

        assertEquals(mapOf("track" to trackStatus), delegate.trackStatuses.value)
        assertEquals(mapOf("album" to albumStatus), delegate.albumStatuses.value)
    }

    @Test
    fun repeatedAlbumDownloadWhileRequestIsRunningIsIgnored() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val finishRequest = CompletableDeferred<Unit>()
        val repository = RecordingDownloadsRepository(
            onDownloadAlbum = {
                requestStarted.complete(Unit)
                finishRequest.await()
            },
        )
        val delegate = DownloadDelegate(repository, backgroundScope)
        val album = album("album")

        val first = async { delegate.download(album) }
        requestStarted.await()
        delegate.download(album)
        finishRequest.complete(Unit)
        first.await()

        assertEquals(listOf(album), repository.downloadedAlbums)
    }

    @Test
    fun networkFailureIsReturnedWithoutEscapingDownload() = runTest {
        val repository = RecordingDownloadsRepository(
            onDownloadTrack = { throw IllegalStateException("network failed") },
        )
        val delegate = DownloadDelegate(repository, backgroundScope)
        val result = delegate.download(track("track"))

        assertEquals("network failed", result.exceptionOrNull()?.message)
    }

    @Test
    fun cancelledRequestDoesNotBlockLaterDownloadForSameAlbum() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val keepFirstRequestRunning = CompletableDeferred<Unit>()
        var requestCount = 0
        val repository = RecordingDownloadsRepository(
            onDownloadAlbum = {
                requestCount += 1
                if (requestCount == 1) {
                    requestStarted.complete(Unit)
                    keepFirstRequestRunning.await()
                }
            },
        )
        val delegate = DownloadDelegate(repository, backgroundScope)
        val album = album("album")

        val first = launch { delegate.download(album) }
        requestStarted.await()
        first.cancelAndJoin()
        delegate.download(album)

        assertEquals(2, requestCount)
    }

    private fun album(id: String) = Album(
        id = id,
        name = "Album $id",
        artist = "Artist",
        artistId = null,
        coverArtUrl = null,
        isFavorite = false,
    )

    private fun track(id: String) = Track(
        id = id,
        title = "Track $id",
        artist = "Artist",
        albumId = null,
        artistId = null,
        trackNumber = null,
        coverArtUrl = null,
        isFavorite = false,
    )
}
