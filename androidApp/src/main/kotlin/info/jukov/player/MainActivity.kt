package info.jukov.player

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import android.content.res.Configuration
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import info.jukov.player.feature.download.DownloadForegroundService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var openDownloads by mutableStateOf(false)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            lifecycleScope.launch {
                (application as JukovApplication).graph.downloadsRepository.reconcile()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        requestNotificationPermissionIfNeeded()
        val darkTheme = resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        enableEdgeToEdge(
            statusBarStyle = systemBarStyle(darkTheme),
            navigationBarStyle = systemBarStyle(darkTheme),
        )

        setContent {
            App(
                graph = (application as JukovApplication).graph,
                openDownloads = openDownloads,
                onOpenDownloadsConsumed = { openDownloads = false },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(DownloadForegroundService.EXTRA_OPEN_DOWNLOADS, false) == true) {
            openDownloads = true
            intent.removeExtra(DownloadForegroundService.EXTRA_OPEN_DOWNLOADS)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun systemBarStyle(darkTheme: Boolean) = if (darkTheme) {
        SystemBarStyle.dark(Color.TRANSPARENT)
    } else {
        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    }
}
