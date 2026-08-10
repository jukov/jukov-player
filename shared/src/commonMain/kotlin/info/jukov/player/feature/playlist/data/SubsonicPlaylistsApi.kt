package info.jukov.player.feature.playlist.data

import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.feature.playlist.domain.Playlist
import info.jukov.player.feature.track.domain.Track
import info.jukov.player.subsonic.data.SubsonicApiClient
import io.ktor.http.Parameters
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class SubsonicPlaylistsApi(private val client: SubsonicApiClient) : PlaylistsApi {
    override suspend fun getPlaylists(session: AuthSession): List<Playlist> =
        client.get("getPlaylists", session, PlaylistResponseDto.serializer())
            .response.playlists?.playlist.orEmpty().map { it.toDomain(session) }

    override suspend fun getPlaylist(session: AuthSession, id: String): Playlist =
        requireNotNull(
            client.get(
                "getPlaylist", session, PlaylistResponseDto.serializer(), mapOf("id" to id),
            ).response.playlist,
        ).toDomain(session)

    override suspend fun createPlaylist(
        session: AuthSession,
        name: String,
        songIds: List<String>,
    ): Playlist? = mutate("createPlaylist", session, Parameters.build {
        append("name", name)
        songIds.forEach { append("songId", it) }
    }).response.playlist?.toDomain(session)

    override suspend fun updatePlaylist(
        session: AuthSession,
        id: String,
        name: String,
        isPublic: Boolean,
    ) {
        mutate("updatePlaylist", session, Parameters.build {
            append("playlistId", id)
            append("name", name)
            append("public", isPublic.toString())
        })
    }

    override suspend fun addTracks(session: AuthSession, playlistId: String, songIds: List<String>) {
        mutate("updatePlaylist", session, Parameters.build {
            append("playlistId", playlistId)
            songIds.forEach { append("songIdToAdd", it) }
        })
    }

    override suspend fun removeTracks(session: AuthSession, playlistId: String, songIndexes: List<Int>) {
        mutate("updatePlaylist", session, Parameters.build {
            append("playlistId", playlistId)
            songIndexes.forEach { append("songIndexToRemove", it.toString()) }
        })
    }

    override suspend fun deletePlaylist(session: AuthSession, id: String) {
        mutate("deletePlaylist", session, Parameters.build { append("id", id) })
    }

    private suspend fun mutate(endpoint: String, session: AuthSession, parameters: Parameters) =
        client.get(endpoint, session, PlaylistResponseDto.serializer(), parameters)

    private fun PlaylistDto.toDomain(session: AuthSession) = Playlist(
        id = id, name = name, owner = owner, songCount = songCount,
        durationSeconds = duration, readOnly = readOnly, isPublic = isPublic,
        tracks = entry.map { it.toDomain(session) },
    )

    private fun PlaylistTrackDto.toDomain(session: AuthSession) = Track(
        id = id, title = title, artist = artist, album = album, albumId = albumId,
        artistId = artistId, trackNumber = track, year = year, coverArtId = coverArt,
        coverArtUrl = coverArt?.let { client.buildUrl("getCoverArt", session, mapOf("id" to it)) },
        streamUrl = client.buildUrl("stream", session, mapOf("id" to id)),
        durationMs = duration?.times(1_000L) ?: 0, contentType = contentType,
        isFavorite = starred != null,
    )
}

@Serializable private data class PlaylistResponseDto(@SerialName("subsonic-response") val response: PlaylistPayloadDto)
@Serializable private data class PlaylistPayloadDto(val playlists: PlaylistListDto? = null, val playlist: PlaylistDto? = null)
@Serializable private data class PlaylistListDto(val playlist: List<PlaylistDto> = emptyList())
@Serializable private data class PlaylistDto(
    val id: String, val name: String, val owner: String? = null, val songCount: Int = 0,
    val duration: Long = 0, @SerialName("readonly") val readOnly: Boolean = false,
    @SerialName("public") val isPublic: Boolean = false,
    val entry: List<PlaylistTrackDto> = emptyList(),
)
@Serializable private data class PlaylistTrackDto(
    val id: String, val title: String, val artist: String = "", val album: String? = null,
    val albumId: String? = null, val artistId: String? = null, val track: Int? = null,
    val year: Int? = null, val coverArt: String? = null, val duration: Int? = null,
    val contentType: String? = null, val starred: String? = null,
)
