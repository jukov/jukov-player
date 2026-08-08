package info.jukov.player.feature.album.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import info.jukov.player.feature.album.domain.Album

@Stable
class AlbumSelectionState internal constructor() {
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

    fun selectedAlbums(albums: List<Album>): List<Album> =
        albums.filter { it.id in selectedIds }

    fun areAllSelectedFavorite(albums: List<Album>): Boolean =
        selectedAlbums(albums).all(Album::isFavorite)

    fun finish(albums: List<Album>, action: (List<Album>) -> Unit) {
        action(selectedAlbums(albums))
        clear()
    }

    internal fun retain(validIds: Set<String>) {
        selectedIds = selectedIds intersect validIds
    }
}

@Composable
fun rememberAlbumSelectionState(
    albums: List<Album>,
    key: Any? = Unit,
): AlbumSelectionState {
    val state = remember(key) { AlbumSelectionState() }
    val validIds = albums.mapTo(mutableSetOf(), Album::id)
    LaunchedEffect(state, validIds) { state.retain(validIds) }
    return state
}
