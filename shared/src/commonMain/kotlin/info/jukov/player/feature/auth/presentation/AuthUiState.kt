package info.jukov.player.feature.auth.presentation

import info.jukov.player.feature.auth.domain.AuthState
import info.jukov.player.core.domain.LoadableState

data class AuthUiState(
    val auth: LoadableState<AuthState> = LoadableState.Content(AuthState.LoggedOut),
    val server: String = "https://music.jukov.info",
    val username: String = "",
    val password: String = "",
)
