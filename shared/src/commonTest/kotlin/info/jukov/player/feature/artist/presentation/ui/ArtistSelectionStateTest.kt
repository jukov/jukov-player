package info.jukov.player.feature.artist.presentation.ui

import info.jukov.player.feature.artist.domain.Artist
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtistSelectionStateTest {
    @Test
    fun selectionResolvesArtistsInDisplayOrderAndClearsAfterAction() {
        val first = artist("first")
        val second = artist("second")
        val state = ArtistSelectionState()
        state.setSelected(second.id, selected = true)
        state.setSelected(first.id, selected = true)

        var selected = emptyList<Artist>()
        state.finish(listOf(first, second)) { selected = it }

        assertEquals(listOf(first, second), selected)
        assertFalse(state.isActive)
    }

    @Test
    fun favoriteStateReflectsOnlySelectedArtists() {
        val state = ArtistSelectionState()
        val favorite = artist("favorite", isFavorite = true)
        val regular = artist("regular")
        state.setSelected(favorite.id, selected = true)

        assertTrue(state.areAllSelectedFavorite(listOf(favorite, regular)))
        state.setSelected(regular.id, selected = true)
        assertFalse(state.areAllSelectedFavorite(listOf(favorite, regular)))
    }

    private fun artist(id: String, isFavorite: Boolean = false) = Artist(
        id = id,
        name = id,
        albumCount = 1,
        coverArtId = null,
        isFavorite = isFavorite,
    )
}
