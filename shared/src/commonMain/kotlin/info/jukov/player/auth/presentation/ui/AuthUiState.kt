package info.jukov.player.auth.presentation.ui

import info.jukov.player.auth.domain.AuthState

data class AuthUiState(
    val authState: AuthState = AuthState.LoggedOut,
    val server: String = "https://music.jukov.info",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)
