package info.jukov.player.auth.data

import com.russhwolf.settings.Settings
import info.jukov.player.auth.domain.AuthSession

class AuthStorageImpl(
    private val settings: Settings = Settings(),
) : AuthStorage {
    override fun read(): AuthSession? {
        val server = settings.getStringOrNull(SERVER_KEY) ?: return null
        val username = settings.getStringOrNull(USERNAME_KEY) ?: return null
        val token = settings.getStringOrNull(TOKEN_KEY) ?: return null
        val salt = settings.getStringOrNull(SALT_KEY) ?: return null
        return AuthSession(server, username, token, salt)
    }

    override fun write(session: AuthSession) {
        settings.putString(SERVER_KEY, session.serverUrl)
        settings.putString(USERNAME_KEY, session.username)
        settings.putString(TOKEN_KEY, session.token)
        settings.putString(SALT_KEY, session.salt)
    }

    override fun clear() {
        settings.remove(SERVER_KEY)
        settings.remove(USERNAME_KEY)
        settings.remove(TOKEN_KEY)
        settings.remove(SALT_KEY)
    }

    private companion object {
        const val SERVER_KEY = "auth.server"
        const val USERNAME_KEY = "auth.username"
        const val TOKEN_KEY = "auth.token"
        const val SALT_KEY = "auth.salt"
    }
}
