package info.jukov.player.feature.playback.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackModelsTest {
    @Test
    fun previousAvailabilityFollowsQueueIndex() {
        assertFalse(PlaybackSnapshot(currentIndex = 0, positionMs = 10_000).hasPrevious)
        assertTrue(PlaybackSnapshot(currentIndex = 1, positionMs = 0).hasPrevious)
    }

    @Test
    fun repeatAllMakesQueueNavigationCircular() {
        val snapshot = PlaybackSnapshot(
            queue = listOf(
                info.jukov.player.feature.track.domain.Track(
                    id = "track",
                    title = "Track",
                    artist = "Artist",
                    albumId = null,
                    artistId = null,
                    trackNumber = null,
                    coverArtUrl = null,
                    isFavorite = false,
                ),
            ),
            currentIndex = 0,
            repeatMode = RepeatMode.All,
        )

        assertTrue(snapshot.hasPrevious)
        assertTrue(snapshot.hasNext)
    }

    @Test
    fun repeatModeCyclesThroughAllStates() {
        assertEquals(RepeatMode.All, RepeatMode.Off.next())
        assertEquals(RepeatMode.One, RepeatMode.All.next())
        assertEquals(RepeatMode.Off, RepeatMode.One.next())
    }

    @Test
    fun platformNavigationAvailabilityOverridesLinearQueueOrder() {
        val snapshot = PlaybackSnapshot(
            currentIndex = 5,
            canSkipPrevious = false,
            canSkipNext = true,
        )

        assertFalse(snapshot.hasPrevious)
        assertTrue(snapshot.hasNext)
    }
}
