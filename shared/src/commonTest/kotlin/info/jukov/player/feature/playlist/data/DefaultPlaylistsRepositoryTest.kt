package info.jukov.player.feature.playlist.data

import info.jukov.player.core.domain.LoadableState
import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.feature.auth.domain.AuthState
import info.jukov.player.feature.playlist.domain.Playlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultPlaylistsRepositoryTest {
    private val session = AuthSession("https://music.example.com", "user", "token", "salt")

    @Test
    fun cachedListIsSharedUntilForcedRefresh() = runTest {
        val api = FakePlaylistsApi()
        val repository = DefaultPlaylistsRepository(api, FakeAuthRepository(session))

        repository.loadPlaylists()
        repository.loadPlaylists()

        assertEquals(1, api.listRequests)
        assertEquals(listOf("First"), repository.playlists.value.content?.map(Playlist::name))

        api.playlists = listOf(Playlist("2", "Second"))
        repository.loadPlaylists(forceRefresh = true)

        assertEquals(2, api.listRequests)
        assertEquals(listOf("Second"), repository.playlists.value.content?.map(Playlist::name))
    }

    @Test
    fun creatingPlaylistRefreshesSharedList() = runTest {
        val api = FakePlaylistsApi()
        val repository = DefaultPlaylistsRepository(api, FakeAuthRepository(session))
        repository.loadPlaylists()

        repository.createPlaylist("Created")

        assertEquals(listOf("First", "Created"), repository.playlists.value.content?.map(Playlist::name))
        assertEquals(2, api.listRequests)
    }
}

private class FakePlaylistsApi : PlaylistsApi {
    var playlists = listOf(Playlist("1", "First"))
    var listRequests = 0

    override suspend fun getPlaylists(session: AuthSession): List<Playlist> {
        listRequests += 1
        return playlists
    }

    override suspend fun getPlaylist(session: AuthSession, id: String): Playlist =
        playlists.first { it.id == id }

    override suspend fun createPlaylist(
        session: AuthSession,
        name: String,
        songIds: List<String>,
    ) {
        playlists = playlists + Playlist((playlists.size + 1).toString(), name)
    }

    override suspend fun addTracks(session: AuthSession, playlistId: String, songIds: List<String>) = Unit
    override suspend fun removeTracks(session: AuthSession, playlistId: String, songIndexes: List<Int>) = Unit
    override suspend fun deletePlaylist(session: AuthSession, id: String) = Unit
}

private class FakeAuthRepository(session: AuthSession) : AuthRepository {
    override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.LoggedIn(session))
    override suspend fun login(serverUrl: String, username: String, password: String) =
        Result.failure<AuthSession>(UnsupportedOperationException())
    override suspend fun logout() = Unit
}
