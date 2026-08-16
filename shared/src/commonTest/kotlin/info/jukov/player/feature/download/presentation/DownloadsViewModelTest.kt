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

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {
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
}
