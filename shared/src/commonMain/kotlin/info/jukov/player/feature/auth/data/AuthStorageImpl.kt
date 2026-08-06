package info.jukov.player.feature.auth.data

import com.russhwolf.settings.Settings
import info.jukov.player.feature.auth.domain.AuthSession

class AuthStorageImpl(
    private val settings: Settings = Settings(),
) : AuthStorage {
    override fun read(): AuthSession? {
        val server = settings.getStringOrNull(SERVER_KEY) ?: return null
        val username = settings.getStringOrNull(USERNAME_KEY) ?: return null
        val token = settings.getStringOrNull(TOKEN_KEY) ?: return null
        val salt = settings.getStringOrNull(SALT_KEY) ?: return null
        return AuthSession(
            server, username, token, salt,
            settings.getStringOrNull(SERVER_TYPE_KEY),
            settings.getStringOrNull(SERVER_VERSION_KEY),
        )
    }

    override fun write(session: AuthSession) {
        settings.putString(SERVER_KEY, session.serverUrl)
        settings.putString(USERNAME_KEY, session.username)
        settings.putString(TOKEN_KEY, session.token)
        settings.putString(SALT_KEY, session.salt)
        session.serverType?.let { settings.putString(SERVER_TYPE_KEY, it) }
        session.serverVersion?.let { settings.putString(SERVER_VERSION_KEY, it) }
    }

    override fun clear() {
        settings.remove(SERVER_KEY)
        settings.remove(USERNAME_KEY)
        settings.remove(TOKEN_KEY)
        settings.remove(SALT_KEY)
        settings.remove(SERVER_TYPE_KEY)
        settings.remove(SERVER_VERSION_KEY)
    }

    private companion object {
        const val SERVER_KEY = "auth.server"
        const val USERNAME_KEY = "auth.username"
        const val TOKEN_KEY = "auth.token"
        const val SALT_KEY = "auth.salt"
        const val SERVER_TYPE_KEY = "auth.serverType"
        const val SERVER_VERSION_KEY = "auth.serverVersion"
    }
}
