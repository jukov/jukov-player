package info.jukov.player.subsonic.data

import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.AppException

import info.jukov.player.feature.auth.domain.AuthSession
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.Parameters
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
        parameters: Map<String, String> = emptyMap(),
    ): T = get(
        endpoint = endpoint,
        session = session,
        deserializer = deserializer,
        parameters = parameters.toParameters(),
    )

    suspend fun <T> get(
        endpoint: String,
        session: AuthSession,
        deserializer: DeserializationStrategy<T>,
        parameters: Parameters,
    ): T {
        val response = client.get(
            buildUrl(
                endpoint = endpoint,
                session = session,
                parameters = Parameters.build {
                    appendAll(parameters)
                    set("f", "json")
                },
            ),
        )
        val body = response.bodyAsText()
        validateResponse(response, body)
        return json.decodeFromString(deserializer, body)
    }

    private fun validateResponse(response: HttpResponse, body: String) {
        val subsonicResponse = runCatching {
            json.decodeFromString<SubsonicEnvelopeDto>(body).response
        }.getOrNull()
        val error = subsonicResponse?.error
        if (subsonicResponse?.status == "failed" && error != null) {
            throw AppException(AppError.OpenSubsonic(error.code))
        }
        if (!response.status.isSuccess()) throw AppException(AppError.Http(response.status.value))
        if (subsonicResponse == null) throw AppException(AppError.InvalidServerResponse)
        if (subsonicResponse.status != "ok") {
            throw AppException(AppError.UnknownOpenSubsonicStatus(subsonicResponse.status))
        }
    }

    fun buildUrl(
        endpoint: String,
        session: AuthSession,
        parameters: Map<String, String> = emptyMap(),
    ): String = buildUrl(endpoint, session, parameters.toParameters())

    fun buildUrl(
        endpoint: String,
        session: AuthSession,
        parameters: Parameters,
    ): String = URLBuilder("${session.serverUrl.trimEnd('/')}/rest/$endpoint").apply {
        this.parameters.append("u", session.username)
        this.parameters.append("t", session.token)
        this.parameters.append("s", session.salt)
        this.parameters.append("v", API_VERSION)
        this.parameters.append("c", CLIENT_NAME)
        this.parameters.appendAll(parameters)
    }.buildString()

    suspend fun <T> rawGet(
        endpoint: String,
        session: AuthSession,
        parameters: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        block: suspend (HttpResponse) -> T,
    ): T = client.prepareGet(buildUrl(endpoint, session, parameters)) {
        headers { headers.forEach { (name, value) -> append(name, value) } }
    }.execute(block)

    private fun Map<String, String>.toParameters(): Parameters = Parameters.build {
        this@toParameters.forEach { (key, value) -> append(key, value) }
    }

    private companion object {
        const val API_VERSION = "1.16.1"
        const val CLIENT_NAME = "jukov_player"
    }
}
