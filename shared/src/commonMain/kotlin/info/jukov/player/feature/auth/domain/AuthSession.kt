package info.jukov.player.feature.auth.domain

data class AuthSession(
    val serverUrl: String,
    val username: String,
    val token: String,
    val salt: String,
    val serverType: String? = null,
    val serverVersion: String? = null,
)

val AuthSession.accountKey: String
    get() = "${serverUrl.trim().trimEnd('/').lowercase()}|${username.lowercase()}"
