package info.jukov.player.auth.data

import info.jukov.player.auth.domain.AuthSession

interface AuthStorage {
    fun read(): AuthSession?
    fun write(session: AuthSession)
    fun clear()
}
