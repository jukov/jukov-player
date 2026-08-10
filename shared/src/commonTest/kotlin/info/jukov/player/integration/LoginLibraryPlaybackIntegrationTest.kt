package info.jukov.player.integration

import info.jukov.player.core.domain.LoadableState
import info.jukov.player.feature.auth.data.AuthStorage
import info.jukov.player.feature.auth.data.DefaultAuthRepository
import info.jukov.player.feature.auth.data.SubsonicAuthApi
import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.feature.auth.domain.AuthState
import info.jukov.player.feature.playback.domain.PlaybackController
import info.jukov.player.feature.playback.domain.PlaybackOrigin
import info.jukov.player.feature.playback.domain.PlaybackSnapshot
import info.jukov.player.feature.track.data.SubsonicTracksApi
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.track.domain.TracksFilter
import info.jukov.player.subsonic.data.SubsonicApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LoginLibraryPlaybackIntegrationTest {
    @Test
    fun loginLoadsTracksAndStartsSelectedTrack() = runTest {
        val client = SubsonicApiClient(testHttpClient(), Json { ignoreUnknownKeys = true })
        val authRepository = DefaultAuthRepository(
            api = SubsonicAuthApi(client),
            storage = InMemoryAuthStorage(),
        )

        val session = authRepository.login(
            serverUrl = "https://music.test",
            username = "listener",
            password = "secret",
        ).getOrThrow()
        val tracks = SubsonicTracksApi(client).getTracks(session, TracksFilter.All)
        val playback = RecordingPlaybackController()
        playback.play(tracks, startIndex = 0)

        assertIs<AuthState.LoggedIn>(authRepository.authState.value)
        assertEquals(listOf("Test Song"), tracks.map(Track::title))
        assertTrue(tracks.single().streamUrl?.contains("/rest/stream") == true)
        assertEquals(tracks, playback.playedTracks)
        assertEquals(0, playback.startIndex)
    }
}

private fun testHttpClient() = HttpClient(
    MockEngine { request ->
        val response = when (request.url.encodedPath.substringAfterLast('/')) {
            "ping" -> """{"subsonic-response":{"status":"ok","type":"test","serverVersion":"1.0"}}"""
            "search3" -> """{"subsonic-response":{"status":"ok","searchResult3":{"song":[{"id":"track-1","title":"Test Song","artist":"Test Artist","album":"Test Album","albumId":"album-1","artistId":"artist-1","duration":123,"contentType":"audio/mpeg"}]}}}"""
            else -> """{"subsonic-response":{"status":"ok"}}"""
        }
        respond(response, headers = headersOf(HttpHeaders.ContentType, "application/json"))
    },
)

private class InMemoryAuthStorage : AuthStorage {
    private var session: AuthSession? = null
    override fun read(): AuthSession? = session
    override fun write(session: AuthSession) {
        this.session = session
    }
    override fun clear() {
        session = null
    }
}

private class RecordingPlaybackController : PlaybackController {
    override val state = MutableStateFlow<LoadableState<PlaybackSnapshot>>(
        LoadableState.Content(PlaybackSnapshot()),
    )
    var playedTracks: List<Track> = emptyList()
    var startIndex: Int? = null

    override fun play(tracks: List<Track>, startIndex: Int) {
        playedTracks = tracks
        this.startIndex = startIndex
    }

    override fun play(tracks: List<Track>, startIndex: Int, origin: PlaybackOrigin) {
        play(tracks, startIndex)
    }

    override fun playPause() = Unit
    override fun next() = Unit
    override fun previous() = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun stopAndClear() = Unit
}
