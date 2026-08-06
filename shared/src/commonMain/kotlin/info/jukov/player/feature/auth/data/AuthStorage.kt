package info.jukov.player.feature.auth.data

import info.jukov.player.feature.auth.domain.AuthSession

interface AuthStorage {
    fun read(): AuthSession?
    fun write(session: AuthSession)
    fun clear()
}
