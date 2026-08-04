package info.jukov.player.auth.presentation.ui

import info.jukov.player.auth.domain.AuthState
import info.jukov.player.core.presentation.LoadableState

data class AuthUiState(
    val auth: LoadableState<AuthState> = LoadableState.Content(AuthState.LoggedOut),
    val server: String = "https://music.jukov.info",
    val username: String = "",
    val password: String = "",
)
