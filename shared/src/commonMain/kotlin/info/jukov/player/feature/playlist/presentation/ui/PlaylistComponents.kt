package info.jukov.player.feature.playlist.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.presentation.ui.Padding
import info.jukov.player.core.presentation.ui.localizedMessage
import info.jukov.player.feature.playlist.domain.Playlist
import info.jukov.player.feature.playlist.presentation.PlaylistPickerSubmission
import info.jukov.player.feature.playlist.presentation.PlaylistPickerViewModel
import jukovplayer.shared.generated.resources.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPickerHost(viewModel: PlaylistPickerViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<AppError?>(null) }
    LaunchedEffect(viewModel) { viewModel.messages.collect { error = it } }
    if (!state.visible) return
    val dismissBlocked by rememberUpdatedState(state.pending)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            target != SheetValue.Hidden || !dismissBlocked
        },
    )
    LaunchedEffect(state.submission) {
        if (state.submission == PlaylistPickerSubmission.Success) {
            delay(SUCCESS_DISPLAY_DURATION_MILLIS)
            viewModel.dismiss()
        }
    }
    ModalBottomSheet(
        onDismissRequest = viewModel::dismiss,
        sheetState = sheetState,
    ) {
        when (state.submission) {
            PlaylistPickerSubmission.Idle -> PlaylistPickerContent(
                playlists = state.playlists,
                error = error,
                onCreate = viewModel::showCreate,
                onAddTo = viewModel::addTo,
            )
            PlaylistPickerSubmission.Pending -> PlaylistPickerStatus(
                message = stringResource(Res.string.adding_to_playlist),
            ) {
                LoadingIndicator()
            }
            PlaylistPickerSubmission.Success -> PlaylistPickerStatus(
                message = stringResource(Res.string.added_to_playlist),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
    }
    if (state.creating) {
        CreatePlaylistDialog(state.pending, viewModel::hideCreate, viewModel::create)
    }
}

@Composable
private fun PlaylistPickerContent(
    playlists: LoadableState<List<Playlist>>,
    error: AppError?,
    onCreate: () -> Unit,
    onAddTo: (Playlist) -> Unit,
) {
    Text(
        stringResource(Res.string.choose_playlist),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(Padding.medium),
    )
    ListItem(
        modifier = Modifier.clickable(onClick = onCreate),
        leadingContent = { Icon(painterResource(Res.drawable.add), null) },
        headlineContent = { Text(stringResource(Res.string.create_and_add)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
    when (playlists) {
        is LoadableState.Loading -> Box(
            Modifier.fillMaxWidth().padding(Padding.large),
            contentAlignment = Alignment.Center,
        ) { LoadingIndicator() }
        is LoadableState.Failure -> Text(
            playlists.error.localizedMessage(),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(Padding.medium),
        )
        is LoadableState.Content -> playlists.content.forEach { playlist ->
            ListItem(
                modifier = Modifier.clickable { onAddTo(playlist) },
                headlineContent = { Text(playlist.name) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
    error?.let {
        Text(
            it.localizedMessage(),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(Padding.medium),
        )
    }
    Spacer(Modifier.height(Padding.large))
}

@Composable
private fun PlaylistPickerStatus(
    message: String,
    indicator: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(Padding.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Padding.medium),
    ) {
        indicator()
        Text(message, style = MaterialTheme.typography.titleMedium)
    }
    Spacer(Modifier.height(Padding.large))
}

@Composable
internal fun CreatePlaylistDialog(
    pending: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.create_playlist)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                enabled = !pending,
                singleLine = true,
                label = { Text(stringResource(Res.string.playlist_name)) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.trim()) },
                enabled = name.isNotBlank() && !pending,
            ) {
                if (pending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(Res.string.create))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !pending) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

private const val SUCCESS_DISPLAY_DURATION_MILLIS = 1_500L

@Composable
internal fun DeletePlaylistDialog(name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.delete_playlist_title)) },
        text = { Text(stringResource(Res.string.delete_playlist_message, name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(Res.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

@Composable
internal fun ReadOnlyPill() {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            stringResource(Res.string.read_only),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Padding.small, vertical = Padding.xSmall),
        )
    }
}

@Composable
internal fun PlaylistBackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(Res.drawable.arrow_back), stringResource(Res.string.back))
    }
}

@Composable
internal fun PlaylistCentered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
