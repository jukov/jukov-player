package info.jukov.player.feature.playback.domain

import info.jukov.player.feature.track.domain.Track
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackModeOperationsTest {
    private val queue = List(6) { track("track-$it") }

    @Test
    fun shuffleKeepsPlayedPrefixAndCanRestoreCanonicalOrder() {
        val shuffled = enableShuffle(queue, currentIndex = 1, random = Random(7))

        assertEquals(queue.take(2), shuffled.queue.take(2))
        assertEquals(queue.drop(2).toSet(), shuffled.queue.drop(2).toSet())
        assertEquals(queue, disableShuffle(shuffled.queue, shuffled.canonicalQueue, 1).queue)
    }

    @Test
    fun canonicalOrderKeepsAdditionsAndRemovalsWhileShuffled() {
        val shuffled = enableShuffle(queue, currentIndex = 0, random = Random(9))
        val changed = shuffled.queue.toMutableList().apply { removeAt(2) } + track("added")
        val canonical = updateCanonicalQueue(shuffled.queue, changed, shuffled.canonicalQueue, 0)

        val restored = disableShuffle(changed, canonical, 0).queue

        assertEquals(changed.first(), restored.first())
        assertEquals(changed.toSet(), restored.toSet())
        assertEquals("added", restored.last().id)
    }

    private fun track(id: String) = Track(
        id = id,
        title = id,
        artist = "artist",
        albumId = null,
        artistId = null,
        trackNumber = null,
        coverArtUrl = null,
        streamUrl = "https://example.invalid/$id",
        durationMs = 1_000,
        isFavorite = false,
    )
}
