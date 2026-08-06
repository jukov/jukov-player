package info.jukov.player.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.feature.auth.domain.AuthState
import info.jukov.player.feature.auth.domain.LoginUseCase
import info.jukov.player.feature.auth.domain.LogoutUseCase
import info.jukov.player.core.presentation.LoadableState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
                _state.update { it.copy(auth = LoadableState.Content(authState)) }
            }
        }
    }

    fun setServer(value: String) = _state.update {
        it.copy(
            server = value,
            auth = it.auth.withoutFailure(),
        )
    }
    fun setUsername(value: String) = _state.update {
        it.copy(
            username = value,
            auth = it.auth.withoutFailure(),
        )
    }
    fun setPassword(value: String) = _state.update {
        it.copy(
            password = value,
            auth = it.auth.withoutFailure(),
        )
    }

    fun login() {
        if (_state.value.auth is LoadableState.Loading) return
        viewModelScope.launch {
            val current = _state.value
            _state.update { it.copy(auth = LoadableState.Loading(it.auth.content)) }
            loginUseCase(current.server, current.username, current.password)
                .onSuccess { session ->
                    _state.update {
                        it.copy(
                            password = "",
                            auth = LoadableState.Content(AuthState.LoggedIn(session)),
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            auth = LoadableState.Failure(
                                message = error.message ?: "Не удалось войти",
                                content = it.auth.content,
                            ),
                        )
                    }
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _state.update { it.copy(auth = LoadableState.Loading(it.auth.content)) }
            logoutUseCase()
            _state.update {
                it.copy(password = "", auth = LoadableState.Content(AuthState.LoggedOut))
            }
        }
    }

    private fun LoadableState<AuthState>.withoutFailure(): LoadableState<AuthState> =
        if (this is LoadableState.Failure) {
            LoadableState.Content(content ?: AuthState.LoggedOut)
        } else {
            this
        }
}
