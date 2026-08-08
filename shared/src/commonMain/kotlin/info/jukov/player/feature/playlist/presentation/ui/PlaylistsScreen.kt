package info.jukov.player.feature.playlist.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.presentation.ui.*
import info.jukov.player.feature.playlist.domain.Playlist
import info.jukov.player.feature.playlist.presentation.PlaylistsViewModel
import jukovplayer.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    viewModel: PlaylistsViewModel,
    onBack: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
) {
    LaunchedEffect(viewModel) { viewModel.load() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    var error by remember { mutableStateOf<AppError?>(null) }
    LaunchedEffect(viewModel) { viewModel.messages.collect { error = it } }
    val errorMessage = error?.localizedMessage()
    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbar.showSnackbar(it) }
        error = null
    }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            AppFlexibleTopAppBar(
                title = stringResource(Res.string.playlists),
                scrollBehavior = scroll,
                navigationIcon = { PlaylistBackButton(onBack) },
                actions = {
                    IconButton(onClick = { creating = true }) {
                        Icon(
                            painterResource(Res.drawable.add),
                            stringResource(Res.string.create_playlist),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val playlists = state.content.orEmpty()
        PullToRefreshBox(
            isRefreshing = state is LoadableState.Loading && state.content != null,
            onRefresh = { viewModel.load(forceRefresh = true) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state is LoadableState.Loading && playlists.isEmpty() -> PlaylistCentered {
                    LoadingIndicator()
                }
                state is LoadableState.Failure && playlists.isEmpty() -> PlaylistCentered {
                    Button(onClick = { viewModel.load(forceRefresh = true) }) {
                        Text(stringResource(Res.string.retry))
                    }
                }
                playlists.isEmpty() -> PlaylistCentered {
                    Text(stringResource(Res.string.playlists_not_found))
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = Padding.small)
                        .withPlayerBottomInset(),
                ) {
                    items(playlists, key = Playlist::id) { playlist ->
                        ListItem(
                            modifier = Modifier.clickable { onPlaylistClick(playlist) },
                            headlineContent = {
                                Text(
                                    playlist.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Text(
                                    stringResource(
                                        Res.string.playlist_tracks_count,
                                        playlist.songCount,
                                    ),
                                )
                            },
                            trailingContent = {
                                if (!viewModel.isEditable(playlist)) {
                                    ReadOnlyPill()
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    if (creating) {
        CreatePlaylistDialog(
            pending = false,
            onDismiss = { creating = false },
            onCreate = { viewModel.create(it) { creating = false } },
        )
    }
}
