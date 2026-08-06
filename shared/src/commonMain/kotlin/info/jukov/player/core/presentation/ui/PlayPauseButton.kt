package info.jukov.player.core.presentation.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import jukovplayer.shared.generated.resources.Res
import jukovplayer.shared.generated.resources.pause
import jukovplayer.shared.generated.resources.play
import jukovplayer.shared.generated.resources.play_arrow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = CircleShape,
    ) {
        Icon(
            painter = painterResource(
                if (isPlaying) Res.drawable.pause else Res.drawable.play_arrow,
            ),
            contentDescription = stringResource(
                if (isPlaying) Res.string.pause else Res.string.play,
            ),
        )
    }
}
