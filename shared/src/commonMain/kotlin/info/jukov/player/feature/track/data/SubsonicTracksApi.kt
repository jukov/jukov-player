package info.jukov.player.feature.track.data

import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.subsonic.data.SubsonicApiClient
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.feature.track.domain.TracksFilter
import info.jukov.player.core.domain.Page

class SubsonicTracksApi(private val client: SubsonicApiClient) : TracksApi {
    override suspend fun getTracksPage(session: AuthSession, offset: Int, size: Int): Page<Track> {
        val tracks = requestAllTracksPage(session, offset, size)
        return Page(
            items = tracks.withSharedAlbumCoverArt().map { it.toDomain(session) },
            hasMore = tracks.size == size,
        )
    }

    override suspend fun getTracks(session: AuthSession, filter: TracksFilter): List<Track> {
        val tracks = when (filter) {
            TracksFilter.All -> getAllTracks(session)
            is TracksFilter.ByAlbum -> getAlbumTracks(session, filter.albumId)
            is TracksFilter.ByArtist -> getArtistTracks(session, filter.artistId)
        }
        return tracks.withSharedAlbumCoverArt().map { it.toDomain(session) }
    }

    private suspend fun getAllTracks(session: AuthSession): List<TrackDto> = buildList {
        var offset = 0
        do {
            val page = requestAllTracksPage(session, offset, PAGE_SIZE)
            addAll(page)
            offset += page.size
        } while (page.size == PAGE_SIZE)
    }

    private suspend fun requestAllTracksPage(session: AuthSession, offset: Int, size: Int) =
        client.get(
            endpoint = "search3",
            session = session,
            deserializer = TracksResponseDto.serializer(),
            parameters = mapOf(
                "query" to "",
                "artistCount" to "0",
                "albumCount" to "0",
                "songCount" to size.toString(),
                "songOffset" to offset.toString(),
            ),
        ).response.searchResult3?.song.orEmpty()

    private suspend fun getArtistTracks(
        session: AuthSession,
        artistId: String,
    ): List<TrackDto> {
        val albums = client.get(
            endpoint = "getArtist",
            session = session,
            deserializer = TracksResponseDto.serializer(),
            parameters = mapOf("id" to artistId),
        ).response.artist?.album.orEmpty()
        return albums.flatMap { getAlbumTracks(session, it.id) }
    }

    private suspend fun getAlbumTracks(session: AuthSession, albumId: String): List<TrackDto> {
        val album = client.get(
            endpoint = "getAlbum",
            session = session,
            deserializer = TracksResponseDto.serializer(),
            parameters = mapOf("id" to albumId),
        ).response.album ?: return emptyList()
        return album.song.map { song ->
            song.copy(coverArt = album.coverArt ?: song.coverArt)
        }
    }

    private fun TrackDto.toDomain(session: AuthSession) = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        artistId = artistId,
        trackNumber = track,
        year = year,
        coverArtId = coverArt,
        coverArtUrl = coverArt?.let {
            client.buildUrl("getCoverArt", session, mapOf("id" to it))
        },
        streamUrl = client.buildUrl("stream", session, mapOf("id" to id)),
        durationMs = duration?.times(1_000L) ?: 0,
        contentType = contentType,
        isFavorite = starred != null,
    )

    private companion object {
        const val PAGE_SIZE = 500
    }
}
