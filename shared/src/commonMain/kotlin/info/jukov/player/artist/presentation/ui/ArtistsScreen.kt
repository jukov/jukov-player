package info.jukov.player.artist.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.jukov.player.artist.domain.Artist
import info.jukov.player.artist.presentation.ArtistsViewModel
import info.jukov.player.core.presentation.LoadableState
import info.jukov.player.core.presentation.ui.AppFlexibleTopAppBar
import info.jukov.player.core.presentation.ui.Padding
import jukovplayer.shared.generated.resources.Res
import jukovplayer.shared.generated.resources.more_vert
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistsScreen(
    viewModel: ArtistsViewModel,
    onLogout: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val artists = state.content.orEmpty()
    var refreshRequested by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state !is LoadableState.Loading) refreshRequested = false
    }

    val isRefreshing = refreshRequested && state is LoadableState.Loading
    val pullToRefreshState = rememberPullToRefreshState()
    val canScrollTopAppBar = remember(pullToRefreshState) {
        { pullToRefreshState.distanceFraction == 0f }
    }
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = canScrollTopAppBar,
    )
    val isPullToRefreshEnabled by remember(topAppBarScrollBehavior, pullToRefreshState) {
        derivedStateOf {
            topAppBarScrollBehavior.state.collapsedFraction == 0f ||
                    pullToRefreshState.distanceFraction > 0f
        }
    }
    Scaffold(
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        topBar = { ArtistsTopAppBar(onLogout, topAppBarScrollBehavior) },
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                refreshRequested = true
                viewModel.retry()
            },
            state = pullToRefreshState,
            enabled = isPullToRefreshEnabled,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = isRefreshing,
                    state = pullToRefreshState,
                )
            },
            modifier = Modifier.fillMaxSize().padding(it),
        ) {
            when {
                state is LoadableState.Loading && artists.isEmpty() -> LoadingContent()
                state is LoadableState.Failure && artists.isEmpty() ->
                    ErrorContent((state as LoadableState.Failure).message, viewModel::retry)
                artists.isEmpty() -> EmptyContent()
                else -> ArtistsContent(
                    artists = artists,
                    error = (state as? LoadableState.Failure)?.message,
                    onRetry = viewModel::retry,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ArtistsTopAppBar(
    onLogout: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    AppFlexibleTopAppBar(
        title = "Исполнители",
        scrollBehavior = scrollBehavior,
        actions = {
            IconButton(
                onClick = { menuExpanded = true },
            ) {
                Icon(
                    painter = painterResource(Res.drawable.more_vert),
                    contentDescription = "Ещё",
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Выйти") },
                    onClick = {
                        menuExpanded = false
                        onLogout()
                    },
                )
            }
        },
    )
}

@Composable
private fun ArtistsContent(
    artists: List<Artist>,
    error: String?,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        error?.let {
            TextButton(onClick = onRetry) {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
        LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = Padding.small),
                verticalArrangement = Arrangement.spacedBy(Padding.xSmall),
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
    Box(Modifier.fillMaxSize().padding(Padding.large), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(Padding.medium))
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
