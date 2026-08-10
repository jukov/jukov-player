package info.jukov.player.feature.track.presentation.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class TrackDurationTest {
    @Test
    fun formatsMillisecondsAsMinutesAndSeconds() {
        assertEquals("3:05", formatTrackDuration(185_900))
    }

    @Test
    fun clampsNegativeDurationToZero() {
        assertEquals("0:00", formatTrackDuration(-1))
    }
}
