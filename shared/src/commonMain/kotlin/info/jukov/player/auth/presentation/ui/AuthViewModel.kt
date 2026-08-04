package info.jukov.player.auth.presentation.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.jukov.player.auth.domain.AuthRepository
import info.jukov.player.auth.domain.LoginUseCase
import info.jukov.player.auth.domain.LogoutUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    repository: AuthRepository,
    private val loginUseCase: LoginUseCase,
    private val logoutUseCase: LogoutUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState(authState = repository.authState.value))
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.authState.collect { authState ->
                update { copy(authState = authState) }
            }
        }
    }

    fun setServer(value: String) = update { copy(server = value, error = null) }
    fun setUsername(value: String) = update { copy(username = value, error = null) }
    fun setPassword(value: String) = update { copy(password = value, error = null) }

    fun login() {
        if (_state.value.isLoading) return
        viewModelScope.launch {
            update { copy(isLoading = true, error = null) }
            val current = _state.value
            loginUseCase(current.server, current.username, current.password)
                .onSuccess {
                    update {
                        copy(
                            password = "",
                            isLoading = false,
                        )
                    }
                }
                .onFailure { error ->
                    update { copy(isLoading = false, error = error.message ?: "Не удалось войти") }
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
            update { copy(password = "", isLoading = false, error = null) }
        }
    }

    private fun update(block: AuthUiState.() -> AuthUiState) {
        _state.value = _state.value.block()
    }
}
