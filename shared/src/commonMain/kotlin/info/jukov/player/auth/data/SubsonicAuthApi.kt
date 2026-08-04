package info.jukov.player.auth.data

import info.jukov.player.auth.domain.AuthSession
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

class SubsonicAuthApi(private val client: HttpClient) : AuthApi {
    override suspend fun ping(session: AuthSession) {
        val response = client.get("${session.serverUrl}/rest/ping") {
            parameter("u", session.username)
            parameter("t", session.token)
            parameter("s", session.salt)
            parameter("v", "1.16.1")
            parameter("c", "jukov_player")
            parameter("f", "json")
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess() || !body.contains(STATUS_OK_PATTERN)) {
            val message = ERROR_MESSAGE_PATTERN.find(body)?.groupValues?.get(1)
            throw IllegalStateException(message ?: "Сервер отклонил авторизацию (${response.status.value})")
        }
    }

    private companion object {
        val STATUS_OK_PATTERN = Regex("\"status\"\\s*:\\s*\"ok\"")
        val ERROR_MESSAGE_PATTERN = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"")
    }
}
