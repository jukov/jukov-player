package info.jukov.player.feature.playlist.data

import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.feature.auth.domain.AuthState
import info.jukov.player.feature.auth.domain.accountKey
import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.domain.toAppError
import info.jukov.player.feature.playlist.domain.Playlist
import info.jukov.player.feature.playlist.domain.PlaylistsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DefaultPlaylistsRepository(
    private val api: PlaylistsApi,
    private val authRepository: AuthRepository,
) : PlaylistsRepository {
    private val _playlists = MutableStateFlow<LoadableState<List<Playlist>>>(LoadableState.Loading(null))
    override val playlists = _playlists.asStateFlow()
    private val details = MutableStateFlow<Map<String, LoadableState<Playlist>>>(emptyMap())
    private val loadMutex = Mutex()
    private var accountKey: String? = null

    private fun session() = (authRepository.authState.value as? AuthState.LoggedIn)?.session
        ?: error("Authentication required")

    override fun playlist(id: String) = details.map {
        it[id] ?: LoadableState.Loading(content = null)
    }.distinctUntilChanged()

    override suspend fun loadPlaylists(forceRefresh: Boolean): Result<Unit> = loadMutex.withLock {
        val session = session().also(::ensureAccount)
        if (!forceRefresh && _playlists.value.content != null) {
            return@withLock Result.success(Unit)
        }
        val old = _playlists.value.content
        _playlists.value = LoadableState.Loading(old)
        runCatching { api.getPlaylists(session) }
            .onSuccess { items ->
                if (accountKey == session.accountKey) {
                    _playlists.value = LoadableState.Content(items)
                }
            }
            .onFailure { error ->
                if (accountKey == session.accountKey) {
                    _playlists.value = LoadableState.Failure(
                        error.toAppError(AppError.PlaylistsLoadFailed),
                        old,
                    )
                }
            }
            .map { Unit }
    }

    override suspend fun loadPlaylist(id: String, forceRefresh: Boolean): Result<Unit> =
        loadMutex.withLock {
            val session = session().also(::ensureAccount)
            val current = details.value[id]
            if (!forceRefresh && current?.content != null) {
                return@withLock Result.success(Unit)
            }
            val old = current?.content
            updateDetail(id, LoadableState.Loading(old))
            runCatching { api.getPlaylist(session, id) }
                .onSuccess { playlist ->
                    if (accountKey == session.accountKey) {
                        updateDetail(id, LoadableState.Content(playlist))
                    }
                }
                .onFailure { error ->
                    if (accountKey == session.accountKey) {
                        updateDetail(
                            id,
                            LoadableState.Failure(
                                error.toAppError(AppError.PlaylistLoadFailed),
                                old,
                            ),
                        )
                    }
                }
                .map { Unit }
        }

    override suspend fun createPlaylist(
        name: String,
        isPublic: Boolean,
        songIds: List<String>,
    ): Result<Unit> =
        runCatching { api.createPlaylist(session(), name, isPublic, songIds) }
            .onSuccess { loadPlaylists(forceRefresh = true) }

    override suspend fun updatePlaylist(id: String, name: String, isPublic: Boolean): Result<Unit> =
        runCatching { api.updatePlaylist(session(), id, name, isPublic) }
            .onSuccess {
                loadPlaylist(id, forceRefresh = true)
                loadPlaylists(forceRefresh = true)
            }

    override suspend fun addTracks(playlistId: String, songIds: List<String>): Result<Unit> =
        runCatching { api.addTracks(session(), playlistId, songIds) }
            .onSuccess {
                loadPlaylist(playlistId, forceRefresh = true)
                loadPlaylists(forceRefresh = true)
            }

    override suspend fun removeTracks(playlistId: String, songIndexes: List<Int>): Result<Unit> =
        runCatching { api.removeTracks(session(), playlistId, songIndexes) }
            .onSuccess {
                loadPlaylist(playlistId, forceRefresh = true)
                loadPlaylists(forceRefresh = true)
            }

    override suspend fun deletePlaylist(id: String): Result<Unit> =
        runCatching { api.deletePlaylist(session(), id) }
            .onSuccess {
                details.value = details.value - id
                loadPlaylists(forceRefresh = true)
            }

    override fun isEditable(playlist: Playlist): Boolean {
        val username = (authRepository.authState.value as? AuthState.LoggedIn)?.session?.username ?: return false
        return playlist.isEditableBy(username)
    }

    private fun ensureAccount(session: info.jukov.player.feature.auth.domain.AuthSession) {
        if (accountKey != session.accountKey) {
            accountKey = session.accountKey
            _playlists.value = LoadableState.Loading(content = null)
            details.value = emptyMap()
        }
    }

    private fun updateDetail(id: String, state: LoadableState<Playlist>) {
        details.value = details.value + (id to state)
    }
}
