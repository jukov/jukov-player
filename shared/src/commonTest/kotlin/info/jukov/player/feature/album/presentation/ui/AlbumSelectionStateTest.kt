package info.jukov.player.feature.album.presentation.ui

import info.jukov.player.feature.album.domain.Album
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlbumSelectionStateTest {
    @Test
    fun finishPassesSelectedAlbumsInSourceOrderAndClearsSelection() {
        val state = AlbumSelectionState()
        val albums = listOf(album("first"), album("second"), album("third"))
        state.setSelected("third", selected = true)
        state.setSelected("first", selected = true)
        var selected = emptyList<Album>()

        state.finish(albums) { selected = it }

        assertEquals(listOf("first", "third"), selected.map(Album::id))
        assertFalse(state.isActive)
    }

    @Test
    fun favoriteStateReflectsOnlySelectedAlbums() {
        val state = AlbumSelectionState()
        val albums = listOf(
            album("favorite", isFavorite = true),
            album("not-favorite", isFavorite = false),
        )
        state.setSelected("favorite", selected = true)

        assertTrue(state.areAllSelectedFavorite(albums))

        state.setSelected("not-favorite", selected = true)

        assertFalse(state.areAllSelectedFavorite(albums))
    }

    @Test
    fun retainDropsAlbumsThatAreNoLongerVisible() {
        val state = AlbumSelectionState()
        state.setSelected("visible", selected = true)
        state.setSelected("removed", selected = true)

        state.retain(setOf("visible"))

        assertEquals(setOf("visible"), state.selectedIds)
    }

    private fun album(id: String, isFavorite: Boolean = false) = Album(
        id = id,
        name = "Album $id",
        artist = "Artist",
        artistId = null,
        coverArtUrl = null,
        isFavorite = isFavorite,
    )
}
