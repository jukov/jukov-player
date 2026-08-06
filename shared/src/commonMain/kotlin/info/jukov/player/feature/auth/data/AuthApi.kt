package info.jukov.player.feature.auth.data

import info.jukov.player.feature.auth.domain.AuthSession

interface AuthApi {
    suspend fun ping(session: AuthSession): ServerInfo
}

data class ServerInfo(val type: String?, val version: String?)
