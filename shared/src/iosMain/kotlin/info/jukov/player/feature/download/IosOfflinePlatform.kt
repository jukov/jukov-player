package info.jukov.player.feature.download

import info.jukov.player.feature.download.domain.OfflinePlatform

object IosOfflinePlatform : OfflinePlatform {
    override fun enqueue(accountKey: String) = Unit

    override fun recover(accountKey: String) = Unit

    override fun cancelTrack(accountKey: String, trackId: String) = Unit

    override fun cancelTracks(accountKey: String, trackIds: List<String>) = Unit

    override fun cancelAccount(accountKey: String) = Unit

    override fun deleteTrack(accountKey: String, relativePath: String?) = Unit

    override fun deleteTracks(accountKey: String, relativePaths: List<String>) = Unit

    override fun deleteArtwork(accountKey: String, relativePath: String?) = Unit

    override fun deleteArtworks(accountKey: String, relativePaths: List<String>) = Unit

    override fun deleteAccount(accountKey: String) = Unit

    override fun fileUri(accountKey: String, relativePath: String): String = relativePath

    override fun exists(accountKey: String, relativePath: String): Boolean = false

    override fun cleanupStaleParts(accountKey: String, activeTrackIds: Set<String>) = Unit
}
