package info.jukov.player.core.presentation.ui

import androidx.compose.runtime.Composable
import info.jukov.player.core.domain.AppError
import jukovplayer.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppError.localizedMessage(): String = when (this) {
    AppError.ServerAddressRequired -> stringResource(Res.string.error_server_address_required)
    AppError.UsernameRequired -> stringResource(Res.string.error_username_required)
    AppError.PasswordRequired -> stringResource(Res.string.error_password_required)
    AppError.AuthenticationRejected -> stringResource(Res.string.error_authentication_rejected)
    AppError.AuthenticationRequired -> stringResource(Res.string.error_authentication_required)
    AppError.AlbumsLoadFailed -> stringResource(Res.string.error_albums_load_failed)
    AppError.ArtistsLoadFailed -> stringResource(Res.string.error_artists_load_failed)
    AppError.TracksLoadFailed -> stringResource(Res.string.error_tracks_load_failed)
    AppError.FavoritesLoadFailed -> stringResource(Res.string.error_favorites_load_failed)
    AppError.FavoriteUpdateFailed -> stringResource(Res.string.error_favorite_update_failed)
    AppError.DownloadFailed -> stringResource(Res.string.error_download_failed)
    AppError.PlaylistsLoadFailed -> stringResource(Res.string.error_playlists_load_failed)
    AppError.PlaylistLoadFailed -> stringResource(Res.string.error_playlist_load_failed)
    AppError.PlaylistUpdateFailed -> stringResource(Res.string.error_playlist_update_failed)
    AppError.SearchFailed -> stringResource(Res.string.error_search_failed)
    is AppError.OpenSubsonic -> stringResource(Res.string.error_opensubsonic, code)
    is AppError.Http -> stringResource(Res.string.error_http, statusCode)
    AppError.InvalidServerResponse -> stringResource(Res.string.error_invalid_server_response)
    is AppError.UnknownOpenSubsonicStatus ->
        stringResource(Res.string.error_unknown_opensubsonic_status, status)
    AppError.PlayerConnectionFailed -> stringResource(Res.string.error_player_connection_failed)
    AppError.MissingTrackStreamUrl -> stringResource(Res.string.error_missing_track_stream_url)
    AppError.PlaybackFailed -> stringResource(Res.string.error_playback_failed)
}
