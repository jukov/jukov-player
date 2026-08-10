package info.jukov.player.feature.playlist.presentation

import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.feature.playlist.domain.Playlist
import info.jukov.player.feature.playlist.domain.PlaylistCreationResult
import info.jukov.player.feature.playlist.domain.PlaylistsRepository
import info.jukov.player.feature.track.domain.Track
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistPickerViewModelTest {
    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun successfulAdditionStaysVisibleUntilConfirmationIsDismissed() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = FakePlaylistsRepository()
        val viewModel = PlaylistPickerViewModel(repository)
        var successCalls = 0
        viewModel.open(listOf(TRACK)) { successCalls += 1 }

        viewModel.addTo(PLAYLIST)
        runCurrent()

        assertEquals(PlaylistPickerSubmission.Success, viewModel.state.value.submission)
        assertTrue(viewModel.state.value.visible)
        assertEquals(1, successCalls)
        assertEquals(listOf(PLAYLIST.id to listOf(TRACK.id)), repository.addCalls)

        viewModel.dismiss()

        assertFalse(viewModel.state.value.visible)
    }

    @Test
    fun pickerCannotBeDismissedOrSubmittedAgainWhileRequestIsPending() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val requestGate = CompletableDeferred<Unit>()
        val repository = FakePlaylistsRepository(requestGate = requestGate)
        val viewModel = PlaylistPickerViewModel(repository)
        viewModel.open(listOf(TRACK))

        viewModel.addTo(PLAYLIST)
        runCurrent()
        viewModel.dismiss()
        viewModel.addTo(PLAYLIST)
        runCurrent()

        assertTrue(viewModel.state.value.visible)
        assertTrue(viewModel.state.value.pending)
        assertEquals(1, repository.addCalls.size)

        requestGate.complete(Unit)
        runCurrent()

        assertEquals(PlaylistPickerSubmission.Success, viewModel.state.value.submission)
    }

    @Test
    fun failedAdditionRestoresInteractivePicker() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = FakePlaylistsRepository(
            addResult = Result.failure(IllegalStateException("failed")),
        )
        val viewModel = PlaylistPickerViewModel(repository)
        viewModel.open(listOf(TRACK))

        viewModel.addTo(PLAYLIST)
        runCurrent()

        assertEquals(PlaylistPickerSubmission.Idle, viewModel.state.value.submission)
        assertTrue(viewModel.state.value.visible)
        assertFalse(viewModel.state.value.pending)
    }

    @Test
    fun partialCreateShowsConfirmationAndEmitsSettingsWarning() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = FakePlaylistsRepository(
            createResult = Result.success(PlaylistCreationResult(settingsSynced = false)),
        )
        val viewModel = PlaylistPickerViewModel(repository)
        val warning = async(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.messages.first()
        }
        viewModel.open(listOf(TRACK))
        viewModel.showCreate()

        viewModel.create("Created", isPublic = true)
        runCurrent()

        assertEquals(PlaylistPickerSubmission.Success, viewModel.state.value.submission)
        assertTrue(viewModel.state.value.visible)
        assertFalse(viewModel.state.value.creating)
        assertEquals(AppError.PlaylistUpdateFailed, warning.await())
    }
}

private class FakePlaylistsRepository(
    private val addResult: Result<Unit> = Result.success(Unit),
    private val createResult: Result<PlaylistCreationResult> = Result.success(
        PlaylistCreationResult(settingsSynced = true),
    ),
    private val requestGate: CompletableDeferred<Unit>? = null,
) : PlaylistsRepository {
    override val playlists = MutableStateFlow<LoadableState<List<Playlist>>>(
        LoadableState.Content(listOf(PLAYLIST)),
    )
    val addCalls = mutableListOf<Pair<String, List<String>>>()

    override fun playlist(id: String): Flow<LoadableState<Playlist>> =
        MutableStateFlow(LoadableState.Content(PLAYLIST))

    override suspend fun loadPlaylists(forceRefresh: Boolean): Result<Unit> = Result.success(Unit)

    override suspend fun loadPlaylist(id: String, forceRefresh: Boolean): Result<Unit> =
        Result.success(Unit)

    override suspend fun createPlaylist(
        name: String,
        isPublic: Boolean,
        songIds: List<String>,
    ): Result<PlaylistCreationResult> = createResult

    override suspend fun updatePlaylist(
        id: String,
        name: String,
        isPublic: Boolean,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun addTracks(playlistId: String, songIds: List<String>): Result<Unit> {
        addCalls += playlistId to songIds
        requestGate?.await()
        return addResult
    }

    override suspend fun removeTracks(playlistId: String, songIndexes: List<Int>): Result<Unit> =
        Result.success(Unit)

    override suspend fun deletePlaylist(id: String): Result<Unit> = Result.success(Unit)

    override fun isEditable(playlist: Playlist): Boolean = true
}

private val PLAYLIST = Playlist(id = "playlist", name = "Playlist")

private val TRACK = Track(
    id = "track",
    title = "Track",
    artist = "Artist",
    albumId = null,
    artistId = null,
    trackNumber = 1,
    coverArtUrl = null,
    isFavorite = false,
)
