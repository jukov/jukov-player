package info.jukov.player.auth.domain

sealed interface AuthState {
    data object LoggedOut : AuthState
    data class LoggedIn(val session: AuthSession) : AuthState
}
