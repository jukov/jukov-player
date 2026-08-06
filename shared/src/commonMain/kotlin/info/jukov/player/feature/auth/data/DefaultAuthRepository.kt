package info.jukov.player.feature.auth.data

import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.AppException

import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.feature.auth.domain.AuthState
import info.jukov.player.util.md5
import info.jukov.player.util.toHex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random
import info.jukov.player.core.data.cache.CacheDao
import info.jukov.player.feature.auth.domain.accountKey

class DefaultAuthRepository(
    private val api: AuthApi,
    private val storage: AuthStorage,
    private val cacheDao: CacheDao? = null,
) : AuthRepository {
    private val _authState = MutableStateFlow(
        storage.read()?.let(AuthState::LoggedIn) ?: AuthState.LoggedOut,
    )
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override suspend fun login(
        serverUrl: String,
        username: String,
        password: String,
    ): Result<AuthSession> = runCatching {
        val salt = Random.nextBytes(12).toHex()
        val session = AuthSession(
            serverUrl = serverUrl,
            username = username,
            token = md5(password + salt),
            salt = salt,
        )
        val serverInfo = api.ping(session)
        val enriched = session.copy(serverType = serverInfo.type, serverVersion = serverInfo.version)
        storage.write(enriched)
        _authState.value = AuthState.LoggedIn(enriched)
        enriched
    }

    override suspend fun logout() {
        (authState.value as? AuthState.LoggedIn)?.session?.let { cacheDao?.clearAccount(it.accountKey) }
        storage.clear()
        _authState.value = AuthState.LoggedOut
    }
}
