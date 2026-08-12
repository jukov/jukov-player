package info.jukov.player.feature.playlist.presentation

import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.feature.download.presentation.DownloadDelegate
import info.jukov.player.feature.download.presentation.RecordingDownloadsRepository
import info.jukov.player.feature.favorite.domain.FavoriteChange
import info.jukov.player.feature.favorite.domain.FavoriteTarget
import info.jukov.player.feature.favorite.domain.Favorites
import info.jukov.player.feature.favorite.domain.FavoritesRepository
import info.jukov.player.feature.favorite.presentation.FavoriteDelegate
import info.jukov.player.feature.playlist.domain.Playlist
import info.jukov.player.feature.playlist.domain.PlaylistCreationResult
import info.jukov.player.feature.playlist.domain.PlaylistsRepository
import info.jukov.player.feature.track.domain.Track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistViewModelTest {
    @AfterTest
    fun resetMainDispatcher() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun downloadFailureIsReportedToPlaylistObservers() = runTest {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val downloads = RecordingDownloadsRepository(
            onDownloadTrack = { throw IllegalStateException("network failed") },
        )
        val viewModel = PlaylistViewModel(
            repository = StubPlaylistsRepository(),
            downloadDelegate = DownloadDelegate(downloads, backgroundScope),
            favoriteDelegate = FavoriteDelegate(StubFavoritesRepository()),
        )
        var message: AppError? = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            message = viewModel.messages.first()
        }

        viewModel.download(track("track"))
        advanceUntilIdle()

        assertEquals(AppError.DownloadFailed, message)
    }

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

private class StubFavoritesRepository : FavoritesRepository {
    override val changes = MutableSharedFlow<FavoriteChange>()
    override fun getFavorites(forceRefresh: Boolean): Flow<LoadableState<Favorites>> = emptyFlow()
    override suspend fun setFavorite(target: FavoriteTarget, isFavorite: Boolean) = Result.success(Unit)
    override suspend fun setFavorites(targets: List<FavoriteTarget>, isFavorite: Boolean) = Result.success(Unit)
}

private class StubPlaylistsRepository : PlaylistsRepository {
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
    ): Result<PlaylistCreationResult> = error("Not used")
    override suspend fun updatePlaylist(id: String, name: String, isPublic: Boolean) = Result.success(Unit)
    override suspend fun addTracks(playlistId: String, songIds: List<String>) = Result.success(Unit)
    override suspend fun removeTracks(playlistId: String, songIndexes: List<Int>) = Result.success(Unit)
    override suspend fun deletePlaylist(id: String) = Result.success(Unit)
    override fun isEditable(playlist: Playlist) = false
}
