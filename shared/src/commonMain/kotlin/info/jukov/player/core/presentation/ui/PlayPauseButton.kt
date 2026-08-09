package info.jukov.player.core.presentation.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import jukovplayer.shared.generated.resources.Res
import jukovplayer.shared.generated.resources.pause
import jukovplayer.shared.generated.resources.play
import jukovplayer.shared.generated.resources.play_arrow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    isLoading: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconSize: Dp = 24.dp,
) {
    val loadingDescription = stringResource(Res.string.pause)
    FilledIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = CircleShape,
    ) {
        if (isLoading) {
            LoadingIndicator(
                modifier = Modifier.size(iconSize).semantics {
                    contentDescription = loadingDescription
                },
                color = LocalContentColor.current,
            )
        } else {
            Icon(
                painter = painterResource(
                    if (isPlaying) Res.drawable.pause else Res.drawable.play_arrow,
                ),
                contentDescription = stringResource(
                    if (isPlaying) Res.string.pause else Res.string.play,
                ),
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
