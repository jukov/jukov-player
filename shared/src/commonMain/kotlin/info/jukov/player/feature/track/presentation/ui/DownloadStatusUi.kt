package info.jukov.player.feature.track.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import info.jukov.player.feature.download.domain.DownloadState
import info.jukov.player.feature.download.domain.DownloadStatus
import jukovplayer.shared.generated.resources.Res
import jukovplayer.shared.generated.resources.arrow_down_bold
import jukovplayer.shared.generated.resources.download
import jukovplayer.shared.generated.resources.downloads
import jukovplayer.shared.generated.resources.pause
import jukovplayer.shared.generated.resources.play
import jukovplayer.shared.generated.resources.play_arrow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun DownloadPlayButton(
    isPlaying: Boolean,
    isLoading: Boolean = false,
    onClick: () -> Unit,
    status: DownloadStatus?,
) {
    val loadingDescription = stringResource(Res.string.pause)
    Box {
        IconButton(onClick = onClick) {
            if (isLoading) {
                LoadingIndicator(
                    modifier = Modifier.size(24.dp).semantics {
                        contentDescription = loadingDescription
                    },
                    color = LocalContentColor.current,
                )
            } else {
                Icon(
                    painter = painterResource(
                        if (isPlaying) {
                            Res.drawable.pause
                        } else {
                            Res.drawable.play_arrow
                        },
                    ),
                    contentDescription = stringResource(
                        if (isPlaying) {
                            Res.string.pause
                        } else {
                            Res.string.play
                        },
                    ),
                )
            }
        }
        DownloadBadge(
            status = status,
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-6).dp, y = (-6).dp),
        )
    }
}

@Composable
internal fun DownloadIconButton(status: DownloadStatus?, onClick: () -> Unit) {
    Box {
        IconButton(onClick = onClick) {
            Icon(painterResource(Res.drawable.download), stringResource(Res.string.downloads))
        }
        DownloadBadge(
            status = status,
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-6).dp, y = (-6).dp),
        )
    }
}

@Composable
internal fun DownloadBadge(status: DownloadStatus?, modifier: Modifier = Modifier) {
    when (status?.state) {
        DownloadState.Queued, DownloadState.Downloading -> DownloadStatusBadgeContainer(modifier) {
            status.progress?.let { progress ->
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(11.dp),
                    strokeWidth = 1.5.dp,
                )
            } ?: CircularProgressIndicator(
                modifier = Modifier.size(11.dp),
                strokeWidth = 1.5.dp,
            )
        }
        DownloadState.Completed -> DownloadStatusBadgeContainer(modifier) {
            Icon(
                painter = painterResource(Res.drawable.arrow_down_bold),
                contentDescription = stringResource(Res.string.downloads),
                modifier = Modifier.size(11.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        DownloadState.Failed -> DownloadStatusBadgeContainer(modifier) {
            Text(
                "!",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        null -> Unit
    }
}

@Composable
private fun DownloadStatusBadgeContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
