package info.jukov.player.feature.playback

import androidx.media3.session.CommandButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidFavoriteCommandTest {
    @Test
    fun unfilledFavoriteUsesSecondaryAndOverflowSlots() {
        val spec = favoriteCommandSpec(
            isFavorite = false,
            enabled = true,
            displayName = "Add to favorites",
        )

        assertEquals(CommandButton.ICON_HEART_UNFILLED, spec.icon)
        assertEquals("Add to favorites", spec.displayName)
        assertEquals(
            listOf(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW),
            spec.slots,
        )
        assertTrue(spec.enabled)
    }

    @Test
    fun filledFavoriteCanBeDisabledWhileMutationIsPending() {
        val spec = favoriteCommandSpec(
            isFavorite = true,
            enabled = false,
            displayName = "Remove from favorites",
        )

        assertEquals(CommandButton.ICON_HEART_FILLED, spec.icon)
        assertFalse(spec.enabled)
    }

    @Test
    fun favoriteCommandRejectsUntrustedControllers() {
        assertFalse(canAccessFavoriteCommand(isTrustedController = false))
        assertTrue(canAccessFavoriteCommand(isTrustedController = true))
    }
}
