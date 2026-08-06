package info.jukov.player.feature.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PlaybackMediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return
        val serviceIntent = Intent(context, PlaybackService::class.java).apply {
            action = intent.action
            intent.extras?.let(::putExtras)
        }
        context.startForegroundService(serviceIntent)
    }
}
