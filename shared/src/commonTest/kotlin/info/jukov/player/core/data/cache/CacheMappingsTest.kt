package info.jukov.player.core.data.cache

import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.feature.auth.domain.accountKey
import info.jukov.player.feature.track.domain.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class CacheMappingsTest {
    @Test
    fun accountKeyNormalizesServerAndUsername() {
        val session = AuthSession(" HTTPS://Music.Example.COM/ ", "User", "token", "salt")

        assertEquals("https://music.example.com|user", session.accountKey)
    }

    @Test
    fun trackEntityDoesNotPersistAuthenticatedUrls() {
        val track = Track(
            id = "track", title = "Title", artist = "Artist", albumId = "album",
            artistId = "artist", trackNumber = 1, coverArtId = "cover",
            coverArtUrl = "https://server/rest/getCoverArt?t=secret",
            streamUrl = "https://server/rest/stream?t=secret", isFavorite = false,
        )

        val entity = track.toEntity("account")

        assertEquals("cover", entity.coverArtId)
        assertFalse(entity.toString().contains("secret"))
        assertNull(entity.contentType)
    }
}
