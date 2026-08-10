package info.jukov.player.subsonic.data

import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.AppException
import info.jukov.player.feature.auth.domain.AuthSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Parameters
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class SubsonicApiClientTest {
    @Test
    fun buildUrlIncludesAuthenticationAndKeepsRepeatedParameters() {
        val client = SubsonicApiClient(HttpClient(), Json)
        val session = AuthSession("https://music.example.com", "user", "token", "salt")

        val url = io.ktor.http.Url(
            client.buildUrl(
                endpoint = "star",
                session = session,
                parameters = Parameters.build {
                    append("id", "track-1")
                    append("id", "track-2")
                },
            ),
        )

        assertEquals("user", url.parameters["u"])
        assertEquals("token", url.parameters["t"])
        assertEquals("salt", url.parameters["s"])
        assertEquals("1.16.1", url.parameters["v"])
        assertEquals("jukov_player", url.parameters["c"])
        assertEquals(listOf("track-1", "track-2"), url.parameters.getAll("id"))
    }

    @Test
    fun successfulEnvelopeIsDeserialized() = runTest {
        val client = client(
            """{"subsonic-response":{"status":"ok","type":"navidrome"}}""",
        )

        val response = client.get(
            endpoint = "ping",
            session = SESSION,
            deserializer = SubsonicEnvelopeDto.serializer(),
        )

        assertEquals("navidrome", response.response.type)
    }

    @Test
    fun openSubsonicFailureIsMappedBeforeHttpStatus() = runTest {
        val client = client(
            body = """{"subsonic-response":{"status":"failed","error":{"code":40}}}""",
            status = HttpStatusCode.Unauthorized,
        )

        val exception = assertFailsWith<AppException> {
            client.get("ping", SESSION, SubsonicEnvelopeDto.serializer())
        }

        assertEquals(AppError.OpenSubsonic(40), exception.error)
    }

    @Test
    fun httpFailureWithoutSubsonicErrorIsMapped() = runTest {
        val client = client(
            body = """{"subsonic-response":{"status":"ok"}}""",
            status = HttpStatusCode.ServiceUnavailable,
        )

        val exception = assertFailsWith<AppException> {
            client.get("ping", SESSION, SubsonicEnvelopeDto.serializer())
        }

        assertEquals(AppError.Http(503), exception.error)
    }

    @Test
    fun malformedBodyIsRejected() = runTest {
        val exception = assertFailsWith<AppException> {
            client("not-json").get("ping", SESSION, SubsonicEnvelopeDto.serializer())
        }

        assertEquals(AppError.InvalidServerResponse, exception.error)
    }

    @Test
    fun unknownEnvelopeStatusIsRejected() = runTest {
        val exception = assertFailsWith<AppException> {
            client("""{"subsonic-response":{"status":"maintenance"}}""").get(
                "ping",
                SESSION,
                SubsonicEnvelopeDto.serializer(),
            )
        }

        assertEquals(AppError.UnknownOpenSubsonicStatus("maintenance"), exception.error)
    }

    private fun client(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): SubsonicApiClient = SubsonicApiClient(
        HttpClient(
            MockEngine {
                respond(
                    content = body,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ),
        Json { ignoreUnknownKeys = true },
    )

    private companion object {
        val SESSION = AuthSession("https://music.example.com", "user", "token", "salt")
    }
}
