package info.jukov.player.artist.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.jukov.player.artist.domain.Artist
import info.jukov.player.core.presentation.LoadableState

@Composable
fun ArtistsScreen(
    viewModel: ArtistsViewModel,
    username: String,
    onLogout: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().safeContentPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Исполнители", style = MaterialTheme.typography.headlineMedium)
                Text(username, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onLogout) { Text("Выйти") }
        }

        val artists = state.content.orEmpty()
        when {
            state is LoadableState.Loading && artists.isEmpty() -> LoadingContent()
            state is LoadableState.Failure && artists.isEmpty() ->
                ErrorContent((state as LoadableState.Failure).message, viewModel::retry)
            artists.isEmpty() -> EmptyContent()
            else -> ArtistsContent(
                artists = artists,
                isLoading = state is LoadableState.Loading,
                error = (state as? LoadableState.Failure)?.message,
                onRetry = viewModel::retry,
            )
        }
    }
}

@Composable
private fun ArtistsContent(
    artists: List<Artist>,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {
            TextButton(onClick = onRetry) {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
        LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(artists, key = { it.id }) { artist ->
                    ListItem(
                        headlineContent = {
                            Text(artist.name, fontWeight = FontWeight.Medium)
                        },
                        supportingContent = {
                            Text("Альбомы: ${artist.albumCount}")
                        },
                    )
                    HorizontalDivider()
                }
            }
    }
}

@Composable
private fun LoadingContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) { Text("Повторить") }
        }
    }
}

@Composable
private fun EmptyContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Исполнители не найдены")
    }
}
