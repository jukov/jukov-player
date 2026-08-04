package info.jukov.player.auth.data

import info.jukov.player.auth.domain.AuthSession

interface AuthApi {
    suspend fun ping(session: AuthSession)
}
