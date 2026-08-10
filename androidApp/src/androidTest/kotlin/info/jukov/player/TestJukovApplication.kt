package info.jukov.player

import androidx.room3.Room
import com.russhwolf.settings.MapSettings
import info.jukov.player.core.data.cache.CacheDatabase
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.di.AppGraph
import info.jukov.player.di.createAppGraph
import info.jukov.player.feature.auth.data.AuthStorageImpl
import info.jukov.player.feature.download.domain.OfflinePlatform
import info.jukov.player.feature.download.domain.OfflinePlatformFactory
import info.jukov.player.feature.playback.data.PlaybackStore
import info.jukov.player.feature.playback.data.SettingsPlaybackStore
import info.jukov.player.feature.playback.domain.PlaybackController
import info.jukov.player.feature.playback.domain.PlaybackControllerFactory
import info.jukov.player.feature.playback.domain.PlaybackOrigin
import info.jukov.player.feature.playback.domain.PlaybackSnapshot
import info.jukov.player.feature.track.domain.Track
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicBoolean

class TestJukovApplication : JukovApplication() {
    override fun createGraph(): AppGraph {
        val json = Json { ignoreUnknownKeys = true }
        return createAppGraph(
            playbackControllerFactory = TestPlaybackControllerFactory,
            cacheDatabaseBuilder = Room.inMemoryDatabaseBuilder<CacheDatabase>(),
            offlinePlatformFactory = OfflinePlatformFactory { _, _, _, _ -> TestOfflinePlatform },
            httpClient = testHttpClient(),
            authStorage = AuthStorageImpl(MapSettings()),
            playbackStore = SettingsPlaybackStore(json, MapSettings()),
        )
    }
}

private fun testHttpClient() = HttpClient(
    MockEngine { request ->
        val endpoint = request.url.encodedPath.substringAfterLast('/')
        val response = when {
            endpoint == "ping" && TestBackend.consumeRejectedLogin() -> AUTH_FAILURE_RESPONSE
            endpoint == "ping" -> PING_RESPONSE
            endpoint == "search3" -> TRACKS_RESPONSE
            else -> EMPTY_RESPONSE
        }
        respond(
            content = response,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    },
)

object TestBackend {
    private val rejectNextLogin = AtomicBoolean(false)

    fun rejectNextLogin() {
        rejectNextLogin.set(true)
    }

    fun reset() {
        rejectNextLogin.set(false)
    }

    fun consumeRejectedLogin(): Boolean = rejectNextLogin.compareAndSet(true, false)
}

private object TestPlaybackControllerFactory : PlaybackControllerFactory {
    override fun create(
        playbackStore: PlaybackStore,
        favoriteMutator: info.jukov.player.feature.favorite.domain.FavoriteMutator,
    ): PlaybackController = TestPlaybackController(playbackStore)
}

private class TestPlaybackController(
    private val store: PlaybackStore,
) : PlaybackController {
    private val mutableState = MutableStateFlow<LoadableState<PlaybackSnapshot>>(
        LoadableState.Content(PlaybackSnapshot()),
    )
    override val state = mutableState

    override fun play(tracks: List<Track>, startIndex: Int) {
        play(tracks, startIndex, PlaybackOrigin.TrackList)
    }

    override fun play(tracks: List<Track>, startIndex: Int, origin: PlaybackOrigin) {
        val queue = tracks.drop(startIndex)
        store.write(queue, currentIndex = 0, origin = origin)
        mutableState.value = LoadableState.Content(
            PlaybackSnapshot(
                queue = queue,
                currentIndex = 0,
                durationMs = queue.firstOrNull()?.durationMs ?: 0,
                isPlaying = true,
                origin = origin,
            ),
        )
    }

    override fun addToQueue(tracks: List<Track>) = Unit
    override fun playPause() = Unit
    override fun next() = Unit
    override fun previous() = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun stopAndClear() {
        store.clear()
        mutableState.value = LoadableState.Content(PlaybackSnapshot())
    }
}

private object TestOfflinePlatform : OfflinePlatform {
    override fun enqueue(accountKey: String) = Unit
    override fun recover(accountKey: String) = Unit
    override suspend fun cancelTrack(accountKey: String, trackId: String) = Unit
    override suspend fun cancelTracks(accountKey: String, trackIds: List<String>) = Unit
    override suspend fun cancelAccount(accountKey: String) = Unit
    override fun deleteTrack(accountKey: String, relativePath: String?) = Unit
    override fun deleteTracks(accountKey: String, relativePaths: List<String>) = Unit
    override fun deleteArtwork(accountKey: String, relativePath: String?) = Unit
    override fun deleteArtworks(accountKey: String, relativePaths: List<String>) = Unit
    override fun deleteAccount(accountKey: String) = Unit
    override fun fileUri(accountKey: String, relativePath: String) = "file:///$relativePath"
    override fun exists(accountKey: String, relativePath: String) = false
    override fun cleanupStaleParts(accountKey: String, activeTrackIds: Set<String>) = Unit
}

private const val PING_RESPONSE =
    """{"subsonic-response":{"status":"ok","type":"test","serverVersion":"1.0"}}"""

private const val TRACKS_RESPONSE =
    """{"subsonic-response":{"status":"ok","searchResult3":{"song":[{"id":"track-1","title":"Test Song","artist":"Test Artist","album":"Test Album","albumId":"album-1","artistId":"artist-1","duration":123,"contentType":"audio/mpeg"}]}}}"""

private const val EMPTY_RESPONSE =
    """{"subsonic-response":{"status":"ok"}}"""

private const val AUTH_FAILURE_RESPONSE =
    """{"subsonic-response":{"status":"failed","error":{"code":40}}}"""
