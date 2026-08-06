package info.jukov.player.feature.auth.domain

data class AuthSession(
    val serverUrl: String,
    val username: String,
    val token: String,
    val salt: String,
)
