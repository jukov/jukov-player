package info.jukov.player.feature.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import info.jukov.player.core.data.cache.OfflineArtworkEntity
import info.jukov.player.core.data.cache.OfflineTrackEntity
import info.jukov.player.di.AndroidAppGraphOwner
import info.jukov.player.feature.auth.domain.AuthSession
import info.jukov.player.feature.auth.domain.AuthState
import info.jukov.player.feature.auth.domain.accountKey
import info.jukov.player.feature.download.domain.DownloadState
import info.jukov.player.feature.download.domain.DownloadErrorKind
import info.jukov.player.feature.download.domain.MAX_AUTOMATIC_DOWNLOAD_RETRIES
import info.jukov.player.feature.download.domain.downloadRetryDelayMs
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import jukovplayer.shared.generated.resources.Res
import jukovplayer.shared.generated.resources.cancel_all_downloads
import jukovplayer.shared.generated.resources.download_notification_channel
import jukovplayer.shared.generated.resources.download_notification_progress
import jukovplayer.shared.generated.resources.download_notification_track_progress
import jukovplayer.shared.generated.resources.downloads_completed_notification_text
import jukovplayer.shared.generated.resources.downloads_completed_notification_title
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.getString
import kotlin.time.Clock

class DownloadForegroundService : Service() {
    private val graph by lazy { (applicationContext as AndroidAppGraphOwner).graph }
    private val dao by lazy { graph.cacheDao }
    private val api by lazy { graph.subsonicApiClient }
    private val platform by lazy { AndroidOfflinePlatform(applicationContext) }
    private val transferBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private var accountKeyForActions: String? = null
    private val notificationMutex = Mutex()
    private var notificationState = NotificationState()

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val accountKey = intent?.getStringExtra(AndroidOfflinePlatform.KEY_ACCOUNT)
            ?: (graph.authRepository.authState.value as? AuthState.LoggedIn)?.session?.accountKey
        if (accountKey == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        accountKeyForActions = accountKey
        if (intent?.action == ACTION_CANCEL_ALL) {
            scope.launch { cancelAllPendingDownloads(accountKey, startId) }
            return START_NOT_STICKY
        }
        if (downloadJob?.isActive != true) {
            downloadJob = scope.launch { runBatch(accountKey) }
        } else if (intent?.action == ACTION_QUEUE_CHANGED) {
            scope.launch { refreshForeground(accountKey) }
        }
        return START_STICKY
    }

    private suspend fun cancelAllPendingDownloads(accountKey: String, startId: Int) {
        downloadJob?.cancelAndJoin()
        val pending = dao.pendingOfflineTracks(accountKey)
        pending.forEach { item ->
            platform.cancelTrack(accountKey, item.trackId)
            dao.deleteTrackOwnerships(accountKey, item.trackId)
            dao.deleteOfflineTrack(accountKey, item.trackId)
        }
        platform.cancelRecovery(accountKey)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runBatch(accountKey: String) {
        updateForeground(0, dao.pendingOfflineTrackCount(accountKey), null, null, start = true)
        val session = (graph.authRepository.authState.value as? AuthState.LoggedIn)?.session
            ?.takeIf { it.accountKey == accountKey }
        if (session == null) {
            failPending(accountKey, "Authentication required")
            stopSelf()
            return
        }

        var completedCount = 0
        val completedBefore = dao.completedOfflineTrackCount(accountKey)
        var batchSize = dao.pendingOfflineTrackCount(accountKey)
        var scheduledFutureRetry = false
        updateForeground(completedCount, batchSize, null, null)

        while (currentCoroutineContext().isActive) {
            Log.d(LOG_TAG, "batch: looking for next track; completed=$completedCount total=$batchSize")
            var item = dao.nextPendingOfflineTrack(accountKey, Clock.System.now().toEpochMilliseconds())
            if (item == null) {
                val nextRetryAt = dao.nextRetryAt(accountKey, Clock.System.now().toEpochMilliseconds())
                    ?: break
                platform.scheduleRecovery(
                    accountKey,
                    (nextRetryAt - Clock.System.now().toEpochMilliseconds()).coerceAtLeast(1),
                )
                scheduledFutureRetry = true
                break
            }
            val trackStartedAtMs = SystemClock.elapsedRealtime()
            trace(item.trackId, trackStartedAtMs, "selected from queue")
            val metadata = dao.track(accountKey, item.trackId)
            if (metadata == null) {
                markFailure(accountKey, item, "Track metadata is missing")
                completedCount++
                continue
            }
            val trackLabel = "${metadata.artist} – ${metadata.title}"

            batchSize = maxOf(
                batchSize,
                completedCount + dao.pendingOfflineTrackCount(accountKey),
            )
            downloadTrack(
                accountKey, session, item, trackLabel, completedCount, batchSize, trackStartedAtMs,
            )
            trace(item.trackId, trackStartedAtMs, "downloadTrack returned; advancing queue")
            completedCount++
        }
        if (!scheduledFutureRetry) {
            platform.cancelRecovery(accountKey)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        val successfulCount = (dao.completedOfflineTrackCount(accountKey) - completedBefore)
            .coerceAtLeast(0)
        if (successfulCount > 0 && successfulCount == completedCount) {
            postCompletionNotification(successfulCount)
        }
        stopSelf()
    }

    private suspend fun downloadTrack(
        accountKey: String,
        session: AuthSession,
        item: OfflineTrackEntity,
        trackLabel: String,
        completedCount: Int,
        batchSize: Int,
        trackStartedAtMs: Long,
    ): TrackResult {
        while (true) {
            try {
                val current = dao.offlineTrack(accountKey, item.trackId) ?: return TrackResult.Skipped
                trace(item.trackId, trackStartedAtMs, "attempt ${current.retryCount + 1} started")
                return downloadTrackAttempt(
                    accountKey, session, current, trackLabel, completedCount, batchSize,
                    trackStartedAtMs,
                )
            } catch (_: TrackRemovedException) {
                platform.trackPartFile(accountKey, item.trackId).delete()
                return TrackResult.Skipped
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: PermanentDownloadException) {
                val current = dao.offlineTrack(accountKey, item.trackId)
                    ?: return TrackResult.Skipped
                markFailure(accountKey, current, error.message.orEmpty(), error.kind)
                return TrackResult.Completed
            } catch (error: IOException) {
                val message = error.message ?: error::class.simpleName.orEmpty()
                val current = dao.offlineTrack(accountKey, item.trackId)
                    ?: return TrackResult.Skipped
                val retryCount = current.retryCount + 1
                trace(item.trackId, trackStartedAtMs, "network attempt $retryCount failed: $message")
                if (retryCount > MAX_AUTOMATIC_DOWNLOAD_RETRIES) {
                    markFailure(accountKey, current, message, DownloadErrorKind.Network)
                    return TrackResult.Completed
                }
                if (!queueForRetry(accountKey, current, message, retryCount)) {
                    return TrackResult.Skipped
                }
                trace(item.trackId, trackStartedAtMs, "queued for network-constrained retry")
                return TrackResult.Completed
            } catch (error: Throwable) {
                val current = dao.offlineTrack(accountKey, item.trackId)
                    ?: return TrackResult.Skipped
                markFailure(
                    accountKey, current,
                    error.message ?: error::class.simpleName.orEmpty(),
                    DownloadErrorKind.Unknown,
                )
                return TrackResult.Completed
            }
        }
    }

    private suspend fun downloadTrackAttempt(
        accountKey: String,
        session: AuthSession,
        item: OfflineTrackEntity,
        trackLabel: String,
        completedCount: Int,
        batchSize: Int,
        trackStartedAtMs: Long,
    ): TrackResult {
        updateForeground(completedCount, batchSize, trackLabel, 0f)
        val destination = platform.trackFile(accountKey, item.trackId).apply {
            parentFile?.mkdirs()
        }
        val part = platform.trackPartFile(accountKey, item.trackId)
        var downloaded: DownloadedFile
        while (true) {
            val offset = part.takeIf(File::isFile)?.length() ?: 0L
            updateState(
                accountKey, item, DownloadState.Downloading,
                offset, item.expectedSize, null, null,
            )

            trace(item.trackId, trackStartedAtMs, "audio request started; offset=$offset")
            downloaded = requestTrack(session, item.trackId, offset) { response ->
                trace(
                    item.trackId,
                    trackStartedAtMs,
                    "audio response received; status=${response.status.value} " +
                        "contentLength=${response.headers[HttpHeaders.ContentLength]}",
                )
                validateTrackResponse(response)
                val plan = prepareTransfer(part, offset, response)
                trace(
                    item.trackId, trackStartedAtMs,
                    "audio transfer started; initial=${plan.initialBytes} total=${plan.totalBytes}",
                )
                transferTrack(
                    accountKey, item, trackLabel, completedCount, batchSize, part, response, plan,
                )
            }
            trace(
                item.trackId, trackStartedAtMs,
                "audio transfer finished; bytes=${downloaded.bytes} total=${downloaded.total}",
            )
            val total = downloaded.total ?: break
            if (downloaded.bytes > total) {
                throw PermanentDownloadException("Downloaded file exceeds expected size")
            }
            if (downloaded.bytes == total) break
            if (downloaded.bytes <= offset) {
                throw NetworkDownloadException("Range response made no progress")
            }
            // The server capped this 206 response before the end of the file. Request the next
            // range immediately; this is normal continuation and does not consume a retry attempt.
        }

        trace(item.trackId, trackStartedAtMs, "file finalization started")
        finalizeTrack(accountKey, item, part, destination, downloaded, trackStartedAtMs)
        trace(item.trackId, trackStartedAtMs, "file finalization and track DB update finished")
        trace(item.trackId, trackStartedAtMs, "artwork stage started")
        downloadTrackArtwork(accountKey, item.trackId, session, trackStartedAtMs)
        trace(item.trackId, trackStartedAtMs, "artwork stage finished")
        return TrackResult.Completed
    }

    private suspend fun <T> requestTrack(
        session: AuthSession,
        trackId: String,
        offset: Long,
        block: suspend (HttpResponse) -> T,
    ): T = api.rawGet(
        endpoint = "download",
        session = session,
        parameters = mapOf("id" to trackId),
        headers = if (offset > 0) mapOf(HttpHeaders.Range to "bytes=$offset-") else emptyMap(),
        block = block,
    )

    private fun validateTrackResponse(response: HttpResponse) {
        if (response.status.value !in 200..299) {
            throw PermanentDownloadException(
                "HTTP ${response.status.value}", DownloadErrorKind.Http,
            )
        }
        if (response.headers[HttpHeaders.ContentType]?.contains("json", ignoreCase = true) == true) {
            throw PermanentDownloadException(
                "Server returned an API error", DownloadErrorKind.InvalidResponse,
            )
        }
    }

    private fun prepareTransfer(part: File, offset: Long, response: HttpResponse): TransferPlan {
        val append = offset > 0 && response.status == HttpStatusCode.PartialContent
        // 206 starts exactly at offset: retain .part and append. If Range was ignored, 200
        // contains the complete file, so the stale partial file is discarded before writing.
        if (!append) part.delete()
        val initialBytes = if (append) offset else 0L
        val responseBytes = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        val totalBytes = if (response.status == HttpStatusCode.PartialContent) {
            val contentRange = response.headers[HttpHeaders.ContentRange]
                ?: throw PermanentDownloadException("Partial response has no Content-Range")
            val parsedRange = parseContentRange(contentRange)
                ?: throw PermanentDownloadException("Partial response has invalid Content-Range")
            if (parsedRange.start != offset) {
                throw PermanentDownloadException("Partial response starts at an unexpected offset")
            }
            parsedRange.total
        } else {
            responseBytes
        }
        return TransferPlan(append, initialBytes, totalBytes)
    }

    private suspend fun transferTrack(
        accountKey: String,
        item: OfflineTrackEntity,
        trackLabel: String,
        completedCount: Int,
        batchSize: Int,
        part: File,
        response: HttpResponse,
        plan: TransferPlan,
    ): DownloadedFile {
        val channel = response.bodyAsChannel()
        var downloadedBytes = plan.initialBytes
        var lastProgressAtMs = 0L
        val output = try {
            FileOutputStream(part, plan.append)
        } catch (error: IOException) {
            throw PermanentDownloadException(
                error.message ?: "Cannot open local file", DownloadErrorKind.Local,
            )
        }
        output.use {
            while (!channel.isClosedForRead) {
                ensureTrackStillRequested(accountKey, item.trackId)
                val read = channel.readAvailable(transferBuffer)
                if (read <= 0) continue
                try {
                    output.write(transferBuffer, 0, read)
                } catch (error: IOException) {
                    throw PermanentDownloadException(
                        error.message ?: "Cannot write local file", DownloadErrorKind.Local,
                    )
                }
                downloadedBytes += read
                val nowMs = SystemClock.elapsedRealtime()
                if (nowMs - lastProgressAtMs >= PROGRESS_UPDATE_INTERVAL_MS ||
                    downloadedBytes == plan.totalBytes
                ) {
                    reportTrackProgress(
                        accountKey, item, trackLabel, completedCount, batchSize,
                        downloadedBytes, plan.totalBytes,
                    )
                    lastProgressAtMs = nowMs
                }
            }
        }
        reportTrackProgress(
            accountKey, item, trackLabel, completedCount, batchSize,
            downloadedBytes, plan.totalBytes,
        )
        return DownloadedFile(downloadedBytes, plan.totalBytes)
    }

    private suspend fun ensureTrackStillRequested(accountKey: String, trackId: String) {
        if (dao.offlineTrack(accountKey, trackId) == null) throw TrackRemovedException()
    }

    private suspend fun reportTrackProgress(
        accountKey: String,
        item: OfflineTrackEntity,
        trackLabel: String,
        completedCount: Int,
        batchSize: Int,
        downloadedBytes: Long,
        totalBytes: Long?,
    ) {
        updateState(
            accountKey, item, DownloadState.Downloading,
            downloadedBytes, totalBytes, null, null,
        )
        updateForeground(
            completedCount = completedCount,
            batchSize = batchSize,
            currentTitle = trackLabel,
            currentProgress = totalBytes?.takeIf { it > 0 }
                ?.let { downloadedBytes.toFloat() / it },
        )
    }

    private suspend fun finalizeTrack(
        accountKey: String,
        item: OfflineTrackEntity,
        part: File,
        destination: File,
        downloaded: DownloadedFile,
        trackStartedAtMs: Long,
    ) {
        destination.delete()
        if (!part.renameTo(destination)) {
            throw PermanentDownloadException("Cannot finalize file")
        }
        trace(item.trackId, trackStartedAtMs, "part file renamed; track DB update started")
        dao.updateOfflineTrackState(
            accountKey, item.trackId, DownloadState.Completed.name,
            downloaded.bytes, downloaded.total, platform.relative(destination, accountKey),
            null, Clock.System.now().toEpochMilliseconds(),
        )
    }

    private suspend fun downloadTrackArtwork(
        accountKey: String,
        trackId: String,
        session: AuthSession,
        trackStartedAtMs: Long,
    ) {
        if (dao.offlineTrack(accountKey, trackId) == null) {
            trace(trackId, trackStartedAtMs, "artwork skipped: track no longer requested")
            return
        }
        val coverArtId = dao.track(accountKey, trackId)?.coverArtId
        if (coverArtId == null) {
            trace(trackId, trackStartedAtMs, "artwork skipped: no coverArtId")
            return
        }
        trace(trackId, trackStartedAtMs, "artwork resolved; coverArtId=$coverArtId")
        downloadArtwork(accountKey, trackId, coverArtId, session, trackStartedAtMs)
    }

    private suspend fun downloadArtwork(
        accountKey: String,
        trackId: String,
        coverArtId: String,
        session: AuthSession,
        trackStartedAtMs: Long,
    ) {
        val existing = dao.offlineArtwork(accountKey, coverArtId)
        val existingFilePresent = existing?.relativePath?.let { platform.exists(accountKey, it) } == true
        trace(
            trackId, trackStartedAtMs,
            "artwork cache checked; state=${existing?.state} filePresent=$existingFilePresent",
        )
        if (existing?.state == DownloadState.Completed.name && existingFilePresent) {
            trace(trackId, trackStartedAtMs, "artwork reused from cache")
            return
        }

        trace(trackId, trackStartedAtMs, "artwork request started; coverArtId=$coverArtId")
        api.rawGet(
            "getCoverArt",
            session,
            mapOf("id" to coverArtId, "size" to ARTWORK_SIZE.toString()),
        ) { response ->
            trace(
                trackId, trackStartedAtMs,
                "artwork response received; status=${response.status.value} " +
                    "contentLength=${response.headers[HttpHeaders.ContentLength]}",
            )
            if (response.status.value !in 200..299) return@rawGet
            if (response.headers[HttpHeaders.ContentType]
                    ?.contains("json", ignoreCase = true) == true
            ) return@rawGet

            val file = platform.artworkFile(accountKey, coverArtId).apply { parentFile?.mkdirs() }
            val part = File(file.path + ".part")
            trace(trackId, trackStartedAtMs, "artwork transfer started")
            val bytes = transferResponse(response, part)
            trace(trackId, trackStartedAtMs, "artwork transfer finished; bytes=$bytes")
            persistArtworkIfReferenced(accountKey, coverArtId, response, part, file, bytes)
            trace(trackId, trackStartedAtMs, "artwork file and DB persistence finished")
        }
    }

    private suspend fun transferResponse(response: HttpResponse, destination: File): Long {
        val channel = response.bodyAsChannel()
        var bytes = 0L
        FileOutputStream(destination).use { output ->
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(transferBuffer)
                if (read <= 0) continue
                output.write(transferBuffer, 0, read)
                bytes += read
            }
        }
        return bytes
    }

    private suspend fun persistArtworkIfReferenced(
        accountKey: String,
        coverArtId: String,
        response: HttpResponse,
        part: File,
        file: File,
        bytes: Long,
    ) {
        if (dao.artworkReferenceCount(accountKey, coverArtId) == 0) {
            part.delete()
            file.delete()
            return
        }
        file.delete()
        if (!part.renameTo(file)) return
        dao.upsertOfflineArtwork(
            OfflineArtworkEntity(
                accountKey, coverArtId, platform.relative(file, accountKey),
                response.headers[HttpHeaders.ContentType], bytes,
                DownloadState.Completed.name, null, Clock.System.now().toEpochMilliseconds(),
            ),
        )
    }

    private suspend fun queueForRetry(
        accountKey: String,
        item: OfflineTrackEntity,
        message: String,
        retryCount: Int,
    ): Boolean {
        val current = dao.offlineTrack(accountKey, item.trackId) ?: return false
        val bytes = platform.trackPartFile(accountKey, item.trackId)
            .takeIf(File::exists)?.length() ?: 0
        updateState(
            accountKey, current, DownloadState.Queued,
            bytes, current.expectedSize, message, null, DownloadErrorKind.Network,
            retryCount, Clock.System.now().toEpochMilliseconds() + downloadRetryDelayMs(retryCount),
        )
        return true
    }

    private suspend fun markFailure(
        accountKey: String,
        item: OfflineTrackEntity,
        message: String,
        kind: DownloadErrorKind = DownloadErrorKind.Local,
    ) {
        val downloadedBytes = platform.trackPartFile(accountKey, item.trackId)
            .takeIf(File::exists)?.length() ?: item.downloadedBytes
        updateState(
            accountKey, item, DownloadState.Failed,
            downloadedBytes, item.expectedSize, message, null, kind, item.retryCount, null,
        )
    }

    private suspend fun updateState(
        accountKey: String,
        item: OfflineTrackEntity,
        state: DownloadState,
        downloadedBytes: Long,
        expectedSize: Long?,
        error: String?,
        completedAtMs: Long?,
        errorKind: DownloadErrorKind? = null,
        retryCount: Int = item.retryCount,
        nextRetryAtMs: Long? = null,
    ) {
        dao.updateOfflineTrackState(
            accountKey, item.trackId, state.name, downloadedBytes, expectedSize,
            item.relativePath, error, completedAtMs,
            errorKind?.name, retryCount, nextRetryAtMs,
        )
    }

    private suspend fun failPending(accountKey: String, message: String) {
        dao.failPendingOfflineTracks(accountKey, message)
    }

    private suspend fun updateForeground(
        completedCount: Int,
        batchSize: Int,
        currentTitle: String?,
        currentProgress: Float?,
        start: Boolean = false,
    ) {
        notificationMutex.withLock {
            notificationState = NotificationState(
                completedCount = completedCount,
                batchSize = maxOf(notificationState.batchSize, batchSize),
                currentTitle = currentTitle,
                currentProgress = currentProgress,
            )
            publishForeground(notificationState, start)
        }
    }

    private suspend fun refreshForeground(accountKey: String) {
        val pendingCount = dao.pendingOfflineTrackCount(accountKey)
        notificationMutex.withLock {
            notificationState = notificationState.copy(
                batchSize = maxOf(
                    notificationState.batchSize,
                    notificationState.completedCount + pendingCount,
                ),
            )
            publishForeground(notificationState, start = false)
        }
    }

    private suspend fun publishForeground(state: NotificationState, start: Boolean) {
        val info = foregroundInfo(
            state.completedCount,
            state.batchSize,
            state.currentTitle,
            state.currentProgress,
        )
        if (start) {
            startForeground(NOTIFICATION_ID, info.notification, info.foregroundServiceType)
        } else {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, info.notification)
        }
    }

    private suspend fun postCompletionNotification(successfulCount: Int) {
        val contentIntent = downloadsPendingIntent(COMPLETION_NOTIFICATION_ID)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(getString(Res.string.downloads_completed_notification_title))
            .setContentText(
                getString(Res.string.downloads_completed_notification_text, successfulCount),
            )
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(COMPLETION_NOTIFICATION_ID, notification)
    }

    private fun downloadsPendingIntent(requestCode: Int): PendingIntent? {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?.putExtra(EXTRA_OPEN_DOWNLOADS, true)
            ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return launchIntent?.let {
            PendingIntent.getActivity(
                this,
                requestCode,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }

    private fun cancelAllPendingIntent(): PendingIntent = PendingIntent.getService(
        this,
        CANCEL_REQUEST_CODE,
        Intent(this, DownloadForegroundService::class.java)
            .setAction(ACTION_CANCEL_ALL)
            .putExtra(AndroidOfflinePlatform.KEY_ACCOUNT, accountKeyForActions),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private suspend fun foregroundInfo(
        completedCount: Int,
        batchSize: Int,
        currentTitle: String?,
        currentProgress: Float?,
    ): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(Res.string.download_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val overall = if (batchSize > 0) {
            (completedCount + (currentProgress ?: 0f)) / batchSize
        } else 0f
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(
                getString(
                    Res.string.download_notification_progress,
                    completedCount + 1,
                    batchSize.coerceAtLeast(1),
                ),
            )
            .setContentText(
                currentTitle?.let { trackLabel ->
                    currentProgress?.let { progress ->
                        getString(
                            Res.string.download_notification_track_progress,
                            (progress * 100).toInt().coerceIn(0, 100),
                            trackLabel,
                        )
                    } ?: trackLabel
                },
            )
            .setContentIntent(downloadsPendingIntent(NOTIFICATION_ID))
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(Res.string.cancel_all_downloads),
                cancelAllPendingIntent(),
            )
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, (overall * 100).toInt().coerceIn(0, 100), false)
            .build()
        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private data class TransferPlan(
        val append: Boolean,
        val initialBytes: Long,
        val totalBytes: Long?,
    )

    private data class DownloadedFile(val bytes: Long, val total: Long?)

    private data class NotificationState(
        val completedCount: Int = 0,
        val batchSize: Int = 0,
        val currentTitle: String? = null,
        val currentProgress: Float? = null,
    )

    private enum class TrackResult { Completed, Skipped }
    private class TrackRemovedException : Exception()
    private class NetworkDownloadException(message: String) : Exception(message)
    private class PermanentDownloadException(
        message: String,
        val kind: DownloadErrorKind = DownloadErrorKind.Local,
    ) : Exception(message)

    companion object {
        const val LOG_TAG = "OfflineDownloadTrace"
        const val CHANNEL_ID = "offline_downloads"
        const val NOTIFICATION_ID = 20_260_807
        const val COMPLETION_NOTIFICATION_ID = 20_260_808
        const val CANCEL_REQUEST_CODE = 20_260_809
        const val EXTRA_OPEN_DOWNLOADS = "info.jukov.player.OPEN_DOWNLOADS"
        const val ACTION_QUEUE_CHANGED = "info.jukov.player.DOWNLOAD_QUEUE_CHANGED"
        const val ACTION_CANCEL_ALL = "info.jukov.player.CANCEL_ALL_DOWNLOADS"
        const val PROGRESS_UPDATE_INTERVAL_MS = 100L
        const val ARTWORK_SIZE = 1024
    }

    private fun trace(trackId: String, trackStartedAtMs: Long, message: String) {
        val elapsedMs = SystemClock.elapsedRealtime() - trackStartedAtMs
        Log.d(LOG_TAG, "track=$trackId +${elapsedMs}ms $message")
    }
}

internal data class ParsedContentRange(val start: Long, val endInclusive: Long, val total: Long)

internal fun parseContentRange(value: String): ParsedContentRange? {
    val unitAndRange = value.split(' ', limit = 2)
    if (unitAndRange.size != 2 || unitAndRange[0] != "bytes") return null
    val rangeAndTotal = unitAndRange[1].split('/', limit = 2)
    if (rangeAndTotal.size != 2 || rangeAndTotal[1] == "*") return null
    val bounds = rangeAndTotal[0].split('-', limit = 2)
    if (bounds.size != 2) return null
    val start = bounds[0].toLongOrNull() ?: return null
    val end = bounds[1].toLongOrNull() ?: return null
    val total = rangeAndTotal[1].toLongOrNull() ?: return null
    if (start < 0 || end < start || total <= end) return null
    return ParsedContentRange(start, end, total)
}
