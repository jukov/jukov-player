package info.jukov.player.feature.playlist.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.core.presentation.ui.Padding
import info.jukov.player.core.presentation.ui.localizedMessage
import info.jukov.player.feature.playlist.domain.Playlist
import info.jukov.player.feature.playlist.presentation.PlaylistPickerViewModel
import jukovplayer.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPickerHost(viewModel: PlaylistPickerViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<AppError?>(null) }
    LaunchedEffect(viewModel) { viewModel.messages.collect { error = it } }
    val snackbar = remember { SnackbarHostState() }
    val errorMessage = error?.localizedMessage()
    LaunchedEffect(errorMessage, state.visible) {
        if (errorMessage != null && !state.visible) {
            snackbar.showSnackbar(errorMessage)
            error = null
        }
    }
    if (state.visible) {
        ModalBottomSheet(onDismissRequest = viewModel::dismiss) {
            Text(
                stringResource(Res.string.choose_playlist),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(Padding.medium),
            )
            ListItem(
                modifier = Modifier.clickable(
                    enabled = !state.pending,
                    onClick = {
                        error = null
                        viewModel.showCreate()
                    },
                ),
                leadingContent = { Icon(painterResource(Res.drawable.add), null) },
                headlineContent = { Text(stringResource(Res.string.create_and_add)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            when (val playlists = state.playlists) {
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
                        modifier = Modifier.clickable(enabled = !state.pending) {
                            error = null
                            viewModel.addTo(playlist)
                        },
                        headlineContent = { Text(playlist.name) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            if (!state.creating && errorMessage != null) {
                Text(
                    errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(Padding.medium),
                )
            }
            Spacer(Modifier.height(Padding.large))
        }
        if (state.creating) {
            CreatePlaylistDialog(
                pending = state.pending,
                errorMessage = errorMessage,
                onDismiss = viewModel::hideCreate,
                onCreate = { name, isPublic ->
                    error = null
                    viewModel.create(name, isPublic)
                },
            )
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.navigationBarsPadding().padding(Padding.medium),
        )
    }
}

@Composable
internal fun CreatePlaylistDialog(
    pending: Boolean,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onCreate: (String, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {
            if (!pending) {
                onDismiss()
            }
        },
        title = { Text(stringResource(Res.string.create_playlist)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    enabled = !pending,
                    singleLine = true,
                    label = { Text(stringResource(Res.string.playlist_name)) },
                )
                Spacer(Modifier.height(Padding.medium))
                PlaylistVisibilityControl(
                    isPublic = isPublic,
                    enabled = !pending,
                    onChange = { isPublic = it },
                )
                if (errorMessage != null) {
                    Spacer(Modifier.height(Padding.medium))
                    Text(
                        errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.trim(), isPublic) },
                enabled = name.isNotBlank() && !pending,
            ) { Text(stringResource(Res.string.create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !pending) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
internal fun EditPlaylistDialog(
    playlist: Playlist,
    pending: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(playlist.id, playlist.name) { mutableStateOf(playlist.name) }
    var isPublic by remember(playlist.id, playlist.isPublic) { mutableStateOf(playlist.isPublic) }
    AlertDialog(
        onDismissRequest = {
            if (!pending) {
                onDismiss()
            }
        },
        title = { Text(stringResource(Res.string.edit_playlist)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    enabled = !pending,
                    singleLine = true,
                    label = { Text(stringResource(Res.string.playlist_name)) },
                )
                Spacer(Modifier.height(Padding.medium))
                PlaylistVisibilityControl(
                    isPublic = isPublic,
                    enabled = !pending,
                    onChange = { isPublic = it },
                )
                Spacer(Modifier.height(Padding.medium))
                TextButton(onClick = onDelete, enabled = !pending) {
                    Text(
                        stringResource(Res.string.delete_playlist),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), isPublic) },
                enabled = name.isNotBlank() && !pending,
            ) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !pending) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun PlaylistVisibilityControl(
    isPublic: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(
            value = isPublic,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onChange,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.public_playlist))
            Text(
                stringResource(Res.string.public_playlist_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = isPublic, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
internal fun DeletePlaylistDialog(
    name: String,
    pending: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!pending) {
                onDismiss()
            }
        },
        title = { Text(stringResource(Res.string.delete_playlist_title)) },
        text = { Text(stringResource(Res.string.delete_playlist_message, name)) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !pending) {
                Text(stringResource(Res.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !pending) {
                Text(stringResource(Res.string.cancel))
            }
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
