package info.jukov.player.feature.playback.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackModelsTest {
    @Test
    fun previousAvailabilityFollowsQueueIndex() {
        assertFalse(PlaybackSnapshot(currentIndex = 0, positionMs = 10_000).hasPrevious)
        assertTrue(PlaybackSnapshot(currentIndex = 1, positionMs = 0).hasPrevious)
    }
}
