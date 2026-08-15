package info.jukov.player.feature.download.presentation

import info.jukov.player.feature.album.domain.Album
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
