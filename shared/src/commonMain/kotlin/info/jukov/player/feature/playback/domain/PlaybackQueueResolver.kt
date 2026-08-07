package info.jukov.player.feature.playback.domain

import info.jukov.player.feature.download.domain.DownloadsRepository
import info.jukov.player.feature.track.domain.Track

interface PlaybackQueueResolver {
    suspend fun resolve(tracks: List<Track>): List<Track>
}

class DefaultPlaybackQueueResolver(
    private val downloadsRepository: DownloadsRepository,
) : PlaybackQueueResolver {
    override suspend fun resolve(tracks: List<Track>): List<Track> {
        val localTracks = downloadsRepository.localTrackUris(tracks.map(Track::id))
        val coverArtIds = tracks.mapNotNull(Track::coverArtId).distinct()
        val localArtwork = downloadsRepository.localArtworkUris(coverArtIds)
        return tracks.map { track ->
            track.copy(
                streamUrl = localTracks[track.id] ?: track.streamUrl,
                coverArtUrl = track.coverArtId?.let(localArtwork::get) ?: track.coverArtUrl,
            )
        }
    }
}
