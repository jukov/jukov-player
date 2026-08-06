package info.jukov.player.core.presentation.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Height of the player overlay inside the screen's safe drawing area. */
val LocalPlayerBottomInset = staticCompositionLocalOf { 0.dp }

/** Keeps the last scrollable item reachable above the player without resizing the screen. */
@Composable
fun PaddingValues.withPlayerBottomInset(): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    val playerBottomInset = LocalPlayerBottomInset.current
    return PaddingValues(
        start = if (layoutDirection == LayoutDirection.Ltr) {
            calculateLeftPadding(layoutDirection)
        } else {
            calculateRightPadding(layoutDirection)
        },
        top = calculateTopPadding(),
        end = if (layoutDirection == LayoutDirection.Ltr) {
            calculateRightPadding(layoutDirection)
        } else {
            calculateLeftPadding(layoutDirection)
        },
        bottom = calculateBottomPadding() + playerBottomInset,
    )
}
