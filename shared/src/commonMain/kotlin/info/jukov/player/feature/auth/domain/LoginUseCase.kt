package info.jukov.player.feature.auth.domain

class LoginUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(serverUrl: String, username: String, password: String): Result<AuthSession> {
        val server = serverUrl.trim().trimEnd('/').let {
            if (it.isNotEmpty() && !it.startsWith("http://") && !it.startsWith("https://")) "https://$it" else it
        }
        if (server.isBlank()) return Result.failure(IllegalArgumentException("Укажите адрес сервера"))
        if (username.isBlank()) return Result.failure(IllegalArgumentException("Укажите логин"))
        if (password.isBlank()) return Result.failure(IllegalArgumentException("Укажите пароль"))
        return repository.login(server, username.trim(), password)
    }
}
