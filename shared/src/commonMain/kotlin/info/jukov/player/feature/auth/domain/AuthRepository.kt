package info.jukov.player.feature.auth.domain

import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val authState: StateFlow<AuthState>
    suspend fun login(serverUrl: String, username: String, password: String): Result<AuthSession>
    suspend fun logout()
}
