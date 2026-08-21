package info.jukov.player.feature.playback.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import info.jukov.player.core.presentation.ui.Padding
import info.jukov.player.core.presentation.ui.SMALL_ARTWORK_SIZE
import info.jukov.player.core.presentation.ui.rememberArtworkRequest
import info.jukov.player.feature.track.domain.Track
import jukovplayer.shared.generated.resources.Res
import jukovplayer.shared.generated.resources.track_cover
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun QueueTrackRow(
    track: Track,
    isCurrent: Boolean,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandle: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isCurrent) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Padding.small, vertical = Padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isCurrent) {
            Spacer(Modifier.size(48.dp))
        } else {
            Checkbox(
                checked = selected,
                onCheckedChange = onSelectedChange,
                modifier = Modifier.size(48.dp),
            )
        }
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            track.coverArtUrl?.let { url ->
                AsyncImage(
                    model = rememberArtworkRequest(url, track.albumId, SMALL_ARTWORK_SIZE),
                    contentDescription = stringResource(Res.string.track_cover, track.title),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(Modifier.width(Padding.medium))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isCurrent) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                },
            )
            Text(
                track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        dragHandle?.invoke()
    }
}
