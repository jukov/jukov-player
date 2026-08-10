package info.jukov.player.feature.playlist.presentation

import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.feature.playlist.domain.Playlist
import info.jukov.player.feature.playlist.domain.PlaylistCreationResult
import info.jukov.player.feature.playlist.domain.PlaylistsRepository
import info.jukov.player.feature.track.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistPickerViewModelTest {
    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun partialCreateClosesPickerAndEmitsSettingsWarning() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = FakePlaylistsRepository()
        val viewModel = PlaylistPickerViewModel(repository)
        val warning = async(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.messages.first()
        }

        viewModel.open(
            listOf(
                Track(
                    id = "track-1",
                    title = "Track",
                    artist = "Artist",
                    albumId = null,
                    artistId = null,
                    trackNumber = null,
                    coverArtUrl = null,
                    isFavorite = false,
                ),
            ),
        )
        viewModel.showCreate()
        viewModel.create("Created", isPublic = true)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.visible)
        assertEquals(AppError.PlaylistUpdateFailed, warning.await())
    }
}

private class FakePlaylistsRepository : PlaylistsRepository {
    override val playlists = MutableStateFlow<LoadableState<List<Playlist>>>(
        LoadableState.Content(emptyList()),
    )

    override fun playlist(id: String): Flow<LoadableState<Playlist>> = emptyFlow()
    override suspend fun loadPlaylists(forceRefresh: Boolean) = Result.success(Unit)
    override suspend fun loadPlaylist(id: String, forceRefresh: Boolean) = Result.success(Unit)

    override suspend fun createPlaylist(
        name: String,
        isPublic: Boolean,
        songIds: List<String>,
    ) = Result.success(PlaylistCreationResult(settingsSynced = false))

    override suspend fun updatePlaylist(id: String, name: String, isPublic: Boolean) =
        Result.success(Unit)

    override suspend fun addTracks(playlistId: String, songIds: List<String>) = Result.success(Unit)
    override suspend fun removeTracks(playlistId: String, songIndexes: List<Int>) = Result.success(Unit)
    override suspend fun deletePlaylist(id: String) = Result.success(Unit)
    override fun isEditable(playlist: Playlist) = true
}
