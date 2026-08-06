package info.jukov.player.core.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import kotlin.math.roundToInt

@Stable
class AppCollapsingTopAppBarState internal constructor() {
    var heightOffsetPx by mutableFloatStateOf(0f)
        private set

    private var collapseRangePx = 0f

    val collapsedFraction: Float
        get() = if (collapseRangePx == 0f) 0f else -heightOffsetPx / collapseRangePx

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (available.y < 0f) consume(available.y) else Offset.Zero

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset = if (available.y > 0f) consume(available.y) else Offset.Zero
    }

    internal fun updateCollapseRange(rangePx: Float) {
        collapseRangePx = rangePx.coerceAtLeast(0f)
    }

    private fun consume(deltaY: Float): Offset {
        val previous = heightOffsetPx
        heightOffsetPx = (heightOffsetPx + deltaY).coerceIn(-collapseRangePx, 0f)
        return Offset(x = 0f, y = heightOffsetPx - previous)
    }
}

@Composable
fun rememberAppCollapsingTopAppBarState(): AppCollapsingTopAppBarState =
    remember { AppCollapsingTopAppBarState() }

@Composable
fun AppCollapsingTopAppBar(
    state: AppCollapsingTopAppBarState,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit,
    expandedContent: @Composable () -> Unit,
    collapsedContent: @Composable () -> Unit,
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainer

    SubcomposeLayout(
        modifier = modifier.fillMaxWidth().background(containerColor),
    ) { constraints ->
        val width = constraints.maxWidth
        val looseConstraints = Constraints(
            minWidth = width,
            maxWidth = width,
            minHeight = 0,
            maxHeight = Constraints.Infinity,
        )
        val expandedPlaceable = subcompose(AppBarSlot.Expanded) {
            Box(contentAlignment = Alignment.TopCenter) { expandedContent() }
        }.single().measure(looseConstraints)
        val collapsedPlaceable = subcompose(AppBarSlot.Collapsed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(CollapsedContentHeight)
                    .padding(start = 64.dp, end = Padding.small),
                contentAlignment = Alignment.CenterStart,
            ) {
                collapsedContent()
            }
        }.single().measure(looseConstraints)
        val navigationPlaceable = subcompose(AppBarSlot.Navigation) {
            Box(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(CollapsedContentHeight)
                    .padding(start = NavigationIconStartPadding),
                contentAlignment = Alignment.Center,
            ) {
                navigationIcon()
            }
        }.single().measure(
            Constraints(maxWidth = width, maxHeight = Constraints.Infinity),
        )

        val collapseRangePx =
            (expandedPlaceable.height - collapsedPlaceable.height).coerceAtLeast(0).toFloat()
        state.updateCollapseRange(collapseRangePx)
        val heightOffsetPx = state.heightOffsetPx.coerceIn(-collapseRangePx, 0f)
        val collapsedFraction = state.collapsedFraction.coerceIn(0f, 1f)
        val expandedAlpha =
            (1f - collapsedFraction / ContentSwitchFraction).coerceIn(0f, 1f)
        val compactAlpha = (
            (collapsedFraction - ContentSwitchFraction) / (1f - ContentSwitchFraction)
        ).coerceIn(0f, 1f)
        val currentHeight = (expandedPlaceable.height + heightOffsetPx)
            .roundToInt()
            .coerceAtLeast(collapsedPlaceable.height)

        layout(width, currentHeight) {
            expandedPlaceable.placeRelativeWithLayer(0, heightOffsetPx.roundToInt()) {
                alpha = expandedAlpha
            }
            collapsedPlaceable.placeRelativeWithLayer(0, 0) { alpha = compactAlpha }
            navigationPlaceable.placeRelative(0, 0)
        }
    }
}

private enum class AppBarSlot { Expanded, Collapsed, Navigation }

private val CollapsedContentHeight = 64.dp
private val NavigationIconStartPadding = 4.dp
private const val ContentSwitchFraction = 0.75f
