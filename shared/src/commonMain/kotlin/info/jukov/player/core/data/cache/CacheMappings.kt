package info.jukov.player.core.data.cache

import info.jukov.player.feature.album.domain.Album
import info.jukov.player.feature.artist.domain.Artist
import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.subsonic.data.SubsonicApiClient

fun Artist.toEntity(accountKey: String) = ArtistEntity(accountKey, id, name, albumCount, coverArtId, isFavorite)
fun ArtistEntity.toDomain() = Artist(id, name, albumCount, coverArtId, isFavorite)

fun Album.toEntity(accountKey: String) =
    AlbumEntity(accountKey, id, name, artist, artistId, year, coverArtId, isFavorite)
fun AlbumEntity.toDomain(session: AuthSession, client: SubsonicApiClient) = Album(
    id = id,
    name = name,
    artist = artist,
    artistId = artistId,
    year = year,
    coverArtId = coverArtId,
    coverArtUrl = coverArtId?.let { client.buildUrl("getCoverArt", session, mapOf("id" to it)) },
    isFavorite = isFavorite,
)

fun Track.toEntity(accountKey: String) = TrackEntity(
    accountKey, id, title, artist, album, albumId, artistId, trackNumber, year, coverArtId,
    durationMs, contentType, isFavorite,
)

fun TrackEntity.toDomain(session: AuthSession, client: SubsonicApiClient) = Track(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    artistId = artistId,
    trackNumber = trackNumber,
    year = year,
    coverArtId = coverArtId,
    coverArtUrl = coverArtId?.let { client.buildUrl("getCoverArt", session, mapOf("id" to it)) },
    streamUrl = client.buildUrl("stream", session, mapOf("id" to id)),
    durationMs = durationMs,
    contentType = contentType,
    isFavorite = isFavorite,
)
