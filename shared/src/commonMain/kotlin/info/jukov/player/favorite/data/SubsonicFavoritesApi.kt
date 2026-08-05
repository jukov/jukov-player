package info.jukov.player.favorite.data

import info.jukov.player.album.domain.Album
import info.jukov.player.artist.domain.Artist
import info.jukov.player.auth.domain.AuthSession
import info.jukov.player.favorite.domain.FavoriteTarget
import info.jukov.player.favorite.domain.Favorites
import info.jukov.player.subsonic.data.SubsonicApiClient
import info.jukov.player.subsonic.data.SubsonicEnvelopeDto
import info.jukov.player.track.domain.Track

class SubsonicFavoritesApi(private val client: SubsonicApiClient) : FavoritesApi {
    override suspend fun getFavorites(session: AuthSession): Favorites {
        val starred = client.get(
            endpoint = "getStarred2",
            session = session,
            deserializer = FavoritesResponseDto.serializer(),
        ).response.starred2 ?: StarredDto()
        return Favorites(
            tracks = starred.song.map { song ->
                Track(
                    id = song.id,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    albumId = song.albumId,
                    artistId = song.artistId,
                    trackNumber = song.track,
                    coverArtId = song.coverArt,
                    coverArtUrl = song.coverArt?.let {
                        client.buildUrl("getCoverArt", session, mapOf("id" to it))
                    },
                    streamUrl = client.buildUrl("stream", session, mapOf("id" to song.id)),
                    durationMs = song.duration?.times(1_000L) ?: 0,
                    contentType = song.contentType,
                    isFavorite = true,
                )
            },
            albums = starred.album.map { album ->
                Album(
                    id = album.id,
                    name = album.name,
                    artist = album.artist,
                    artistId = album.artistId,
                    coverArtUrl = album.coverArt?.let {
                        client.buildUrl("getCoverArt", session, mapOf("id" to it))
                    },
                    isFavorite = true,
                )
            },
            artists = starred.artist.map { artist ->
                Artist(
                    id = artist.id,
                    name = artist.name,
                    albumCount = artist.albumCount,
                    coverArtId = artist.coverArt,
                    isFavorite = true,
                )
            },
        )
    }

    override suspend fun setFavorite(
        session: AuthSession,
        target: FavoriteTarget,
        isFavorite: Boolean,
    ) {
        val parameter = when (target) {
            is FavoriteTarget.Track -> "id"
            is FavoriteTarget.Album -> "albumId"
            is FavoriteTarget.Artist -> "artistId"
        }
        client.get(
            endpoint = if (isFavorite) "star" else "unstar",
            session = session,
            deserializer = SubsonicEnvelopeDto.serializer(),
            parameters = mapOf(parameter to target.id),
        )
    }
}
