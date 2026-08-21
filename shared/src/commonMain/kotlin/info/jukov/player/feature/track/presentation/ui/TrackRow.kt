package info.jukov.player.feature.track.presentation.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import info.jukov.player.core.presentation.ui.FavoriteToggleButton
import info.jukov.player.core.presentation.ui.MetadataPill
import info.jukov.player.core.presentation.ui.Padding
import info.jukov.player.core.presentation.ui.SMALL_ARTWORK_SIZE
import info.jukov.player.core.presentation.ui.rememberArtworkRequest
import info.jukov.player.feature.download.domain.DownloadStatus
import info.jukov.player.feature.track.domain.Track
import jukovplayer.shared.generated.resources.Res
import jukovplayer.shared.generated.resources.delete
import jukovplayer.shared.generated.resources.remove_download
import jukovplayer.shared.generated.resources.track_cover
import jukovplayer.shared.generated.resources.track_loading
import jukovplayer.shared.generated.resources.track_playing
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

enum class TrackTrailingAction { Favorite, RemoveDownload }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    showArtwork: Boolean = true,
    showTrackNumber: Boolean = false,
    onPlayClick: () -> Unit,
    isPlaying: Boolean,
    isLoading: Boolean = false,
    favoriteEnabled: Boolean = true,
    onFavoriteClick: () -> Unit = {},
    downloadStatus: DownloadStatus? = null,
    onDownloadClick: () -> Unit = {},
    onCancelDownload: () -> Unit = {},
    onRetryDownload: () -> Unit = {},
    artworkUrl: String? = track.coverArtUrl,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelectedChange: (Boolean) -> Unit = {},
    trailingAction: TrackTrailingAction = TrackTrailingAction.Favorite,
    modifier: Modifier = Modifier,
    dragHandle: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("track.${track.id}")
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isPlaying) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .combinedClickable(
                onClick = {
                    if (selectionMode) {
                        onSelectedChange(!selected)
                    } else {
                        onPlayClick()
                    }
                },
                onLongClick = { onSelectedChange(true) },
            )
            .padding(
                start = Padding.medium,
                end = Padding.small,
                top = Padding.small,
                bottom = Padding.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showTrackNumber) {
            if (selectionMode) {
                SelectionBadge(selected, track.title, Modifier.width(28.dp))
            } else {
                Text(
                    text = track.trackNumber?.toString().orEmpty(),
                    modifier = Modifier.width(28.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (showArtwork) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                artworkUrl?.let { url ->
                    AsyncImage(
                        model = rememberArtworkRequest(
                            url = url,
                            albumId = track.albumId,
                            requestedSize = SMALL_ARTWORK_SIZE,
                        ),
                        contentDescription = stringResource(Res.string.track_cover, track.title),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                if (selectionMode) {
                    SelectionBadge(selected, track.title, Modifier.align(Alignment.BottomEnd))
                }
            }
            Spacer(Modifier.width(Padding.medium))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    isLoading -> TrackLoadingIndicator()
                    isPlaying -> PlayingEqualizer()
                }
                if (isLoading || isPlaying) {
                    Spacer(Modifier.width(Padding.xSmall))
                }
                Text(
                    text = track.title,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.durationMs > 0) {
                    MetadataPill(formatTrackDuration(track.durationMs))
                }
                val trackDetails = listOfNotNull(
                    track.artist.takeIf(String::isNotBlank),
                    track.album?.takeIf(String::isNotBlank),
                ).distinct().joinToString(" · ")
                if (track.durationMs > 0 && trackDetails.isNotEmpty()) {
                    Spacer(Modifier.width(Padding.small))
                }
                Text(
                    text = trackDetails,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailingAction == TrackTrailingAction.Favorite) {
            FavoriteToggleButton(
                isFavorite = track.isFavorite,
                onClick = onFavoriteClick,
                enabled = favoriteEnabled,
            )
        }
        if (trailingAction == TrackTrailingAction.RemoveDownload) {
            IconButton(onClick = onCancelDownload) {
                Icon(painterResource(Res.drawable.delete), stringResource(Res.string.remove_download))
            }
        }
        DownloadBadge(
            status = downloadStatus,
            modifier = Modifier.padding(end = Padding.small),
        )
        dragHandle?.invoke()
    }
}

@Composable
private fun TrackLoadingIndicator() {
    val description = stringResource(Res.string.track_loading)
    LoadingIndicator(
        modifier = Modifier.size(16.dp).semantics { contentDescription = description },
    )
}

@Composable
private fun PlayingEqualizer() {
    val transition = rememberInfiniteTransition(label = "playing equalizer")
    val firstBar by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 480), RepeatMode.Reverse),
        label = "first bar",
    )
    val secondBar by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 620), RepeatMode.Reverse),
        label = "second bar",
    )
    val thirdBar by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 390), RepeatMode.Reverse),
        label = "third bar",
    )
    val color = MaterialTheme.colorScheme.primary
    val description = stringResource(Res.string.track_playing)
    Canvas(
        Modifier.size(16.dp).semantics { contentDescription = description },
    ) {
        val barWidth = size.width * 0.18f
        val gap = size.width * 0.12f
        val totalWidth = barWidth * 3 + gap * 2
        val startX = (size.width - totalWidth) / 2
        listOf(firstBar, secondBar, thirdBar).forEachIndexed { index, heightFraction ->
            val barHeight = size.height * heightFraction
            drawRoundRect(
                color = color,
                topLeft = Offset(startX + index * (barWidth + gap), size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}

internal fun formatTrackDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1_000
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
