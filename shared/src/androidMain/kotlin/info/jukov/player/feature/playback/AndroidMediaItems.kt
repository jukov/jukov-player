package info.jukov.player.feature.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import info.jukov.player.feature.track.domain.Track

internal fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(requireNotNull(streamUrl) { "Track $id has no stream URL" })
    .setMimeType(contentType)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(coverArtUrl?.let(Uri::parse))
            .setDurationMs(durationMs.takeIf { it > 0 })
            .setIsPlayable(true)
            .build(),
    )
    .build()
