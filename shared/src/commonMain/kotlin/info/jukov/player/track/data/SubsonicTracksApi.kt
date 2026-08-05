package info.jukov.player.track.data

import info.jukov.player.auth.domain.AuthSession
import info.jukov.player.subsonic.data.SubsonicApiClient
import info.jukov.player.track.domain.Track
import info.jukov.player.track.domain.TracksFilter

class SubsonicTracksApi(private val client: SubsonicApiClient) : TracksApi {
    override suspend fun getTracks(session: AuthSession, filter: TracksFilter): List<Track> {
        val tracks = when (filter) {
            TracksFilter.All -> getAllTracks(session)
            is TracksFilter.ByAlbum -> getAlbumTracks(session, filter.albumId)
            is TracksFilter.ByArtist -> getArtistTracks(session, filter.artistId)
        }
        return tracks.map { it.toDomain(session) }
    }

    private suspend fun getAllTracks(session: AuthSession): List<TrackDto> = buildList {
        var offset = 0
        do {
            val page = client.get(
                endpoint = "search3",
                session = session,
                deserializer = TracksResponseDto.serializer(),
                parameters = mapOf(
                    "query" to "",
                    "artistCount" to "0",
                    "albumCount" to "0",
                    "songCount" to PAGE_SIZE.toString(),
                    "songOffset" to offset.toString(),
                ),
            ).response.searchResult3?.song.orEmpty()
            addAll(page)
            offset += page.size
        } while (page.size == PAGE_SIZE)
    }

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

    private suspend fun getAlbumTracks(session: AuthSession, albumId: String): List<TrackDto> =
        client.get(
            endpoint = "getAlbum",
            session = session,
            deserializer = TracksResponseDto.serializer(),
            parameters = mapOf("id" to albumId),
        ).response.album?.song.orEmpty()

    private fun TrackDto.toDomain(session: AuthSession) = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        artistId = artistId,
        trackNumber = track,
        coverArtId = coverArt,
        coverArtUrl = coverArt?.let {
            client.buildUrl("getCoverArt", session, mapOf("id" to it))
        },
        streamUrl = client.buildUrl("stream", session, mapOf("id" to id)),
        durationMs = duration?.times(1_000L) ?: 0,
        contentType = contentType,
        isStarred = starred != null,
    )

    private companion object {
        const val PAGE_SIZE = 500
    }
}
