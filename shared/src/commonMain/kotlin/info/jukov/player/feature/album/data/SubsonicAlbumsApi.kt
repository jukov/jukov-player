package info.jukov.player.feature.album.data

import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.subsonic.data.SubsonicApiClient
import info.jukov.player.core.domain.Page
import info.jukov.player.core.domain.AlbumSortCriterion
import info.jukov.player.core.domain.SortDirection
import info.jukov.player.core.domain.SortOption

class SubsonicAlbumsApi(private val client: SubsonicApiClient) : AlbumsApi {
    override suspend fun getAlbumsPage(session: AuthSession, offset: Int, size: Int, sort: SortOption<AlbumSortCriterion>): Page<Album> {
        val albums = requestAlbumsPage(session, offset, size, sort)
        return Page(albums.map { it.toDomain(session) }, hasMore = albums.size == size)
    }

    override suspend fun getAlbums(session: AuthSession, artistId: String?): List<Album> {
        val (response, artistName) = if (artistId == null) {
            val albums = buildList {
                var offset = 0
                do {
                    val page = requestAlbumsPage(
                        session,
                        offset,
                        PAGE_SIZE,
                        SortOption(AlbumSortCriterion.Title, SortDirection.Ascending),
                    )
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

        return response.map { it.toDomain(session, artistName) }.sortedBy { it.name.lowercase() }
    }

    private suspend fun requestAlbumsPage(session: AuthSession, offset: Int, size: Int, sort: SortOption<AlbumSortCriterion>) =
        client.get(
            endpoint = "getAlbumList2",
            session = session,
            deserializer = AlbumsResponseDto.serializer(),
            parameters = buildMap {
                put("type", when (sort.criterion) {
                    AlbumSortCriterion.Title -> "alphabeticalByName"
                    AlbumSortCriterion.Artist -> "alphabeticalByArtist"
                    AlbumSortCriterion.Year -> "byYear"
                })
                put("size", size.toString())
                put("offset", offset.toString())
                if (sort.criterion == AlbumSortCriterion.Year) {
                    put("fromYear", if (sort.direction == SortDirection.Ascending) "0" else "9999")
                    put("toYear", if (sort.direction == SortDirection.Ascending) "9999" else "0")
                }
            },
        ).response.albumList2?.album.orEmpty()

    private fun AlbumDto.toDomain(session: AuthSession, fallbackArtist: String = "") = Album(
        id = id,
        name = name,
        artist = artist.ifBlank { fallbackArtist },
        artistId = artistId,
        year = year,
        coverArtId = coverArt,
        coverArtUrl = coverArt?.let {
            client.buildUrl("getCoverArt", session, mapOf("id" to it))
        },
        isFavorite = starred != null,
    )

    private companion object {
        const val PAGE_SIZE = 500
    }
}
