package info.jukov.player.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

object Routes {
    @Serializable
    data object Login : NavKey

    @Serializable
    data object Library : NavKey

    @Serializable
    data object Artists : NavKey

    @Serializable
    data class Albums(val artistId: String? = null) : NavKey
}
