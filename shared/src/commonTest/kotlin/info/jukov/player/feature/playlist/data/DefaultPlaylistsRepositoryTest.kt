package info.jukov.player.feature.playlist.data

import info.jukov.player.core.domain.LoadableState
import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.feature.auth.domain.AuthState
import info.jukov.player.feature.playlist.domain.Playlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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

        val result = repository.createPlaylist("Created", isPublic = true).getOrThrow()

        assertEquals(true, result.settingsSynced)
        assertEquals(listOf("First", "Created"), repository.playlists.value.content?.map(Playlist::name))
        assertEquals(true, repository.playlists.value.content?.last()?.isPublic)
        assertEquals(2, api.listRequests)
        assertEquals(1, api.updateRequests)
    }

    @Test
    fun emptyCreateResponseResolvesNewPlaylistBeforeUpdatingVisibility() = runTest {
        val api = FakePlaylistsApi().apply { createReturnsPlaylist = false }
        val repository = DefaultPlaylistsRepository(api, FakeAuthRepository(session))
        repository.loadPlaylists()

        val result = repository.createPlaylist("Created", isPublic = true).getOrThrow()

        assertEquals(true, result.settingsSynced)
        assertEquals(true, repository.playlists.value.content?.last()?.isPublic)
        assertEquals(3, api.listRequests)
        assertEquals(1, api.updateRequests)
    }

    @Test
    fun failedVisibilityUpdateReportsPartialSuccessWithoutFailingCreation() = runTest {
        val api = FakePlaylistsApi().apply { failSettingsUpdate = true }
        val repository = DefaultPlaylistsRepository(api, FakeAuthRepository(session))
        repository.loadPlaylists()

        val result = repository.createPlaylist("Created", isPublic = true).getOrThrow()

        assertEquals(false, result.settingsSynced)
        assertEquals(listOf("First", "Created"), repository.playlists.value.content?.map(Playlist::name))
        assertEquals(false, repository.playlists.value.content?.last()?.isPublic)
        assertEquals(1, api.updateRequests)
    }

    @Test
    fun updatingPlaylistRefreshesDetailAndSharedList() = runTest {
        val api = FakePlaylistsApi()
        val repository = DefaultPlaylistsRepository(api, FakeAuthRepository(session))
        repository.loadPlaylists()
        repository.loadPlaylist("1")

        repository.updatePlaylist("1", "Renamed", isPublic = true)

        assertEquals("Renamed", repository.playlists.value.content?.single()?.name)
        assertEquals(true, repository.playlists.value.content?.single()?.isPublic)
        assertEquals("Renamed", repository.playlist("1").first().content?.name)
        assertEquals(true, repository.playlist("1").first().content?.isPublic)
        assertEquals(2, api.listRequests)
        assertEquals(2, api.detailRequests)
    }
}

private class FakePlaylistsApi : PlaylistsApi {
    var playlists = listOf(Playlist("1", "First"))
    var listRequests = 0
    var detailRequests = 0
    var updateRequests = 0
    var createReturnsPlaylist = true
    var failSettingsUpdate = false

    override suspend fun getPlaylists(session: AuthSession): List<Playlist> {
        listRequests += 1
        return playlists
    }

    override suspend fun getPlaylist(session: AuthSession, id: String): Playlist {
        detailRequests += 1
        return playlists.first { it.id == id }
    }

    override suspend fun createPlaylist(
        session: AuthSession,
        name: String,
        songIds: List<String>,
    ): Playlist? {
        val created = Playlist(
            id = (playlists.size + 1).toString(),
            name = name,
        )
        playlists = playlists + created
        return created.takeIf { createReturnsPlaylist }
    }

    override suspend fun updatePlaylist(
        session: AuthSession,
        id: String,
        name: String,
        isPublic: Boolean,
    ) {
        updateRequests += 1
        if (failSettingsUpdate) {
            error("Settings update failed")
        }
        playlists = playlists.map { playlist ->
            if (playlist.id == id) {
                playlist.copy(name = name, isPublic = isPublic)
            } else {
                playlist
            }
        }
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
