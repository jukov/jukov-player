package info.jukov.player.feature.artist.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import info.jukov.player.feature.artist.domain.Artist

@Stable
class ArtistSelectionState internal constructor() {
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

    fun selectedArtists(artists: List<Artist>): List<Artist> = artists.filter { it.id in selectedIds }

    fun areAllSelectedFavorite(artists: List<Artist>): Boolean =
        selectedArtists(artists).all(Artist::isFavorite)

    fun finish(artists: List<Artist>, action: (List<Artist>) -> Unit) {
        action(selectedArtists(artists))
        clear()
    }

    internal fun retain(validIds: Set<String>) {
        selectedIds = selectedIds intersect validIds
    }
}

@Composable
fun rememberArtistSelectionState(artists: List<Artist>, key: Any? = Unit): ArtistSelectionState {
    val state = remember(key) { ArtistSelectionState() }
    val validIds = artists.mapTo(mutableSetOf(), Artist::id)
    LaunchedEffect(state, validIds) { state.retain(validIds) }
    return state
}
