package info.jukov.player.feature.track.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import info.jukov.player.core.presentation.ui.AppCollapsingTopAppBar
import info.jukov.player.core.presentation.ui.AppCollapsingTopAppBarState
import info.jukov.player.core.presentation.ui.Padding
import info.jukov.player.core.presentation.ui.PlayPauseButton
import info.jukov.player.core.presentation.ui.SMALL_ARTWORK_SIZE
import info.jukov.player.core.presentation.ui.playerGradientColors
import info.jukov.player.core.presentation.ui.rememberArtworkPalette
import info.jukov.player.core.presentation.ui.rememberArtworkRequest
import info.jukov.player.feature.download.domain.DownloadStatus
import info.jukov.player.feature.track.domain.Track
import jukovplayer.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlbumTracksTopAppBar(
    header: AlbumHeader,
    tracks: List<Track>,
    appBarState: AppCollapsingTopAppBarState,
    onBack: () -> Unit,
    onPlayClick: () -> Unit,
    isPlaying: Boolean,
    isLoading: Boolean,
    isFavorite: Boolean,
    favoriteEnabled: Boolean,
    onFavoriteClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToQueue: () -> Unit,
    downloadStatus: DownloadStatus?,
    onDownloadClick: () -> Unit,
    onArtistClick: (String, String) -> Unit,
) {
    BoxWithConstraints {
        val artworkSize = (maxWidth * 0.75f).coerceAtMost(400.dp)
        AppCollapsingTopAppBar(
            state = appBarState,
            navigationIcon = { BackButton(onBack) },
            expandedContent = {
                ExpandedAlbumTracksHeader(
                    header = header,
                    artworkSize = artworkSize,
                    onPlayClick = onPlayClick,
                    playEnabled = tracks.isNotEmpty(),
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    isFavorite = isFavorite,
                    favoriteEnabled = favoriteEnabled,
                    onFavoriteClick = onFavoriteClick,
                    onAddToPlaylist = onAddToPlaylist,
                    onAddToQueue = onAddToQueue,
                    downloadStatus = downloadStatus,
                    onDownloadClick = onDownloadClick,
                    onArtistClick = onArtistClick,
                )
            },
            collapsedContent = {
                CollapsedAlbumTracksHeader(
                    header = header,
                    onPlayClick = onPlayClick,
                    playEnabled = tracks.isNotEmpty(),
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    onArtistClick = onArtistClick,
                )
            },
        )
    }
}

@Composable
internal fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(
            painter = painterResource(Res.drawable.arrow_back),
            contentDescription = stringResource(Res.string.back),
        )
    }
}

@Composable
private fun ExpandedAlbumTracksHeader(
    header: AlbumHeader,
    artworkSize: Dp,
    onPlayClick: () -> Unit,
    playEnabled: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    isFavorite: Boolean,
    favoriteEnabled: Boolean,
    onFavoriteClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToQueue: () -> Unit,
    downloadStatus: DownloadStatus?,
    onDownloadClick: () -> Unit,
    onArtistClick: (String, String) -> Unit,
) {
    val palette = rememberArtworkPalette(
        key = header.coverArtId ?: header.coverArtUrl ?: header.albumId,
        url = header.coverArtUrl,
    )
    val gradientColors = palette.playerGradientColors(
        surface = MaterialTheme.colorScheme.surface,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to gradientColors[0],
                        .48f to gradientColors[1],
                        1f to gradientColors[2],
                    ),
                ),
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = Padding.small, bottom = Padding.large),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(artworkSize)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            header.coverArtUrl?.let { url ->
                AsyncImage(
                    model = rememberArtworkRequest(url, header.albumId, SMALL_ARTWORK_SIZE),
                    contentDescription = stringResource(Res.string.album_cover, header.name),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(Modifier.size(Padding.small))
        Column(
            modifier = Modifier.width(artworkSize),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = header.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (header.artist.isNotBlank()) {
                Text(
                    text = header.artistAndYear,
                    modifier = Modifier.clickable(
                        enabled = header.artistId != null,
                        onClick = {
                            header.artistId?.let { onArtistClick(it, header.artist) }
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.size(Padding.medium))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onAddToPlaylist,
                    enabled = playEnabled,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.playlist_plus),
                        contentDescription = stringResource(Res.string.add_to_playlist),
                    )
                }
                Spacer(Modifier.width(Padding.small))
                IconButton(
                    onClick = onFavoriteClick,
                    enabled = favoriteEnabled,
                ) {
                    Icon(
                        painter = painterResource(
                            if (isFavorite) {
                                Res.drawable.heart
                            } else {
                                Res.drawable.heart_outline
                            },
                        ),
                        contentDescription = stringResource(
                            if (isFavorite) {
                                Res.string.remove_from_favorites
                            } else {
                                Res.string.add_to_favorites
                            },
                        ),
                    )
                }
                Spacer(Modifier.width(Padding.small))
                PlayPauseButton(
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    onClick = onPlayClick,
                    enabled = playEnabled,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.width(Padding.small))
                DownloadIconButton(downloadStatus, onDownloadClick)
                Spacer(Modifier.width(Padding.small))
                IconButton(
                    onClick = onAddToQueue,
                    enabled = playEnabled,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.playlist_play),
                        contentDescription = stringResource(Res.string.add_album_to_queue),
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsedAlbumTracksHeader(
    header: AlbumHeader,
    onPlayClick: () -> Unit,
    playEnabled: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    onArtistClick: (String, String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            header.coverArtUrl?.let { url ->
                AsyncImage(
                    model = rememberArtworkRequest(url, header.albumId, SMALL_ARTWORK_SIZE),
                    contentDescription = stringResource(Res.string.album_cover, header.name),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(Modifier.width(Padding.medium))
        Column(
            modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = header.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (header.artist.isNotBlank()) {
                Text(
                    text = header.artistAndYear,
                    modifier = Modifier.clickable(
                        enabled = header.artistId != null,
                        onClick = {
                            header.artistId?.let { onArtistClick(it, header.artist) }
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        PlayPauseButton(
            isPlaying = isPlaying,
            isLoading = isLoading,
            onClick = onPlayClick,
            enabled = playEnabled,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.width(Padding.small))
    }
}

data class AlbumHeader(
    val name: String,
    val artist: String,
    val coverArtUrl: String?,
    val coverArtId: String?,
    val albumId: String,
    val artistId: String?,
    val year: Int?,
)

private val AlbumHeader.artistAndYear: String
    get() = year?.let { "$artist · $it" } ?: artist
