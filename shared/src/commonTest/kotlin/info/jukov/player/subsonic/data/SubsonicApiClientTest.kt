package info.jukov.player.subsonic.data

import info.jukov.player.feature.auth.domain.AuthSession
import io.ktor.client.HttpClient
import io.ktor.http.Parameters
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SubsonicApiClientTest {
    @Test
    fun buildUrlKeepsRepeatedParameters() {
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

        assertEquals(listOf("track-1", "track-2"), url.parameters.getAll("id"))
    }
}
