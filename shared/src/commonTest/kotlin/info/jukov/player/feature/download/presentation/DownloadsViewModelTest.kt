package info.jukov.player.feature.download.presentation

import info.jukov.player.core.domain.LoadableState
import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.download.domain.DownloadState
import info.jukov.player.feature.download.domain.DownloadStatus
import info.jukov.player.feature.download.domain.OfflineAlbum
import info.jukov.player.feature.download.domain.OfflineTrack
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {
    @Test
    fun downloadedTrackIsPlayedAfterFileVerification() = runTest {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val selected = track("track")
        val repository = RecordingDownloadsRepository(onEnsureDownloaded = { true })
        val viewModel = DownloadsViewModel(repository)
        var played = false

        viewModel.playWhenDownloaded(listOf(selected), 0) { tracks, index ->
            played = tracks[index] == selected
        }
        advanceUntilIdle()

        assertTrue(played)
    }

    @Test
    fun missingTrackIsQueuedWithoutStartingPlayback() = runTest {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = RecordingDownloadsRepository(onEnsureDownloaded = { false })
        val viewModel = DownloadsViewModel(repository)
        var played = false

        viewModel.playWhenDownloaded(listOf(track("track")), 0) { _, _ -> played = true }
        advanceUntilIdle()

        assertEquals(false, played)
    }

    @Test
    fun albumWithMissingDownloadJobIsDownloadedInsteadOfPartiallyPlayed() = runTest {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = RecordingDownloadsRepository()
        val viewModel = DownloadsViewModel(repository)
        val album = OfflineAlbum(
            album = album("album"),
            tracks = listOf(
                OfflineTrack(track("track"), DownloadStatus(DownloadState.Completed)),
            ),
            expectedTrackCount = 2,
        )
        var played = false

        viewModel.playAlbumWhenDownloaded(album) { _, _ -> played = true }
        advanceUntilIdle()

        assertEquals(listOf(album.album), repository.downloadedAlbums)
        assertEquals(false, played)
    }

    @Test
    fun albumWithNoDownloadJobsIsDownloaded() = runTest {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = RecordingDownloadsRepository()
        val viewModel = DownloadsViewModel(repository)
        val album = OfflineAlbum(
            album = album("album"),
            tracks = emptyList(),
            expectedTrackCount = 2,
        )

        viewModel.playAlbumWhenDownloaded(album) { _, _ -> }
        advanceUntilIdle()

        assertEquals(listOf(album.album), repository.downloadedAlbums)
    }

    @Test
    fun albumRepairFailureIsPublishedWithoutEscapingCoroutine() = runTest {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = RecordingDownloadsRepository(
            onDownloadAlbum = { throw IllegalStateException("network failed") },
        )
        val viewModel = DownloadsViewModel(repository)
        val album = OfflineAlbum(
            album = album("album"),
            tracks = emptyList(),
            expectedTrackCount = 2,
        )

        viewModel.playAlbumWhenDownloaded(album) { _, _ -> }
        advanceUntilIdle()

        assertIs<LoadableState.Failure<*>>(viewModel.state.value)
    }

    @AfterTest
    fun resetMainDispatcher() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun removeAlbumsCancelsEverySelectedAlbumInOrder() = runTest {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val downloadsRepository = RecordingDownloadsRepository()
        val viewModel = DownloadsViewModel(downloadsRepository, info.jukov.player.core.domain.SettingsSortPreferences(com.russhwolf.settings.MapSettings()))
        val albums = listOf(album("first"), album("second"))

        viewModel.removeAlbums(albums)
        advanceUntilIdle()

        assertEquals(listOf("first", "second"), downloadsRepository.cancelledAlbumIds)
    }

    private fun album(id: String) = Album(
        id = id,
        name = "Album $id",
        artist = "Artist",
        artistId = null,
        coverArtUrl = null,
        isFavorite = false,
    )

    private fun track(id: String) = info.jukov.player.feature.track.domain.Track(
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
