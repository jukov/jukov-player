package info.jukov.player.feature.download.data

import info.jukov.player.core.data.cache.AlbumEntity
import info.jukov.player.core.data.cache.DownloadOwnershipEntity
import info.jukov.player.core.data.cache.OfflineAlbumEntity
import info.jukov.player.core.data.cache.OfflineTrackEntity
import info.jukov.player.core.data.cache.TrackEntity
import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.feature.download.domain.DownloadState
import info.jukov.player.feature.download.domain.OfflinePlatform
import info.jukov.player.subsonic.data.SubsonicApiClient
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class OfflineLibraryBuilderTest {
    @Test
    fun buildsAlbumsFromAlbumOwnershipInPositionOrderWhileKeepingTrackSearchFiltered() {
        val first = offlineTrack("first", requestedAtMs = 10)
        val second = offlineTrack("second", requestedAtMs = 20)
        val library = buildOfflineLibrary(
            platform = FakeOfflinePlatform(),
            client = SubsonicApiClient(HttpClient(), Json),
            session = SESSION,
            snapshot = OfflineLibrarySnapshot(
                visibleDownloads = listOf(second),
                allDownloads = listOf(second, first),
                offlineAlbums = listOf(OfflineAlbumEntity(ACCOUNT_KEY, "album", 2, 5)),
                ownerships = listOf(
                    DownloadOwnershipEntity(ACCOUNT_KEY, DOWNLOAD_OWNER_ALBUM, "album", "second", 1),
                    DownloadOwnershipEntity(ACCOUNT_KEY, DOWNLOAD_OWNER_TRACK, "first", "first", 0),
                    DownloadOwnershipEntity(ACCOUNT_KEY, DOWNLOAD_OWNER_ALBUM, "album", "first", 0),
                ),
                artworks = emptyList(),
                tracks = listOf(track("first"), track("second")),
                albums = listOf(album("album")),
            ),
        )

        assertEquals(listOf("second"), library.tracks.map { it.track.id })
        assertEquals(listOf("first", "second"), library.albums.single().tracks.map { it.track.id })
        assertEquals(2, library.albums.single().expectedTrackCount)
    }

    @Test
    fun ignoresDownloadsAndAlbumsWhoseMetadataIsMissing() {
        val missing = offlineTrack("missing", requestedAtMs = 10)

        val library = buildOfflineLibrary(
            platform = FakeOfflinePlatform(),
            client = SubsonicApiClient(HttpClient(), Json),
            session = SESSION,
            snapshot = OfflineLibrarySnapshot(
                visibleDownloads = listOf(missing),
                allDownloads = listOf(missing),
                offlineAlbums = listOf(OfflineAlbumEntity(ACCOUNT_KEY, "missing-album", 1, 5)),
                ownerships = listOf(
                    DownloadOwnershipEntity(
                        ACCOUNT_KEY,
                        DOWNLOAD_OWNER_ALBUM,
                        "missing-album",
                        "missing",
                        0,
                    ),
                ),
                artworks = emptyList(),
                tracks = emptyList(),
                albums = emptyList(),
            ),
        )

        assertEquals(emptyList(), library.tracks)
        assertEquals(emptyList(), library.albums)
    }

    private fun track(id: String) = TrackEntity(
        accountKey = ACCOUNT_KEY,
        id = id,
        title = "Title $id",
        artist = "Artist",
        album = "Album",
        albumId = "album",
        artistId = "artist",
        trackNumber = null,
        year = null,
        coverArtId = null,
        durationMs = 1_000,
        contentType = "audio/mpeg",
        isFavorite = false,
    )

    private fun album(id: String) = AlbumEntity(
        accountKey = ACCOUNT_KEY,
        id = id,
        name = "Album",
        artist = "Artist",
        artistId = "artist",
        year = null,
        coverArtId = null,
        isFavorite = false,
    )

    private fun offlineTrack(id: String, requestedAtMs: Long) = OfflineTrackEntity(
        accountKey = ACCOUNT_KEY,
        trackId = id,
        relativePath = "$id.mp3",
        expectedSize = 100,
        downloadedBytes = 100,
        state = DownloadState.Completed.name,
        error = null,
        requestedAtMs = requestedAtMs,
        completedAtMs = requestedAtMs,
    )

    private companion object {
        val SESSION = AuthSession("https://music.example", "user", "token", "salt")
        const val ACCOUNT_KEY = "https://music.example|user"
    }
}

private class FakeOfflinePlatform : OfflinePlatform {
    override fun enqueue(accountKey: String) = Unit
    override fun recover(accountKey: String) = Unit
    override suspend fun cancelTrack(accountKey: String, trackId: String) = Unit
    override suspend fun cancelTracks(accountKey: String, trackIds: List<String>) = Unit
    override suspend fun cancelAccount(accountKey: String) = Unit
    override fun deleteTrack(accountKey: String, relativePath: String?) = Unit
    override fun deleteTracks(accountKey: String, relativePaths: List<String>) = Unit
    override fun deleteArtwork(accountKey: String, relativePath: String?) = Unit
    override fun deleteArtworks(accountKey: String, relativePaths: List<String>) = Unit
    override fun deleteAccount(accountKey: String) = Unit
    override fun fileUri(accountKey: String, relativePath: String) = "file://$accountKey/$relativePath"
    override fun exists(accountKey: String, relativePath: String) = true
    override fun cleanupStaleParts(accountKey: String, activeTrackIds: Set<String>) = Unit
}
