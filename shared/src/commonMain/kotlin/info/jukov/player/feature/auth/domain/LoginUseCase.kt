package info.jukov.player.feature.auth.domain

import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.AppException

class LoginUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(serverUrl: String, username: String, password: String): Result<AuthSession> {
        val server = serverUrl.trim().trimEnd('/').let {
            if (it.isNotEmpty() && !it.startsWith("http://") && !it.startsWith("https://")) "https://$it" else it
        }
        if (server.isBlank()) return Result.failure(AppException(AppError.ServerAddressRequired))
        if (username.isBlank()) return Result.failure(AppException(AppError.UsernameRequired))
        if (password.isBlank()) return Result.failure(AppException(AppError.PasswordRequired))
        return repository.login(server, username.trim(), password)
    }
}
