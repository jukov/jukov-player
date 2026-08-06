package info.jukov.player.feature.track.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AlbumCoverArtTest {
    @Test
    fun sharesOneCoverArtBetweenTracksFromTheSameAlbum() {
        val tracks = listOf(
            track(id = "one", albumId = "album", coverArt = "cover-one"),
            track(id = "two", albumId = "album", coverArt = null),
            track(id = "three", albumId = "album", coverArt = "cover-three"),
        )

        val result = tracks.withSharedAlbumCoverArt()

        assertEquals(listOf("cover-one", "cover-one", "cover-one"), result.map { it.coverArt })
    }

    @Test
    fun doesNotShareCoverArtBetweenAlbums() {
        val result = listOf(
            track(id = "one", albumId = "first", coverArt = "first-cover"),
            track(id = "two", albumId = "second", coverArt = null),
            track(id = "three", albumId = null, coverArt = null),
        ).withSharedAlbumCoverArt()

        assertEquals("first-cover", result[0].coverArt)
        assertNull(result[1].coverArt)
        assertNull(result[2].coverArt)
    }

    private fun track(id: String, albumId: String?, coverArt: String?) = TrackDto(
        id = id,
        title = id,
        albumId = albumId,
        coverArt = coverArt,
    )
}
