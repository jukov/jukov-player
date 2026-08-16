package info.jukov.player.feature.track.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.favorite.domain.favoriteStateForSelection

@Stable
class TrackSelectionState internal constructor() {
    var selectedIds: Set<String> by mutableStateOf(emptySet())
        private set

    val isActive: Boolean get() = selectedIds.isNotEmpty()
    val selectedCount: Int get() = selectedIds.size

    fun setSelected(id: String, selected: Boolean) {
        selectedIds = if (selected) selectedIds + id else selectedIds - id
    }

    fun clear() {
        selectedIds = emptySet()
    }

    fun selectedTracks(tracks: List<Track>): List<Track> =
        tracks.filter { it.id in selectedIds }

    fun areAllSelectedFavorite(tracks: List<Track>): Boolean =
        !favoriteStateForSelection(selectedTracks(tracks))

    fun finish(tracks: List<Track>, action: (List<Track>) -> Unit) {
        action(selectedTracks(tracks))
        clear()
    }

    internal fun retain(validIds: Set<String>) {
        selectedIds = selectedIds intersect validIds
    }
}

@Composable
fun rememberTrackSelectionState(
    tracks: List<Track>,
    active: Boolean = true,
    key: Any? = Unit,
): TrackSelectionState {
    val state = remember(key) { TrackSelectionState() }
    val validIds = remember(tracks) { tracks.mapTo(mutableSetOf(), Track::id) }
    LaunchedEffect(state, active, validIds) {
        if (active) {
            state.retain(validIds)
        } else {
            state.clear()
        }
    }
    return state
}
