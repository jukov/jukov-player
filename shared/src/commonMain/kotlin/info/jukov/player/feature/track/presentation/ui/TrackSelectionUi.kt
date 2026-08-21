package info.jukov.player.feature.track.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import jukovplayer.shared.generated.resources.Res
import jukovplayer.shared.generated.resources.add_selected_to_queue
import jukovplayer.shared.generated.resources.add_to_playlist
import jukovplayer.shared.generated.resources.arrow_back
import jukovplayer.shared.generated.resources.check
import jukovplayer.shared.generated.resources.clear_selection
import jukovplayer.shared.generated.resources.download
import jukovplayer.shared.generated.resources.download_off
import jukovplayer.shared.generated.resources.download_selected_tracks
import jukovplayer.shared.generated.resources.favorite_selected_tracks
import jukovplayer.shared.generated.resources.heart
import jukovplayer.shared.generated.resources.heart_outline
import jukovplayer.shared.generated.resources.playlist_play
import jukovplayer.shared.generated.resources.playlist_plus
import jukovplayer.shared.generated.resources.remove_from_favorites
import jukovplayer.shared.generated.resources.remove_selected_downloads
import jukovplayer.shared.generated.resources.selected_track
import jukovplayer.shared.generated.resources.selected_tracks
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SelectionBadge(selected: Boolean, title: String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(22.dp)
                .clip(CircleShape)
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                )
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    painterResource(Res.drawable.check),
                    contentDescription = stringResource(Res.string.selected_track, title),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSelectionTopAppBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onDownload: () -> Unit,
    onAddToQueue: () -> Unit,
    allSelectedFavorite: Boolean = false,
    onFavorite: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    removesDownloads: Boolean = false,
) {
    TopAppBar(
        title = { Text(stringResource(Res.string.selected_tracks, selectedCount)) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(painterResource(Res.drawable.arrow_back), stringResource(Res.string.clear_selection))
            }
        },
        actions = {
            onFavorite?.let { action ->
                IconButton(onClick = action) {
                    Icon(
                        painterResource(
                            if (allSelectedFavorite) {
                                Res.drawable.heart_outline
                            } else {
                                Res.drawable.heart
                            },
                        ),
                        stringResource(
                            if (allSelectedFavorite) {
                                Res.string.remove_from_favorites
                            } else {
                                Res.string.favorite_selected_tracks
                            },
                        ),
                    )
                }
            }
            IconButton(onClick = onDownload) {
                Icon(
                    painterResource(
                        if (removesDownloads) {
                            Res.drawable.download_off
                        } else {
                            Res.drawable.download
                        },
                    ),
                    stringResource(
                        if (removesDownloads) {
                            Res.string.remove_selected_downloads
                        } else {
                            Res.string.download_selected_tracks
                        },
                    ),
                )
            }
            IconButton(onClick = onAddToQueue) {
                Icon(painterResource(Res.drawable.playlist_play), stringResource(Res.string.add_selected_to_queue))
            }
            onAddToPlaylist?.let { action ->
                IconButton(onClick = action) {
                    Icon(painterResource(Res.drawable.playlist_plus), stringResource(Res.string.add_to_playlist))
                }
            }
        },
    )
}
