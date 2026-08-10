package info.jukov.player.feature.playback.domain

import info.jukov.player.feature.playback.data.PersistedPlaybackState
import info.jukov.player.feature.playback.data.PlaybackStore
import info.jukov.player.feature.track.domain.Track
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackFavoriteStateTest {
    @Test
    fun updateTrackFavoriteChangesOnlyMatchingQueueItem() {
        val store = RecordingStore(
            PersistedPlaybackState(
                queue = listOf(track("one"), track("two")),
                currentIndex = 1,
            ),
        )

        store.updateTrackFavorite("two", isFavorite = true)

        assertFalse(store.saved.queue[0].isFavorite)
        assertTrue(store.saved.queue[1].isFavorite)
        assertTrue(store.saved.currentIndex == 1)
    }

    private fun track(id: String) = Track(
        id = id,
        title = id,
        artist = "Artist",
        albumId = null,
        artistId = null,
        trackNumber = null,
        coverArtUrl = null,
        isFavorite = false,
    )
}

private class RecordingStore(initial: PersistedPlaybackState) : PlaybackStore {
    var saved = initial

    override fun read(): PersistedPlaybackState = saved

    override fun write(
        queue: List<Track>,
        currentIndex: Int,
        origin: PlaybackOrigin,
    ) {
        saved = PersistedPlaybackState(queue, currentIndex, origin)
    }

    override fun updateCurrentIndex(currentIndex: Int) {
        saved = saved.copy(currentIndex = currentIndex)
    }

    override fun clear() = Unit
}
