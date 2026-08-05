package info.jukov.player.album.data

import info.jukov.player.album.domain.Album
import info.jukov.player.auth.domain.AuthSession
import info.jukov.player.subsonic.data.SubsonicApiClient

class SubsonicAlbumsApi(private val client: SubsonicApiClient) : AlbumsApi {
    override suspend fun getAlbums(session: AuthSession, artistId: String?): List<Album> {
        val (response, artistName) = if (artistId == null) {
            val albums = buildList {
                var offset = 0
                do {
                    val page = client.get(
                        endpoint = "getAlbumList2",
                        session = session,
                        deserializer = AlbumsResponseDto.serializer(),
                        parameters = mapOf(
                            "type" to "alphabeticalByName",
                            "size" to PAGE_SIZE.toString(),
                            "offset" to offset.toString(),
                        ),
                    ).response.albumList2?.album.orEmpty()
                    addAll(page)
                    offset += page.size
                } while (page.size == PAGE_SIZE)
            }
            albums to ""
        } else {
            val artist = client.get(
                endpoint = "getArtist",
                session = session,
                deserializer = AlbumsResponseDto.serializer(),
                parameters = mapOf("id" to artistId),
            ).response.artist
            artist?.album.orEmpty() to artist?.name.orEmpty()
        }

        return response.map { album ->
            Album(
                id = album.id,
                name = album.name,
                artist = album.artist.ifBlank { artistName },
                artistId = album.artistId,
                coverArtUrl = album.coverArt?.let {
                    client.buildUrl(
                        endpoint = "getCoverArt",
                        session = session,
                        parameters = mapOf("id" to it),
                    )
                },
                isFavorite = album.starred != null,
            )
        }.sortedBy { it.name.lowercase() }
    }

    private companion object {
        const val PAGE_SIZE = 500
    }
}
