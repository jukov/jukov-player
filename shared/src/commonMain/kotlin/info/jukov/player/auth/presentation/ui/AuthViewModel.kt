package info.jukov.player.auth.presentation.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.jukov.player.auth.domain.AuthRepository
import info.jukov.player.auth.domain.LoginUseCase
import info.jukov.player.auth.domain.LogoutUseCase
import info.jukov.player.auth.domain.AuthState
import info.jukov.player.core.presentation.LoadableState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    repository: AuthRepository,
    private val loginUseCase: LoginUseCase,
    private val logoutUseCase: LogoutUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(
        AuthUiState(auth = LoadableState.Content(repository.authState.value)),
    )
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.authState.collect { authState ->
                update { copy(auth = LoadableState.Content(authState)) }
            }
        }
    }

    fun setServer(value: String) = update { copy(server = value, auth = auth.withoutFailure()) }
    fun setUsername(value: String) = update { copy(username = value, auth = auth.withoutFailure()) }
    fun setPassword(value: String) = update { copy(password = value, auth = auth.withoutFailure()) }

    fun login() {
        if (_state.value.auth is LoadableState.Loading) return
        viewModelScope.launch {
            val current = _state.value
            update { copy(auth = LoadableState.Loading(auth.content)) }
            loginUseCase(current.server, current.username, current.password)
                .onSuccess { session ->
                    update {
                        copy(
                            password = "",
                            auth = LoadableState.Content(AuthState.LoggedIn(session)),
                        )
                    }
                }
                .onFailure { error ->
                    update {
                        copy(
                            auth = LoadableState.Failure(
                                message = error.message ?: "Не удалось войти",
                                content = auth.content,
                            ),
                        )
                    }
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            update { copy(auth = LoadableState.Loading(auth.content)) }
            logoutUseCase()
            update { copy(password = "", auth = LoadableState.Content(AuthState.LoggedOut)) }
        }
    }

    private fun update(block: AuthUiState.() -> AuthUiState) {
        _state.value = _state.value.block()
    }

    private fun LoadableState<AuthState>.withoutFailure(): LoadableState<AuthState> =
        if (this is LoadableState.Failure) {
            LoadableState.Content(content ?: AuthState.LoggedOut)
        } else {
            this
        }
}
