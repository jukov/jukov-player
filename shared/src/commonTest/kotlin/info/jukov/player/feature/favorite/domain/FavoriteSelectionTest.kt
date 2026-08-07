package info.jukov.player.feature.favorite.domain

import info.jukov.player.feature.track.domain.Track
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FavoriteSelectionTest {
    @Test
    fun removesWhenEverySelectedTrackIsFavorite() {
        assertFalse(
            favoriteStateForSelection(
                listOf(
                    track("one", isFavorite = true),
                    track("two", isFavorite = true),
                ),
            ),
        )
    }

    @Test
    fun addsWhenAtLeastOneSelectedTrackIsNotFavorite() {
        assertTrue(
            favoriteStateForSelection(
                listOf(
                    track("one", isFavorite = true),
                    track("two", isFavorite = false),
                ),
            ),
        )
    }

    private fun track(id: String, isFavorite: Boolean) = Track(
        id = id,
        title = id,
        artist = "Artist",
        albumId = null,
        artistId = null,
        trackNumber = null,
        coverArtUrl = null,
        isFavorite = isFavorite,
    )
}
