package info.jukov.player.feature.auth.presentation

import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.feature.auth.domain.AuthState
import info.jukov.player.feature.auth.domain.LoginUseCase
import info.jukov.player.feature.auth.domain.LogoutUseCase
import info.jukov.player.feature.download.domain.DownloadsRepository
import info.jukov.player.feature.download.domain.OfflineLibrary
import info.jukov.player.feature.download.domain.OfflineTrack
import info.jukov.player.feature.download.domain.DownloadStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    @AfterTest
    fun resetMainDispatcher() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun initialStateHasEmptyServerUrl() {
        assertEquals("", AuthUiState().server)
    }

    @Test
    fun successfulLoginClearsPasswordAndPublishesSession() = runTest {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = FakeAuthRepository()
        val viewModel = viewModel(repository)
        viewModel.setServer("https://music.test")
        viewModel.setUsername("listener")
        viewModel.setPassword("secret")

        viewModel.login()
        advanceUntilIdle()

        assertEquals("", viewModel.state.value.password)
        assertEquals(AuthState.LoggedIn(SESSION), viewModel.state.value.auth.content)
    }

    @Test
    fun failedLoginPreservesFormAndPublishesAuthenticationError() = runTest {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = FakeAuthRepository(loginFailure = IllegalStateException("rejected"))
        val viewModel = viewModel(repository)
        viewModel.setServer("https://music.test")
        viewModel.setUsername("listener")
        viewModel.setPassword("secret")

        viewModel.login()
        advanceUntilIdle()

        val failure = assertIs<LoadableState.Failure<AuthState>>(viewModel.state.value.auth)
        assertEquals(AppError.AuthenticationRejected, failure.error)
        assertEquals("secret", viewModel.state.value.password)
    }

    private fun viewModel(repository: AuthRepository) = AuthViewModel(
        repository,
        LoginUseCase(repository),
        LogoutUseCase(repository, EmptyDownloadsRepository),
    )

    private class FakeAuthRepository(
        private val loginFailure: Throwable? = null,
    ) : AuthRepository {
        private val mutableState = MutableStateFlow<AuthState>(AuthState.LoggedOut)
        override val authState = mutableState

        override suspend fun login(
            serverUrl: String,
            username: String,
            password: String,
        ): Result<AuthSession> {
            loginFailure?.let { return Result.failure(it) }
            mutableState.value = AuthState.LoggedIn(SESSION)
            return Result.success(SESSION)
        }

        override suspend fun logout() {
            mutableState.value = AuthState.LoggedOut
        }
    }

    private companion object {
        val SESSION = AuthSession("https://music.test", "listener", "token", "salt")
    }
}

private object EmptyDownloadsRepository : DownloadsRepository {
    override fun observeLibrary(): Flow<OfflineLibrary> = flowOf(OfflineLibrary())
    override fun searchLibrary(query: String): Flow<OfflineLibrary> = flowOf(OfflineLibrary())
    override fun observeTrackStatuses(): Flow<Map<String, DownloadStatus>> = flowOf(emptyMap())
    override fun observeAlbumStatuses(): Flow<Map<String, DownloadStatus>> = flowOf(emptyMap())
    override fun observeAlbumTracks(albumId: String): Flow<List<OfflineTrack>> = flowOf(emptyList())
    override suspend fun downloadTrack(track: info.jukov.player.feature.track.domain.Track) = Unit
    override suspend fun downloadAlbum(album: info.jukov.player.feature.album.domain.Album) = Unit
    override suspend fun cancelTrack(trackId: String) = Unit
    override suspend fun removeTracks(trackIds: List<String>) = Unit
    override suspend fun cancelAlbum(albumId: String) = Unit
    override suspend fun retryTrack(trackId: String) = Unit
    override suspend fun clearCurrentAccount() = Unit
    override suspend fun reconcile() = Unit
    override suspend fun localTrackUri(trackId: String): String? = null
    override suspend fun localTrackUris(trackIds: List<String>): Map<String, String> = emptyMap()
    override suspend fun localArtworkUri(coverArtId: String?): String? = null
    override suspend fun localArtworkUris(coverArtIds: List<String>): Map<String, String> = emptyMap()
}
