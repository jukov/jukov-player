package info.jukov.player.feature.playlist.presentation

import info.jukov.player.core.domain.LoadableState
import info.jukov.player.feature.playlist.domain.Playlist
import info.jukov.player.feature.track.domain.Track

data class PlaylistPickerState(
    val visible: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val playlists: LoadableState<List<Playlist>> = LoadableState.Loading(null),
    val creating: Boolean = false,
    val pending: Boolean = false,
)
