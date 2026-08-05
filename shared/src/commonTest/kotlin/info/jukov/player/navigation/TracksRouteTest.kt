package info.jukov.player.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TracksRouteTest {
    @Test
    fun allTracksRouteHasNoFilterIds() {
        val route = Routes.Tracks()

        assertNull(route.artistId)
        assertNull(route.albumId)
    }

    @Test
    fun routeAcceptsExactlyOneFilterId() {
        assertEquals("artist", Routes.Tracks(artistId = "artist").artistId)
        assertEquals("album", Routes.Tracks(albumId = "album").albumId)
    }

    @Test
    fun routeRejectsArtistAndAlbumIdsTogether() {
        assertFailsWith<IllegalArgumentException> {
            Routes.Tracks(artistId = "artist", albumId = "album")
        }
    }
}
