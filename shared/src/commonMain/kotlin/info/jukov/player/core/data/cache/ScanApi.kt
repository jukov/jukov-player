package info.jukov.player.core.data.cache

import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.subsonic.data.SubsonicApiClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface ScanApi {
    suspend fun lastScan(session: AuthSession): String?
}

class SubsonicScanApi(private val client: SubsonicApiClient) : ScanApi {
    override suspend fun lastScan(session: AuthSession): String? = client.get(
        endpoint = "getScanStatus",
        session = session,
        deserializer = ScanResponse.serializer(),
    ).response.scanStatus?.lastScan
}

@Serializable
private data class ScanResponse(@SerialName("subsonic-response") val response: ScanPayload)

@Serializable
private data class ScanPayload(val scanStatus: ScanStatus? = null)

@Serializable
private data class ScanStatus(val lastScan: String? = null)
