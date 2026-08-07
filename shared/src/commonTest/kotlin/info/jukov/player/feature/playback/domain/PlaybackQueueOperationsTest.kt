package info.jukov.player.feature.playback.domain

import info.jukov.player.feature.track.domain.Track
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackQueueOperationsTest {
    private val queue = listOf("played", "current", "one", "two", "three").map(::track)

    @Test
    fun movesOnlyFutureTrack() {
        assertEquals(
            listOf("played", "current", "three", "one", "two"),
            moveFutureQueueItem(queue, currentIndex = 1, fromIndex = 4, toIndex = 2).ids(),
        )
        assertEquals(queue, moveFutureQueueItem(queue, currentIndex = 1, fromIndex = 1, toIndex = 3))
        assertEquals(queue, moveFutureQueueItem(queue, currentIndex = 1, fromIndex = 3, toIndex = 1))
    }

    @Test
    fun removesOnlyFutureTracks() {
        assertEquals(
            listOf("played", "current", "two"),
            removeFutureQueueItems(queue, currentIndex = 1, indices = setOf(0, 1, 2, 4)).ids(),
        )
    }

    @Test
    fun movesSelectedTracksAfterCurrentAndPreservesTheirOrder() {
        assertEquals(
            listOf("played", "current", "two", "three", "one"),
            moveFutureQueueItemsToTop(queue, currentIndex = 1, indices = setOf(4, 3, 0, 1)).ids(),
        )
    }

    @Test
    fun invalidIndicesLeaveQueueUnchanged() {
        assertEquals(queue, removeFutureQueueItems(queue, currentIndex = 1, indices = setOf(-1, 10)))
        assertEquals(queue, moveFutureQueueItemsToTop(queue, currentIndex = 1, indices = setOf(0, 1, 10)))
    }

    private fun List<Track>.ids() = map(Track::id)

    private fun track(id: String) = Track(
        id = id,
        title = id,
        artist = "Artist",
        albumId = null,
        artistId = null,
        trackNumber = null,
        coverArtUrl = null,
        streamUrl = "https://music.example.com/$id",
        isFavorite = false,
    )
}
