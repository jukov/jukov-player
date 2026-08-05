package info.jukov.player.track.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import info.jukov.player.core.presentation.LoadableState
import info.jukov.player.core.presentation.ui.Padding
import info.jukov.player.track.domain.Track
import info.jukov.player.track.domain.TracksFilter
import info.jukov.player.track.presentation.TracksViewModel
import jukovplayer.shared.generated.resources.Res
import jukovplayer.shared.generated.resources.arrow_back
import jukovplayer.shared.generated.resources.favorite_border
import jukovplayer.shared.generated.resources.play_arrow
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracksScreen(
    filter: TracksFilter,
    viewModel: TracksViewModel,
    onBack: () -> Unit,
    onFavoriteClick: (Track) -> Unit = {},
    onPlayClick: (Track) -> Unit = {},
) {
    LaunchedEffect(filter) { viewModel.load(filter) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tracks = state.content.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Треки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(Res.drawable.arrow_back),
                            contentDescription = "Назад",
                        )
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        when {
            state is LoadableState.Loading && tracks.isEmpty() ->
                CenteredLoading(Modifier.padding(scaffoldPadding))

            state is LoadableState.Failure && tracks.isEmpty() -> CenteredError(
                message = (state as LoadableState.Failure).message,
                onRetry = viewModel::retry,
                modifier = Modifier.padding(scaffoldPadding),
            )

            tracks.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Треки не найдены")
            }

            else -> TracksList(
                tracks = tracks,
                error = (state as? LoadableState.Failure)?.message,
                onFavoriteClick = onFavoriteClick,
                onPlayClick = onPlayClick,
                modifier = Modifier.padding(scaffoldPadding),
            )
        }
    }
}

@Composable
private fun TracksList(
    tracks: List<Track>,
    error: String?,
    onFavoriteClick: (Track) -> Unit,
    onPlayClick: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Padding.small),
        verticalArrangement = Arrangement.spacedBy(Padding.small),
    ) {
        error?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }
        items(tracks, key = Track::id) { track ->
            TrackRow(track, onFavoriteClick, onPlayClick)
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    onFavoriteClick: (Track) -> Unit,
    onPlayClick: (Track) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Padding.small, vertical = Padding.xSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = track.trackNumber?.toString().orEmpty(),
            modifier = Modifier.width(28.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            track.coverArtUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = "Обложка ${track.title}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(Modifier.width(Padding.medium))
        Column(Modifier.weight(1f)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { onFavoriteClick(track) }) {
            Icon(
                painter = painterResource(Res.drawable.favorite_border),
                contentDescription = "Добавить в избранное",
            )
        }
        IconButton(onClick = { onPlayClick(track) }) {
            Icon(
                painter = painterResource(Res.drawable.play_arrow),
                contentDescription = "Воспроизвести",
            )
        }
    }
}

@Composable
private fun CenteredLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CenteredError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = MaterialTheme.colorScheme.error)
            Button(onClick = onRetry) { Text("Повторить") }
        }
    }
}
