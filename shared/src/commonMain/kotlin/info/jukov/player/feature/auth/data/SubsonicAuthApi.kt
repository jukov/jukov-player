package info.jukov.player.feature.auth.data

import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.subsonic.data.SubsonicApiClient
import info.jukov.player.subsonic.data.SubsonicEnvelopeDto

class SubsonicAuthApi(private val client: SubsonicApiClient) : AuthApi {
    override suspend fun ping(session: AuthSession): Boolean =
        client.get(
            endpoint = "ping",
            session = session,
            deserializer = SubsonicEnvelopeDto.serializer(),
        ).response.status == "ok"
}
