package info.jukov.player.feature.playback

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand

internal const val ACTION_SET_CURRENT_TRACK_FAVORITE =
    "info.jukov.player.action.SET_CURRENT_TRACK_FAVORITE"

internal fun setCurrentTrackFavoriteCommand() = SessionCommand(
    ACTION_SET_CURRENT_TRACK_FAVORITE,
    Bundle.EMPTY,
)

internal data class FavoriteCommandSpec(
    val icon: Int,
    val enabled: Boolean,
    val displayName: String,
    val slots: List<Int>,
)

internal fun favoriteCommandSpec(
    isFavorite: Boolean,
    enabled: Boolean,
    displayName: String,
) = FavoriteCommandSpec(
    icon = if (isFavorite) {
        CommandButton.ICON_HEART_FILLED
    } else {
        CommandButton.ICON_HEART_UNFILLED
    },
    enabled = enabled,
    displayName = displayName,
    slots = listOf(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW),
)

@OptIn(UnstableApi::class)
internal fun favoriteCommandButton(
    isFavorite: Boolean,
    enabled: Boolean,
    displayName: String,
): CommandButton {
    val spec = favoriteCommandSpec(isFavorite, enabled, displayName)
    return CommandButton.Builder(spec.icon)
        .setDisplayName(spec.displayName)
        .setSessionCommand(setCurrentTrackFavoriteCommand())
        .setEnabled(spec.enabled)
        .setSlots(*spec.slots.toIntArray())
        .build()
}
