package info.jukov.player.feature.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object PlaybackNotificationIntent {
    const val EXTRA_OPEN_PLAYER = "info.jukov.player.extra.OPEN_PLAYER"

    fun pendingIntent(context: Context): PendingIntent {
        val intent = requireNotNull(
            context.packageManager.getLaunchIntentForPackage(context.packageName),
        ) { "The application must have a launch activity" }.apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_PLAYER, true)
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private const val REQUEST_CODE = 1_001
}
