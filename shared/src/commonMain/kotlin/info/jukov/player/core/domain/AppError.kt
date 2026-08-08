package info.jukov.player.core.domain

sealed interface AppError {
    data object ServerAddressRequired : AppError
    data object UsernameRequired : AppError
    data object PasswordRequired : AppError
    data object AuthenticationRejected : AppError
    data object AuthenticationRequired : AppError

    data object AlbumsLoadFailed : AppError
    data object ArtistsLoadFailed : AppError
    data object TracksLoadFailed : AppError
    data object FavoritesLoadFailed : AppError
    data object FavoriteUpdateFailed : AppError
    data object PlaylistsLoadFailed : AppError
    data object PlaylistLoadFailed : AppError
    data object PlaylistUpdateFailed : AppError
    data object SearchFailed : AppError

    data class OpenSubsonic(val code: Int) : AppError
    data class Http(val statusCode: Int) : AppError
    data object InvalidServerResponse : AppError
    data class UnknownOpenSubsonicStatus(val status: String) : AppError

    data object PlayerConnectionFailed : AppError
    data object MissingTrackStreamUrl : AppError
    data object PlaybackFailed : AppError
    data object IosPlaybackNotImplemented : AppError
}

class AppException(val error: AppError) : IllegalStateException()

fun Throwable.toAppError(fallback: AppError): AppError =
    (this as? AppException)?.error ?: fallback
