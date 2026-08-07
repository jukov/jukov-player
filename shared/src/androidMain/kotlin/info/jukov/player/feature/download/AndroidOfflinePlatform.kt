package info.jukov.player.feature.download

import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import info.jukov.player.feature.download.domain.OfflinePlatform
import java.io.File

class AndroidOfflinePlatform(context: Context) : OfflinePlatform {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    override fun enqueue(accountKey: String) {
        appContext.startForegroundService(
            downloadServiceIntent(accountKey).setAction(DownloadForegroundService.ACTION_QUEUE_CHANGED),
        )
        scheduleRecovery(accountKey)
    }

    override fun recover(accountKey: String) {
        scheduleRecovery(accountKey)
    }

    internal fun scheduleRecovery(accountKey: String) {
        val request = OneTimeWorkRequestBuilder<DownloadRecoveryWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(Data.Builder().putString(KEY_ACCOUNT, accountKey).build())
            .setInitialDelay(RECOVERY_DELAY_MINUTES, java.util.concurrent.TimeUnit.MINUTES)
            .addTag(accountTag(accountKey))
            .build()
        workManager.enqueueUniqueWork(workName(accountKey), ExistingWorkPolicy.REPLACE, request)
    }

    internal fun startService(accountKey: String) {
        appContext.startForegroundService(downloadServiceIntent(accountKey))
    }

    internal fun cancelRecovery(accountKey: String) {
        workManager.cancelUniqueWork(workName(accountKey))
    }

    override fun cancelTrack(accountKey: String, trackId: String) {
        trackPartFile(accountKey, trackId).delete()
    }

    override fun cancelAccount(accountKey: String) {
        appContext.stopService(downloadServiceIntent(accountKey))
        workManager.cancelUniqueWork(workName(accountKey))
        workManager.cancelAllWorkByTag(accountTag(accountKey))
    }

    override fun deleteTrack(accountKey: String, relativePath: String?) {
        relativePath?.let { resolve(accountKey, it).delete() }
    }

    override fun deleteArtwork(accountKey: String, relativePath: String?) {
        relativePath?.let { resolve(accountKey, it).delete() }
    }

    override fun deleteAccount(accountKey: String) {
        cancelAccount(accountKey)
        accountDirectory(accountKey).deleteRecursively()
    }

    override fun fileUri(accountKey: String, relativePath: String): String =
        resolve(accountKey, relativePath).toURI().toString()

    override fun exists(accountKey: String, relativePath: String): Boolean =
        resolve(accountKey, relativePath).isFile

    override fun cleanupStaleParts(accountKey: String, activeTrackIds: Set<String>) {
        val cutoff = System.currentTimeMillis() - STALE_PART_AGE_MS
        val activeNames = activeTrackIds.mapTo(mutableSetOf()) { "${safeComponent(it)}.audio.part" }
        accountDirectory(accountKey).walkTopDown()
            .filter {
                it.isFile && it.name.endsWith(".part") && it.name !in activeNames &&
                    it.lastModified() < cutoff
            }
            .forEach(File::delete)
    }

    internal fun accountDirectory(accountKey: String) =
        File(appContext.filesDir, "offline/${safeComponent(accountKey)}")

    internal fun resolve(accountKey: String, relativePath: String) =
        File(accountDirectory(accountKey), relativePath)

    internal fun trackFile(accountKey: String, trackId: String) =
        resolve(accountKey, "tracks/${safeComponent(trackId)}.audio")

    internal fun trackPartFile(accountKey: String, trackId: String) =
        File(trackFile(accountKey, trackId).path + ".part")

    internal fun artworkFile(accountKey: String, coverArtId: String) =
        resolve(accountKey, "artwork/${safeComponent(coverArtId)}.image")

    internal fun relative(file: File, accountKey: String): String =
        file.relativeTo(accountDirectory(accountKey)).invariantSeparatorsPath

    private fun downloadServiceIntent(accountKey: String) =
        Intent(appContext, DownloadForegroundService::class.java)
            .putExtra(KEY_ACCOUNT, accountKey)

    companion object {
        const val KEY_ACCOUNT = "accountKey"
        fun workName(accountKey: String) = "downloads:$accountKey"
        fun accountTag(accountKey: String) = "downloads:$accountKey"
        private const val STALE_PART_AGE_MS = 24 * 60 * 60 * 1_000L
        private const val RECOVERY_DELAY_MINUTES = 1L

        private fun safeComponent(value: String): String = Base64.encodeToString(
            value.encodeToByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
    }
}
