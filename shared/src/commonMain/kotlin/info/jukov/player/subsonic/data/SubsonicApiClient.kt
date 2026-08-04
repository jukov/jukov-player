package info.jukov.player.subsonic.data

import info.jukov.player.auth.domain.AuthSession
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json

class SubsonicApiClient(
    private val client: HttpClient,
    private val json: Json,
) {
    suspend fun <T> get(
        endpoint: String,
        session: AuthSession,
        deserializer: DeserializationStrategy<T>,
    ): T = json.decodeFromString(deserializer, request(endpoint, session))

    private suspend fun request(endpoint: String, session: AuthSession): String {
        val response = client.get("${session.serverUrl}/rest/$endpoint") {
            parameter("u", session.username)
            parameter("t", session.token)
            parameter("s", session.salt)
            parameter("v", API_VERSION)
            parameter("c", CLIENT_NAME)
            parameter("f", "json")
        }
        val body = response.bodyAsText()
        val subsonicResponse = runCatching {
            json.decodeFromString<SubsonicEnvelopeDto>(body).response
        }.getOrNull()
        val error = subsonicResponse?.error
        if (subsonicResponse?.status == "failed" && error != null) {
            throw SubsonicApiException(
                code = error.code,
                helpUrl = error.helpUrl,
                message = error.message ?: "OpenSubsonic вернул ошибку ${error.code}",
            )
        }
        check(response.status.isSuccess()) {
            "Сервер вернул ошибку ${response.status.value}"
        }
        checkNotNull(subsonicResponse) {
            "Сервер вернул некорректный ответ"
        }
        check(subsonicResponse.status == "ok") {
            "Неизвестный статус OpenSubsonic: ${subsonicResponse.status}"
        }
        return body
    }

    private companion object {
        const val API_VERSION = "1.16.1"
        const val CLIENT_NAME = "jukov_player"
    }
}
