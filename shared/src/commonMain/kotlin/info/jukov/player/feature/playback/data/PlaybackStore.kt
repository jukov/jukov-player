package info.jukov.player.feature.playback.data

import com.russhwolf.settings.Settings
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.playback.domain.PlaybackOrigin
import info.jukov.player.feature.playback.domain.RepeatMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PersistedPlaybackState(
    val queue: List<Track>,
    val currentIndex: Int,
    val origin: PlaybackOrigin = PlaybackOrigin.TrackList,
    val canonicalQueue: List<Track>? = null,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
)

interface PlaybackStore {
    fun read(): PersistedPlaybackState?
    fun write(
        queue: List<Track>,
        currentIndex: Int,
        origin: PlaybackOrigin = PlaybackOrigin.TrackList,
    )
    fun writePlaybackState(state: PersistedPlaybackState) {
        write(state.queue, state.currentIndex, state.origin)
    }
    fun updateCurrentIndex(currentIndex: Int)
    fun clear()
}

class SettingsPlaybackStore(
    private val json: Json,
    private val settings: Settings = Settings(),
) : PlaybackStore {
    override fun read(): PersistedPlaybackState? = settings.getStringOrNull(STATE_KEY)?.let { value ->
        runCatching { json.decodeFromString<PersistedPlaybackState>(value) }.getOrNull()
    }

    override fun write(queue: List<Track>, currentIndex: Int, origin: PlaybackOrigin) {
        if (queue.isEmpty()) {
            clear()
            return
        }
        val state = PersistedPlaybackState(
            queue = queue,
            currentIndex = currentIndex.coerceIn(queue.indices),
            origin = origin,
        )
        settings.putString(STATE_KEY, json.encodeToString(state))
    }

    override fun writePlaybackState(state: PersistedPlaybackState) {
        if (state.queue.isEmpty()) {
            clear()
            return
        }
        settings.putString(
            STATE_KEY,
            json.encodeToString(state.copy(currentIndex = state.currentIndex.coerceIn(state.queue.indices))),
        )
    }

    override fun updateCurrentIndex(currentIndex: Int) {
        val state = read() ?: return
        writePlaybackState(state.copy(currentIndex = currentIndex))
    }

    override fun clear() = settings.remove(STATE_KEY)

    private companion object {
        const val STATE_KEY = "playback.state"
    }
}
