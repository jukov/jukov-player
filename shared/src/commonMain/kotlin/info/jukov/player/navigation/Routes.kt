package info.jukov.player.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

object Routes {
    @Serializable
    data object Login : NavKey

    @Serializable
    data object Library : NavKey

    @Serializable
    data object Favorites : NavKey

    @Serializable
    data object Artists : NavKey

    @Serializable
    data class Albums(
        val artistId: String? = null,
        val artistName: String? = null,
    ) : NavKey

    @Serializable
    data class Tracks(
        val artistId: String? = null,
        val albumId: String? = null,
        val albumName: String? = null,
        val artistName: String? = null,
        val coverArtUrl: String? = null,
        val albumIsFavorite: Boolean = false,
    ) : NavKey {
        init {
            require(artistId == null || albumId == null) {
                "Tracks route cannot contain both artistId and albumId"
            }
        }
    }
}
