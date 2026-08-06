package info.jukov.player.feature.auth.domain

sealed interface AuthState {
    data object LoggedOut : AuthState
    data class LoggedIn(val session: AuthSession) : AuthState
}
