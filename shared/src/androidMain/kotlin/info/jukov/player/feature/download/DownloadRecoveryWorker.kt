package info.jukov.player.feature.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DownloadRecoveryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val accountKey = inputData.getString(AndroidOfflinePlatform.KEY_ACCOUNT)
            ?: return Result.failure()
        return runCatching {
            AndroidOfflinePlatform(applicationContext).startService(accountKey)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }
}
