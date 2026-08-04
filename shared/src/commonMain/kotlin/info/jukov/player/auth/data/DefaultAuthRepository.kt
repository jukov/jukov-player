package info.jukov.player.auth.data

import info.jukov.player.auth.domain.AuthRepository
import info.jukov.player.auth.domain.AuthSession
import info.jukov.player.auth.domain.AuthState
import info.jukov.player.util.md5
import info.jukov.player.util.toHex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

class DefaultAuthRepository(
    private val api: AuthApi,
    private val storage: AuthStorage,
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
        check(api.ping(session)) { "Сервер отклонил авторизацию" }
        storage.write(session)
        _authState.value = AuthState.LoggedIn(session)
        session
    }

    override suspend fun logout() {
        storage.clear()
        _authState.value = AuthState.LoggedOut
    }
}
