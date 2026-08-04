package info.jukov.player.subsonic.data

class SubsonicApiException(
    val code: Int,
    val helpUrl: String?,
    message: String,
) : IllegalStateException(message)
