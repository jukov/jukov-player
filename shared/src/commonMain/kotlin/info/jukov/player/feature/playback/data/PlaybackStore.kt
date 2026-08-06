package info.jukov.player.feature.playback.data

import com.russhwolf.settings.Settings
import info.jukov.player.feature.track.domain.Track
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PersistedPlaybackState(
    val queue: List<Track>,
    val currentIndex: Int,
)

interface PlaybackStore {
    fun read(): PersistedPlaybackState?
    fun write(queue: List<Track>, currentIndex: Int)
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

    override fun write(queue: List<Track>, currentIndex: Int) {
        if (queue.isEmpty()) {
            clear()
            return
        }
        val state = PersistedPlaybackState(
            queue = queue,
            currentIndex = currentIndex.coerceIn(queue.indices),
        )
        settings.putString(STATE_KEY, json.encodeToString(state))
    }

    override fun updateCurrentIndex(currentIndex: Int) {
        val state = read() ?: return
        write(state.queue, currentIndex)
    }

    override fun clear() = settings.remove(STATE_KEY)

    private companion object {
        const val STATE_KEY = "playback.state"
    }
}
