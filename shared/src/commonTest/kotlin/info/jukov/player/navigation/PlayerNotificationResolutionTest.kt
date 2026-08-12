package info.jukov.player.navigation

import info.jukov.player.core.domain.LoadableState
import info.jukov.player.feature.playback.presentation.PlayerUiState
import info.jukov.player.feature.track.domain.Track
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerNotificationResolutionTest {
    @Test
    fun waitsForPlaybackStateBeforeDecidingThereIsNoTrack() {
        assertEquals(
            PlayerNotificationResolution.WaitForPlayback,
            resolvePlayerNotification(
                requested = true,
                playbackState = LoadableState.Loading(),
            ),
        )
    }

    @Test
    fun opensPlayerWhenPlaybackHasCurrentTrack() {
        assertEquals(
            PlayerNotificationResolution.OpenPlayer,
            resolvePlayerNotification(
                requested = true,
                playbackState = LoadableState.Content(
                    PlayerUiState(currentTrack = track()),
                ),
            ),
        )
    }

    @Test
    fun showsLibraryWhenLoadedPlaybackHasNoTrack() {
        assertEquals(
            PlayerNotificationResolution.ShowLibrary,
            resolvePlayerNotification(
                requested = true,
                playbackState = LoadableState.Content(PlayerUiState()),
            ),
        )
    }

    private fun track() = Track(
        id = "track-id",
        title = "Track",
        artist = "Artist",
        album = "Album",
        albumId = "album-id",
        artistId = "artist-id",
        trackNumber = 1,
        coverArtUrl = null,
        durationMs = 180_000,
        isFavorite = false,
    )
}
