package info.jukov.player.feature.playback.presentation.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerUiFormattingTest {
    @Test
    fun playbackDurationFormatsMinutesAndZeroPaddedSeconds() {
        assertEquals("0:00", formatPlaybackDuration(0L))
        assertEquals("1:05", formatPlaybackDuration(65_999L))
        assertEquals("61:01", formatPlaybackDuration(3_661_000L))
    }

    @Test
    fun playbackDurationClampsNegativeValues() {
        assertEquals("0:00", formatPlaybackDuration(-1L))
    }

    @Test
    fun artistAndYearIncludesYearOnlyWhenPresent() {
        assertEquals("Artist · 2026", formatPlayerArtistAndYear("Artist", 2026))
        assertEquals("Artist", formatPlayerArtistAndYear("Artist", null))
    }
}
