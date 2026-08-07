package info.jukov.player.feature.playback.data

import com.russhwolf.settings.MapSettings
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.playback.domain.PlaybackOrigin
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsPlaybackStoreTest {
    private val store = SettingsPlaybackStore(Json, MapSettings())

    @Test
    fun storesQueueAndCurrentTrackWithoutPosition() {
        val queue = listOf(track("one"), track("two"))

        store.write(queue, 1)

        assertEquals(PersistedPlaybackState(queue, 1), store.read())
    }

    @Test
    fun updatesOnlyCurrentTrackIndex() {
        val queue = listOf(track("one"), track("two"))
        val origin = PlaybackOrigin.Album("album-id")
        store.write(queue, 0, origin)

        store.updateCurrentIndex(1)

        assertEquals(PersistedPlaybackState(queue, 1, origin), store.read())
    }

    @Test
    fun storesPlaybackOrigin() {
        val queue = listOf(track("one"))
        val origin = PlaybackOrigin.Artist("artist-id")

        store.write(queue, 0, origin)

        assertEquals(PersistedPlaybackState(queue, 0, origin), store.read())
    }

    @Test
    fun emptyQueueClearsState() {
        store.write(listOf(track("one")), 0)

        store.write(emptyList(), 0)

        assertNull(store.read())
    }

    @Test
    fun storesReorderedQueueWithCurrentIndexAndOrigin() {
        val origin = PlaybackOrigin.Album("album-id")
        val queue = listOf(track("current"), track("three"), track("one"), track("two"))

        store.write(queue, currentIndex = 0, origin = origin)

        assertEquals(PersistedPlaybackState(queue, currentIndex = 0, origin = origin), store.read())
    }

    private companion object {
        fun track(id: String) = Track(
            id = id,
            title = id,
            artist = "Artist",
            albumId = null,
            artistId = null,
            trackNumber = null,
            coverArtUrl = "https://music.example.com/cover/$id",
            streamUrl = "https://music.example.com/stream/$id",
            isFavorite = false,
        )
    }
}
