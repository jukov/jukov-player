package info.jukov.player.feature.download

import info.jukov.player.core.data.cache.CacheDao
import info.jukov.player.core.data.cache.OfflineArtworkEntity
import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.feature.auth.domain.AuthState
import info.jukov.player.feature.auth.domain.accountKey
import info.jukov.player.feature.download.domain.DownloadState
import info.jukov.player.feature.download.domain.DownloadErrorKind
import info.jukov.player.feature.download.domain.MAX_AUTOMATIC_DOWNLOAD_RETRIES
import info.jukov.player.feature.download.domain.downloadRetryDelayMs
import info.jukov.player.feature.download.domain.OfflinePlatform
import info.jukov.player.feature.download.domain.OfflinePlatformFactory
import info.jukov.player.subsonic.data.SubsonicApiClient
import info.jukov.player.util.md5
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import platform.Foundation.*
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.NSObject
import kotlin.time.Clock
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class IosOfflinePlatformFactory : OfflinePlatformFactory {
    override fun create(
        authRepository: AuthRepository,
        dao: CacheDao,
        client: SubsonicApiClient,
        scope: CoroutineScope,
    ): OfflinePlatform = IosOfflinePlatform(authRepository, dao, client, scope)
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosOfflinePlatform internal constructor(
    private val authRepository: AuthRepository,
    private val dao: CacheDao,
    private val client: SubsonicApiClient,
    private val scope: CoroutineScope,
) : OfflinePlatform {
    private val fileManager = NSFileManager.defaultManager
    private val applicationSupportUrl: NSURL = requireNotNull(
        fileManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        ),
    ) { "Application Support directory is unavailable" }
    private val delegateQueue = NSOperationQueue().apply {
        maxConcurrentOperationCount = 1
        name = "info.jukov.player.background-downloads.delegate"
    }
    private val configuration = NSURLSessionConfiguration
        .backgroundSessionConfigurationWithIdentifier(BACKGROUND_SESSION_IDENTIFIER)
        .apply {
            waitsForConnectivity = true
            allowsCellularAccess = true
            HTTPMaximumConnectionsPerHost = 1
            sessionSendsLaunchEvents = true
        }
    private val sessionDelegate = IosDownloadSessionDelegate(this)
    private val session: NSURLSession by lazy {
        NSURLSession.sessionWithConfiguration(configuration, sessionDelegate, delegateQueue)
    }
    private val completedLocations = mutableSetOf<ULong>()
    private val operations = Channel<suspend () -> Unit>(capacity = Channel.UNLIMITED)
    private val generationLock = NSLock()
    private val progressLock = NSLock()
    private val progressCoalescer = IosProgressCoalescer()
    private val liveActivityProgress = IosLiveActivityProgressCoalescer()
    private var cancellationGeneration = 0L
    private val cancelledAccountTokens = MutableStateFlow<Set<String>>(emptySet())
    private val cancelledTaskDescriptions = MutableStateFlow<Set<String>>(emptySet())
    private val cancelledTaskIdentifiers = MutableStateFlow<Set<ULong>>(emptySet())
    private val backgroundCallbacks = IosBackgroundCallbackCoordinator()

    init {
        scope.launch {
            for (operation in operations) {
                try {
                    operation()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    // Individual persistence/network operations report their own state where possible.
                }
            }
        }
    }

    override fun enqueue(accountKey: String) {
        clearCompletionNotification()
        requestNotificationAuthorization()
        val generation = currentCancellationGeneration()
        submitOperation { schedulePending(accountKey, generation, allowCancelledRetry = true) }
    }

    override fun recover(accountKey: String) {
        val generation = currentCancellationGeneration()
        submitOperation { schedulePending(accountKey, generation, allowCancelledRetry = true) }
    }

    override suspend fun cancelTrack(accountKey: String, trackId: String) {
        val generation = advanceCancellationGeneration()
        val description = iosTaskDescription(TASK_TRACK, accountKey, trackId)
        cancelledTaskDescriptions.update { it + description }
        runOperationAndWait {
            cancelTasks { it == description }
            schedulePending(accountKey, generation, allowCancelledRetry = false)
            refreshProgressAfterCancellation(accountKey, setOf(trackId))
        }
        partUrl(accountKey, trackId).removeIfPresent()
    }

    override suspend fun cancelTracks(accountKey: String, trackIds: List<String>) {
        val generation = advanceCancellationGeneration()
        val descriptions = trackIds.mapTo(hashSetOf()) {
            iosTaskDescription(TASK_TRACK, accountKey, it)
        }
        cancelledTaskDescriptions.update { it + descriptions }
        runOperationAndWait {
            cancelTasks { it in descriptions }
            schedulePending(accountKey, generation, allowCancelledRetry = false)
            refreshProgressAfterCancellation(accountKey, trackIds.toSet())
        }
        trackIds.forEach { partUrl(accountKey, it).removeIfPresent() }
    }

    override suspend fun cancelAccount(accountKey: String) {
        advanceCancellationGeneration()
        val accountToken = safeComponent(accountKey)
        cancelledAccountTokens.update { it + accountToken }
        runOperationAndWait {
            cancelTasks { parseIosTaskDescription(it)?.accountToken == accountToken }
            endLiveActivity()
        }
    }

    override fun deleteTrack(accountKey: String, relativePath: String?) {
        relativePath?.let { resolveUrl(accountKey, it).removeIfPresent() }
    }

    override fun deleteTracks(accountKey: String, relativePaths: List<String>) {
        relativePaths.forEach { resolveUrl(accountKey, it).removeIfPresent() }
    }

    override fun deleteArtwork(accountKey: String, relativePath: String?) {
        relativePath?.let { resolveUrl(accountKey, it).removeIfPresent() }
    }

    override fun deleteArtworks(accountKey: String, relativePaths: List<String>) {
        relativePaths.forEach { resolveUrl(accountKey, it).removeIfPresent() }
    }

    override fun deleteAccount(accountKey: String) {
        accountUrl(accountKey).removeIfPresent()
    }

    override fun fileUri(accountKey: String, relativePath: String): String =
        requireNotNull(resolveUrl(accountKey, relativePath).absoluteString)

    override fun exists(accountKey: String, relativePath: String): Boolean =
        fileManager.fileExistsAtPath(requireNotNull(resolveUrl(accountKey, relativePath).path))

    override fun cleanupStaleParts(accountKey: String, activeTrackIds: Set<String>) {
        val tracksDirectory = directoryUrl(accountKey, TRACKS_DIRECTORY)
        val files = fileManager.contentsOfDirectoryAtURL(
            url = tracksDirectory,
            includingPropertiesForKeys = listOf(NSURLContentModificationDateKey),
            options = 0u,
            error = null,
        ).orEmpty().filterIsInstance<NSURL>()
        val activeNames = activeTrackIds.mapTo(hashSetOf()) { "${safeComponent(it)}.audio.part" }
        val cutoff = NSDate.dateWithTimeIntervalSinceNow(-STALE_PART_AGE_SECONDS)
        files.filter { url ->
            url.lastPathComponent?.endsWith(".part") == true &&
                url.lastPathComponent !in activeNames &&
                (url.resourceValuesForKeys(
                    keys = listOf(NSURLContentModificationDateKey),
                    error = null,
                )?.get(NSURLContentModificationDateKey) as? NSDate)
                    ?.compare(cutoff) == NSOrderedAscending
        }.forEach { it.removeIfPresent() }
    }

    fun handleEventsForBackgroundSession(
        identifier: String,
        completionHandler: () -> Unit,
        onRegistered: () -> Unit,
    ) {
        if (identifier != BACKGROUND_SESSION_IDENTIFIER) {
            completionHandler()
            return
        }
        delegateQueue.addOperationWithBlock {
            dispatchBackgroundCompletion(backgroundCallbacks.register(completionHandler))
            session.getAllTasksWithCompletionHandler { }
            onRegistered()
        }
    }

    internal fun didWriteData(
        downloadTask: NSURLSessionDownloadTask,
        totalBytesWritten: Long,
        totalBytesExpectedToWrite: Long,
    ) {
        val metadata = parseIosTaskDescription(downloadTask.taskDescription) ?: return
        if (metadata.kind != TASK_TRACK) {
            return
        }
        val taskIdentifier = downloadTask.taskIdentifier
        val shouldSchedule = withProgressLock {
            progressCoalescer.offer(
                IosDownloadProgress(
                    metadata = metadata,
                    taskIdentifier = taskIdentifier,
                    downloadedBytes = totalBytesWritten.coerceAtLeast(0),
                    expectedSize = totalBytesExpectedToWrite.takeIf { it > 0 },
                ),
            )
        }
        if (shouldSchedule) {
            submitProgressFlush(taskIdentifier)
        }
    }

    internal fun didFinishDownloading(
        downloadTask: NSURLSessionDownloadTask,
        location: NSURL,
    ) {
        val metadata = parseIosTaskDescription(downloadTask.taskDescription) ?: return
        discardPendingProgress(downloadTask.taskIdentifier)
        val durableLocation = stagingUrl(metadata, downloadTask.taskIdentifier)
        durableLocation.ensureParentDirectory()
        durableLocation.removeIfPresent()
        if (!fileManager.moveItemAtURL(location, durableLocation, error = null)) {
            beginCallbackProcessing()
            submitOperation {
                try {
                    markTaskFailed(
                        metadata,
                        "Cannot retain temporary download",
                        downloadTask.taskIdentifier,
                    )
                } finally {
                    endCallbackProcessing()
                }
            }
            return
        }
        completedLocations += downloadTask.taskIdentifier
        beginCallbackProcessing()
        submitOperation {
            try {
                processCompletedDownload(downloadTask, durableLocation, metadata)
            } finally {
                durableLocation.removeIfPresent()
                endCallbackProcessing()
            }
        }
    }

    internal fun didComplete(task: NSURLSessionTask, error: NSError?) {
        val metadata = parseIosTaskDescription(task.taskDescription) ?: return
        discardPendingProgress(task.taskIdentifier)
        if (error == null) {
            completedLocations.remove(task.taskIdentifier)
            return
        }
        if (completedLocations.remove(task.taskIdentifier)) {
            return
        }
        beginCallbackProcessing()
        submitOperation {
            try {
                markTaskFailed(
                    metadata, error.localizedDescription, task.taskIdentifier,
                    DownloadErrorKind.Network,
                )
            } finally {
                endCallbackProcessing()
            }
        }
    }

    internal fun didFinishBackgroundEvents() {
        beginCallbackProcessing()
        submitOperation { endCallbackProcessing() }
        dispatchBackgroundCompletion(backgroundCallbacks.finishEvents())
    }

    private suspend fun schedulePending(
        accountKey: String,
        generation: Long,
        allowCancelledRetry: Boolean,
    ) {
        if (!isCurrentDownloadGeneration(generation, currentCancellationGeneration())) {
            return
        }
        cleanupStaleStaging()
        val sessionState = authRepository.authState.value as? AuthState.LoggedIn
        if (sessionState?.session?.accountKey != accountKey) {
            dao.failPendingOfflineTracks(accountKey, "Authentication required")
            return
        }
        if (!sessionState.session.serverUrl.startsWith("https://", ignoreCase = true)) {
            dao.failPendingOfflineTracks(
                accountKey, "HTTPS is required on iOS", DownloadErrorKind.Local.name,
            )
            return
        }
        val tasks = currentTasks()
        val accountToken = safeComponent(accountKey)
        if (accountToken in cancelledAccountTokens.value) {
            tasks.filter {
                parseIosTaskDescription(it.taskDescription)?.accountToken == accountToken
            }.forEach(NSURLSessionTask::cancel)
            if (!allowCancelledRetry) {
                return
            }
            cancelledAccountTokens.update { it - accountToken }
        }
        val active = tasks.filter {
            it.state != NSURLSessionTaskStateCanceling &&
                it.state != NSURLSessionTaskStateCompleted
        }.mapNotNullTo(hashSetOf()) { it.taskDescription }
        dao.pendingOfflineTracks(accountKey).forEach { item ->
            val now = Clock.System.now().toEpochMilliseconds()
            val retryAt = item.nextRetryAtMs
            if (retryAt != null && retryAt > now) {
                scope.launch {
                    delay(retryAt - now)
                    submitOperation {
                        schedulePending(accountKey, generation, allowCancelledRetry)
                    }
                }
                return@forEach
            }
            val description = iosTaskDescription(TASK_TRACK, accountKey, item.trackId)
            if (description !in active) {
                if (description in cancelledTaskDescriptions.value && !allowCancelledRetry) {
                    return@forEach
                }
                if (allowCancelledRetry) {
                    cancelledTaskDescriptions.update { it - description }
                }
                val currentSession = (authRepository.authState.value as? AuthState.LoggedIn)?.session
                if (currentSession?.accountKey != accountKey ||
                    !isCurrentDownloadGeneration(generation, currentCancellationGeneration()) ||
                    accountToken in cancelledAccountTokens.value ||
                    description in cancelledTaskDescriptions.value
                ) {
                    return@forEach
                }
                val url = NSURL.URLWithString(
                    client.buildUrl(
                        "download",
                        currentSession,
                        mapOf("id" to item.trackId, "f" to "json"),
                    ),
                ) ?: return@forEach
                val task = session.downloadTaskWithURL(url)
                task.taskDescription = description
                dao.updateOfflineTrackState(
                    accountKey, item.trackId, DownloadState.Downloading.name,
                    0, item.expectedSize, item.relativePath, null, null,
                    null, item.retryCount, null,
                )
                if (currentAccountKey() != accountKey ||
                    !isCurrentDownloadGeneration(generation, currentCancellationGeneration()) ||
                    accountToken in cancelledAccountTokens.value ||
                    description in cancelledTaskDescriptions.value
                ) {
                    task.cancel()
                    return@forEach
                }
                task.resume()
                postLiveActivityProgress(
                    progress = null,
                    pendingCount = dao.pendingOfflineTrackCount(accountKey),
                )
            }
        }
        dao.allOfflineTracks(accountKey)
            .filter { it.state == DownloadState.Completed.name }
            .forEach { scheduleArtwork(accountKey, it.trackId, generation) }
        if (dao.pendingOfflineTrackCount(accountKey) == 0) {
            endLiveActivity()
        }
    }

    private suspend fun processCompletedDownload(
        task: NSURLSessionDownloadTask,
        temporaryUrl: NSURL,
        metadata: IosDownloadTaskMetadata,
    ) {
        val accountKey = accountKeyFor(metadata) ?: return
        if (!isTaskActive(metadata, task.taskIdentifier)) {
            return
        }
        val response = task.response as? NSHTTPURLResponse
        val statusCode = response?.statusCode ?: 0
        val contentType = response?.allHeaderFields?.entries
            ?.firstOrNull { it.key.toString().equals("Content-Type", ignoreCase = true) }
            ?.value?.toString()
        if (statusCode !in 200..299 || isApiErrorContentType(contentType)) {
            markTaskFailed(
                metadata, "HTTP $statusCode", task.taskIdentifier, DownloadErrorKind.Http,
            )
            return
        }
        when (metadata.kind) {
            TASK_TRACK -> completeTrack(accountKey, task, temporaryUrl, metadata.id)
            TASK_ARTWORK -> completeArtwork(accountKey, task, temporaryUrl, metadata.id, contentType)
        }
    }

    private suspend fun completeTrack(
        accountKey: String,
        task: NSURLSessionDownloadTask,
        temporaryUrl: NSURL,
        trackId: String,
    ) {
        val item = dao.offlineTrack(accountKey, trackId) ?: return
        val metadata = IosDownloadTaskMetadata(
            TASK_TRACK,
            safeComponent(accountKey),
            trackId,
        )
        if (!isTaskActive(metadata, task.taskIdentifier)) {
            return
        }
        val destination = trackUrl(accountKey, trackId)
        destination.ensureParentDirectory()
        destination.removeIfPresent()
        val bytes = task.countOfBytesReceived.coerceAtLeast(0)
        val result = finalizeIosDownload(
            isActive = { isTaskActive(metadata, task.taskIdentifier) },
            moveToDestination = {
                fileManager.moveItemAtURL(temporaryUrl, destination, error = null)
            },
            commit = {
                dao.updateOfflineTrackState(
                    accountKey, trackId, DownloadState.Completed.name, bytes,
                    task.countOfBytesExpectedToReceive.takeIf { it > 0 },
                    relative(destination, accountKey), null,
                    Clock.System.now().toEpochMilliseconds(),
                )
                true
            },
            removeDestination = { destination.removeIfPresent() },
        )
        if (result == IosDownloadFinalization.MoveFailed) {
            markTaskFailed(metadata, "Cannot finalize file", task.taskIdentifier)
        }
        if (result != IosDownloadFinalization.Completed) {
            return
        }
        val generation = currentCancellationGeneration()
        scheduleArtwork(accountKey, trackId, generation)
        if (dao.pendingOfflineTrackCount(accountKey) == 0) {
            postCompletionNotification(dao.completedOfflineTrackCount(accountKey))
        }
    }

    private suspend fun scheduleArtwork(accountKey: String, trackId: String, generation: Long) {
        if (!isCurrentDownloadGeneration(generation, currentCancellationGeneration())) {
            return
        }
        val loggedIn = authRepository.authState.value as? AuthState.LoggedIn ?: return
        if (loggedIn.session.accountKey != accountKey) {
            return
        }
        val coverArtId = dao.track(accountKey, trackId)?.coverArtId ?: return
        val existing = dao.offlineArtwork(accountKey, coverArtId)
        if (existing?.state == DownloadState.Completed.name &&
            existing.relativePath?.let { exists(accountKey, it) } == true
        ) {
            return
        }
        val description = iosTaskDescription(TASK_ARTWORK, accountKey, coverArtId)
        if (description in currentTaskDescriptions()) {
            return
        }
        cancelledTaskDescriptions.update { it - description }
        val currentSession = (authRepository.authState.value as? AuthState.LoggedIn)?.session
        if (currentSession?.accountKey != accountKey ||
            !isCurrentDownloadGeneration(generation, currentCancellationGeneration()) ||
            safeComponent(accountKey) in cancelledAccountTokens.value ||
            description in cancelledTaskDescriptions.value
        ) {
            return
        }
        val schedulingEntity =
            OfflineArtworkEntity(
                accountKey, coverArtId, existing?.relativePath, existing?.contentType,
                existing?.downloadedBytes ?: 0, DownloadState.Downloading.name, null, null,
            )
        if (!dao.upsertOfflineArtworkIfReferenced(schedulingEntity)) {
            return
        }
        val url = NSURL.URLWithString(
            client.buildUrl(
                "getCoverArt",
                currentSession,
                mapOf(
                    "id" to coverArtId,
                    "size" to ARTWORK_SIZE.toString(),
                    "f" to "json",
                ),
            ),
        ) ?: return
        val task = session.downloadTaskWithURL(url)
        task.taskDescription = description
        if (currentAccountKey() != accountKey ||
            !isCurrentDownloadGeneration(generation, currentCancellationGeneration()) ||
            safeComponent(accountKey) in cancelledAccountTokens.value ||
            description in cancelledTaskDescriptions.value
        ) {
            task.cancel()
            val restored = existing?.let { dao.upsertOfflineArtworkIfReferenced(it) } == true
            if (!restored) {
                dao.deleteOfflineArtwork(accountKey, coverArtId)
            }
            return
        }
        task.resume()
    }

    private suspend fun completeArtwork(
        accountKey: String,
        task: NSURLSessionDownloadTask,
        temporaryUrl: NSURL,
        coverArtId: String,
        contentType: String?,
    ) {
        if (dao.artworkReferenceCount(accountKey, coverArtId) == 0) {
            temporaryUrl.removeIfPresent()
            dao.deleteOfflineArtwork(accountKey, coverArtId)
            return
        }
        val metadata = IosDownloadTaskMetadata(
            TASK_ARTWORK,
            safeComponent(accountKey),
            coverArtId,
        )
        if (!isTaskActive(metadata, task.taskIdentifier)) {
            return
        }
        val destination = artworkUrl(accountKey, coverArtId)
        destination.ensureParentDirectory()
        destination.removeIfPresent()
        val result = finalizeIosDownload(
            isActive = { isTaskActive(metadata, task.taskIdentifier) },
            moveToDestination = {
                fileManager.moveItemAtURL(temporaryUrl, destination, error = null)
            },
            commit = {
                dao.upsertOfflineArtworkIfReferenced(
                    OfflineArtworkEntity(
                        accountKey, coverArtId, relative(destination, accountKey), contentType,
                        task.countOfBytesReceived.coerceAtLeast(0), DownloadState.Completed.name,
                        null, Clock.System.now().toEpochMilliseconds(),
                    ),
                )
            },
            removeDestination = { destination.removeIfPresent() },
        )
        if (result == IosDownloadFinalization.MoveFailed) {
            markTaskFailed(metadata, "Cannot finalize artwork", task.taskIdentifier)
        }
    }

    private suspend fun markTaskFailed(
        metadata: IosDownloadTaskMetadata,
        message: String,
        taskIdentifier: ULong,
        kind: DownloadErrorKind = DownloadErrorKind.Local,
    ) {
        if (!isTaskActive(metadata, taskIdentifier)) {
            return
        }
        val accountKey = accountKeyFor(metadata) ?: return
        when (metadata.kind) {
            TASK_TRACK -> {
                val item = dao.offlineTrack(accountKey, metadata.id) ?: return
                val retryCount = if (kind == DownloadErrorKind.Network) {
                    item.retryCount + 1
                } else {
                    item.retryCount
                }
                val retryable = kind == DownloadErrorKind.Network &&
                    retryCount <= MAX_AUTOMATIC_DOWNLOAD_RETRIES
                val retryAt = if (retryable) {
                    Clock.System.now().toEpochMilliseconds() + downloadRetryDelayMs(retryCount)
                } else {
                    null
                }
                dao.updateOfflineTrackState(
                    accountKey, metadata.id,
                    if (retryable) DownloadState.Queued.name else DownloadState.Failed.name,
                    item.downloadedBytes, item.expectedSize, item.relativePath, message, null,
                    kind.name, retryCount, retryAt,
                )
                if (retryable) {
                    val generation = currentCancellationGeneration()
                    scope.launch {
                        delay(downloadRetryDelayMs(retryCount))
                        submitOperation {
                            schedulePending(accountKey, generation, allowCancelledRetry = true)
                        }
                    }
                } else if (dao.pendingOfflineTrackCount(accountKey) == 0) {
                    endLiveActivity()
                }
            }
            TASK_ARTWORK -> {
                val artwork = dao.offlineArtwork(accountKey, metadata.id) ?: return
                val retained = dao.upsertOfflineArtworkIfReferenced(
                    artwork.copy(state = DownloadState.Failed.name, error = message),
                )
                if (!retained) {
                    artwork.relativePath?.let { resolveUrl(accountKey, it).removeIfPresent() }
                    dao.deleteOfflineArtwork(accountKey, metadata.id)
                }
            }
        }
    }

    private suspend fun cancelTasks(predicate: (String?) -> Boolean) {
        val matchingTasks = currentTasks().filter { predicate(it.taskDescription) }
        cancelledTaskIdentifiers.update { identifiers ->
            identifiers + matchingTasks.map { it.taskIdentifier }
        }
        matchingTasks.forEach(NSURLSessionTask::cancel)
    }

    private fun submitOperation(operation: suspend () -> Unit) {
        check(operations.trySend(operation).isSuccess) { "Download operation queue is unavailable" }
    }

    private suspend fun flushProgress(taskIdentifier: ULong) {
        val progress = withProgressLock { progressCoalescer.take(taskIdentifier) }
        if (progress != null) {
            val accountKey = accountKeyFor(progress.metadata)
            if (accountKey != null && isTaskActive(progress.metadata, progress.taskIdentifier)) {
                dao.updateOfflineTrackProgress(
                    accountKey = accountKey,
                    trackId = progress.metadata.id,
                    downloadedBytes = progress.downloadedBytes,
                    expectedSize = progress.expectedSize,
                )
                postLiveActivityProgress(
                    progress = progress.expectedSize?.let { expectedSize ->
                        if (expectedSize > 0) {
                            (progress.downloadedBytes * 100 / expectedSize).toInt().coerceIn(0, 100)
                        } else {
                            null
                        }
                    },
                    pendingCount = dao.pendingOfflineTrackCount(accountKey),
                )
            }
        }
        val shouldReschedule = withProgressLock { progressCoalescer.completeFlush(taskIdentifier) }
        if (shouldReschedule) {
            submitProgressFlush(taskIdentifier)
        }
    }

    private fun submitProgressFlush(taskIdentifier: ULong) {
        beginCallbackProcessing()
        submitOperation {
            try {
                flushProgress(taskIdentifier)
            } finally {
                endCallbackProcessing()
            }
        }
    }

    private fun discardPendingProgress(taskIdentifier: ULong) {
        withProgressLock {
            progressCoalescer.discard(taskIdentifier)
        }
    }

    private fun <T> withProgressLock(block: () -> T): T {
        progressLock.lock()
        return try {
            block()
        } finally {
            progressLock.unlock()
        }
    }

    private suspend fun runOperationAndWait(operation: suspend () -> Unit) {
        val completion = CompletableDeferred<Unit>()
        operations.send {
            try {
                operation()
                completion.complete(Unit)
            } catch (error: Throwable) {
                completion.completeExceptionally(error)
            }
        }
        withContext(NonCancellable) { completion.await() }
    }

    private fun currentCancellationGeneration(): Long {
        generationLock.lock()
        return try {
            cancellationGeneration
        } finally {
            generationLock.unlock()
        }
    }

    private fun advanceCancellationGeneration(): Long {
        generationLock.lock()
        return try {
            cancellationGeneration++
            cancellationGeneration
        } finally {
            generationLock.unlock()
        }
    }

    private fun currentAccountKey(): String? =
        (authRepository.authState.value as? AuthState.LoggedIn)?.session?.accountKey

    private fun accountKeyFor(metadata: IosDownloadTaskMetadata): String? = currentAccountKey()
        ?.takeIf {
            safeComponent(it) == metadata.accountToken &&
                metadata.accountToken !in cancelledAccountTokens.value
        }

    private fun isTaskActive(
        metadata: IosDownloadTaskMetadata,
        taskIdentifier: ULong,
    ): Boolean =
        isActiveDownloadAttempt(taskIdentifier, cancelledTaskIdentifiers.value) &&
        metadata.accountToken !in cancelledAccountTokens.value &&
            metadata.description !in cancelledTaskDescriptions.value

    private fun accountUrl(accountKey: String): NSURL = applicationSupportUrl
        .URLByAppendingPathComponent(OFFLINE_DIRECTORY, isDirectory = true)!!
        .URLByAppendingPathComponent(safeComponent(accountKey), isDirectory = true)!!

    private fun directoryUrl(accountKey: String, directory: String): NSURL =
        accountUrl(accountKey).URLByAppendingPathComponent(directory, isDirectory = true)!!

    private fun resolveUrl(accountKey: String, relativePath: String): NSURL {
        require(isSafeRelativePath(relativePath)) { "Unsafe offline path" }
        return accountUrl(accountKey).URLByAppendingPathComponent(relativePath)!!
    }

    private fun trackUrl(accountKey: String, trackId: String): NSURL = directoryUrl(
        accountKey, TRACKS_DIRECTORY,
    ).URLByAppendingPathComponent("${safeComponent(trackId)}.audio")!!

    private fun partUrl(accountKey: String, trackId: String): NSURL = directoryUrl(
        accountKey, TRACKS_DIRECTORY,
    ).URLByAppendingPathComponent("${safeComponent(trackId)}.audio.part")!!

    private fun artworkUrl(accountKey: String, coverArtId: String): NSURL = directoryUrl(
        accountKey, ARTWORK_DIRECTORY,
    ).URLByAppendingPathComponent("${safeComponent(coverArtId)}.image")!!

    private fun stagingUrl(metadata: IosDownloadTaskMetadata, taskIdentifier: ULong): NSURL =
        applicationSupportUrl
            .URLByAppendingPathComponent(STAGING_DIRECTORY, isDirectory = true)!!
            .URLByAppendingPathComponent(
                "${safeComponent(metadata.description)}-$taskIdentifier.download",
            )!!

    private fun cleanupStaleStaging() {
        val stagingDirectory = applicationSupportUrl.URLByAppendingPathComponent(
            STAGING_DIRECTORY,
            isDirectory = true,
        ) ?: return
        val cutoff = NSDate.dateWithTimeIntervalSinceNow(-STALE_PART_AGE_SECONDS)
        fileManager.contentsOfDirectoryAtURL(
            url = stagingDirectory,
            includingPropertiesForKeys = listOf(NSURLContentModificationDateKey),
            options = 0u,
            error = null,
        ).orEmpty().filterIsInstance<NSURL>().filter { url ->
            (url.resourceValuesForKeys(
                keys = listOf(NSURLContentModificationDateKey),
                error = null,
            )?.get(NSURLContentModificationDateKey) as? NSDate)
                ?.compare(cutoff) == NSOrderedAscending
        }.forEach { it.removeIfPresent() }
    }

    private fun relative(url: NSURL, accountKey: String): String {
        val root = requireNotNull(accountUrl(accountKey).path).trimEnd('/')
        val path = requireNotNull(url.path)
        return path.removePrefix("$root/")
    }

    private fun NSURL.ensureParentDirectory() {
        val parent = URLByDeletingLastPathComponent ?: return
        fileManager.createDirectoryAtURL(
            url = parent,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        parent.setResourceValue(
            value = true,
            forKey = NSURLIsExcludedFromBackupKey,
            error = null,
        )
    }

    private fun NSURL.removeIfPresent() {
        path?.takeIf(fileManager::fileExistsAtPath)?.let { fileManager.removeItemAtPath(it, null) }
    }

    private fun beginCallbackProcessing() {
        backgroundCallbacks.beginProcessing()
    }

    private fun endCallbackProcessing() {
        delegateQueue.addOperationWithBlock {
            dispatchBackgroundCompletion(backgroundCallbacks.endProcessing())
        }
    }

    private fun dispatchBackgroundCompletion(handler: (() -> Unit)?) {
        if (handler != null) {
            NSOperationQueue.mainQueue.addOperationWithBlock(handler)
        }
    }

    private fun requestNotificationAuthorization() {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
        ) { _, _ -> }
    }

    private fun postCompletionNotification(completedCount: Int) {
        endLiveActivity()
        val content = UNMutableNotificationContent().apply {
            setTitle("Downloads completed")
            setBody("$completedCount tracks are available offline")
            setSound(platform.UserNotifications.UNNotificationSound.defaultSound())
            setUserInfo(mapOf(NOTIFICATION_OPEN_DOWNLOADS_KEY to true))
        }
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = COMPLETION_NOTIFICATION_IDENTIFIER,
            content = content,
            trigger = null,
        )
        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request) { }
    }

    private fun postLiveActivityProgress(progress: Int?, pendingCount: Int) {
        clearCompletionNotification()
        val state = IosLiveActivityProgress(progress, pendingCount)
        if (!liveActivityProgress.shouldPublish(state)) {
            return
        }
        val userInfo = mutableMapOf<Any?, Any?>(
            LIVE_ACTIVITY_PENDING_COUNT_KEY to pendingCount.coerceAtLeast(1),
        ).apply {
            progress?.let { put(LIVE_ACTIVITY_PERCENT_KEY, it.coerceIn(0, 100)) }
        }
        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = LIVE_ACTIVITY_UPDATE_NOTIFICATION,
            `object` = null,
            userInfo = userInfo,
        )
    }

    private fun endLiveActivity() {
        liveActivityProgress.reset()
        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = LIVE_ACTIVITY_END_NOTIFICATION,
            `object` = null,
        )
    }

    private fun clearCompletionNotification() {
        val identifiers = iosNotificationsClearedOnDownloadStart().toList()
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removePendingNotificationRequestsWithIdentifiers(identifiers)
        center.removeDeliveredNotificationsWithIdentifiers(identifiers)
    }

    private suspend fun refreshProgressAfterCancellation(
        accountKey: String,
        cancelledTrackIds: Set<String>,
    ) {
        val pendingCount = remainingIosDownloadCount(
            pendingTrackIds = dao.pendingOfflineTracks(accountKey).mapTo(hashSetOf()) { it.trackId },
            cancelledTrackIds = cancelledTrackIds,
        )
        if (pendingCount == 0) {
            endLiveActivity()
        } else {
            postLiveActivityProgress(progress = null, pendingCount = pendingCount)
        }
    }

    private suspend fun currentTasks(): List<NSURLSessionTask> = suspendCoroutine { continuation ->
        session.getAllTasksWithCompletionHandler { tasks ->
            continuation.resume(
                tasks.orEmpty().filterIsInstance<NSURLSessionTask>(),
            )
        }
    }

    private suspend fun currentTaskDescriptions(): Set<String> = currentTasks().filter {
        it.state != NSURLSessionTaskStateCanceling &&
            it.state != NSURLSessionTaskStateCompleted
    }
        .mapNotNullTo(hashSetOf()) { it.taskDescription }

}

@OptIn(ExperimentalForeignApi::class)
private class IosDownloadSessionDelegate(
    private val owner: IosOfflinePlatform,
) : NSObject(), NSURLSessionDownloadDelegateProtocol {
    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didWriteData: Long,
        totalBytesWritten: Long,
        totalBytesExpectedToWrite: Long,
    ) {
        owner.didWriteData(downloadTask, totalBytesWritten, totalBytesExpectedToWrite)
    }

    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didFinishDownloadingToURL: NSURL,
    ) {
        owner.didFinishDownloading(downloadTask, didFinishDownloadingToURL)
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didCompleteWithError: NSError?,
    ) {
        owner.didComplete(task, didCompleteWithError)
    }

    override fun URLSessionDidFinishEventsForBackgroundURLSession(session: NSURLSession) {
        owner.didFinishBackgroundEvents()
    }
}

internal fun safeComponent(value: String): String = md5(value)

internal data class IosDownloadTaskMetadata(
    val kind: String,
    val accountToken: String,
    val id: String,
) {
    val description: String get() = "$kind$TASK_SEPARATOR$accountToken$TASK_SEPARATOR$id"
}

internal data class IosDownloadProgress(
    val metadata: IosDownloadTaskMetadata,
    val taskIdentifier: ULong,
    val downloadedBytes: Long,
    val expectedSize: Long?,
)

internal class IosProgressCoalescer {
    private val pending = mutableMapOf<ULong, IosDownloadProgress>()
    private val scheduled = mutableSetOf<ULong>()

    fun offer(progress: IosDownloadProgress): Boolean {
        pending[progress.taskIdentifier] = progress
        return scheduled.add(progress.taskIdentifier)
    }

    fun take(taskIdentifier: ULong): IosDownloadProgress? = pending.remove(taskIdentifier)

    fun completeFlush(taskIdentifier: ULong): Boolean {
        if (taskIdentifier in pending) {
            return true
        }
        scheduled.remove(taskIdentifier)
        return false
    }

    fun discard(taskIdentifier: ULong) {
        pending.remove(taskIdentifier)
        scheduled.remove(taskIdentifier)
    }
}

internal data class IosLiveActivityProgress(
    val percent: Int?,
    val pendingCount: Int,
)

internal class IosLiveActivityProgressCoalescer {
    private var lastPublished: IosLiveActivityProgress? = null

    fun shouldPublish(progress: IosLiveActivityProgress): Boolean {
        val normalized = progress.copy(percent = progress.percent?.coerceIn(0, 100))
        if (normalized == lastPublished) {
            return false
        }
        lastPublished = normalized
        return true
    }

    fun reset() {
        lastPublished = null
    }
}

internal fun remainingIosDownloadCount(
    pendingTrackIds: Set<String>,
    cancelledTrackIds: Set<String>,
): Int = pendingTrackIds.count { it !in cancelledTrackIds }

internal fun iosNotificationsClearedOnDownloadStart(): Set<String> =
    setOf(COMPLETION_NOTIFICATION_IDENTIFIER)

internal class IosBackgroundCallbackCoordinator {
    private val lock = NSLock()
    private var completionHandler: (() -> Unit)? = null
    private var eventsFinished = false
    private var processingCallbacks = 0

    fun register(handler: () -> Unit): (() -> Unit)? {
        return withLock {
            completionHandler = handler
            takeCompletionIfReady()
        }
    }

    fun beginProcessing() {
        withLock { processingCallbacks++ }
    }

    fun endProcessing(): (() -> Unit)? {
        return withLock {
            check(processingCallbacks > 0) { "No background callback is being processed" }
            processingCallbacks--
            takeCompletionIfReady()
        }
    }

    fun finishEvents(): (() -> Unit)? {
        return withLock {
            eventsFinished = true
            takeCompletionIfReady()
        }
    }

    private fun <T> withLock(block: () -> T): T {
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }

    private fun takeCompletionIfReady(): (() -> Unit)? {
        if (!eventsFinished || processingCallbacks != 0) {
            return null
        }
        val handler = completionHandler ?: return null
        completionHandler = null
        eventsFinished = false
        return handler
    }
}

internal enum class IosDownloadFinalization { Completed, Cancelled, MoveFailed, CommitRejected }

internal suspend fun finalizeIosDownload(
    isActive: () -> Boolean,
    moveToDestination: () -> Boolean,
    commit: suspend () -> Boolean,
    removeDestination: () -> Unit,
): IosDownloadFinalization {
    if (!isActive()) {
        return IosDownloadFinalization.Cancelled
    }
    if (!moveToDestination()) {
        return IosDownloadFinalization.MoveFailed
    }
    if (!isActive()) {
        removeDestination()
        return IosDownloadFinalization.Cancelled
    }
    if (!commit()) {
        removeDestination()
        return IosDownloadFinalization.CommitRejected
    }
    if (!isActive()) {
        removeDestination()
        return IosDownloadFinalization.Cancelled
    }
    return IosDownloadFinalization.Completed
}

internal fun iosTaskDescription(kind: String, accountKey: String, id: String): String =
    "$kind$TASK_SEPARATOR${safeComponent(accountKey)}$TASK_SEPARATOR$id"

internal fun parseIosTaskDescription(description: String?): IosDownloadTaskMetadata? {
    val value = description ?: return null
    val firstSeparator = value.indexOf(TASK_SEPARATOR)
    val secondSeparator = value.indexOf(TASK_SEPARATOR, firstSeparator + 1)
    if (firstSeparator <= 0 || secondSeparator <= firstSeparator + 1 ||
        secondSeparator == value.lastIndex
    ) {
        return null
    }
    val kind = value.substring(0, firstSeparator)
    if (kind != TASK_TRACK && kind != TASK_ARTWORK) {
        return null
    }
    return IosDownloadTaskMetadata(
        kind = kind,
        accountToken = value.substring(firstSeparator + 1, secondSeparator),
        id = value.substring(secondSeparator + 1),
    )
}

internal fun isSafeRelativePath(value: String): Boolean =
    value.isNotBlank() &&
        !value.startsWith('/') &&
        !value.startsWith('\\') &&
        value.split('/', '\\').all { it.isNotBlank() && it != "." && it != ".." }

internal fun isCurrentDownloadGeneration(submitted: Long, current: Long): Boolean =
    submitted == current

internal fun isActiveDownloadAttempt(
    taskIdentifier: ULong,
    cancelledTaskIdentifiers: Set<ULong>,
): Boolean = taskIdentifier !in cancelledTaskIdentifiers

internal fun isApiErrorContentType(contentType: String?): Boolean =
    contentType?.let { value ->
        value.contains("json", ignoreCase = true) || value.contains("xml", ignoreCase = true)
    } == true

private const val BACKGROUND_SESSION_IDENTIFIER = "info.jukov.player.offline-downloads"
private const val NOTIFICATION_OPEN_DOWNLOADS_KEY = "openDownloads"
private const val LIVE_ACTIVITY_UPDATE_NOTIFICATION = "info.jukov.player.downloadActivity.update"
private const val LIVE_ACTIVITY_END_NOTIFICATION = "info.jukov.player.downloadActivity.end"
private const val LIVE_ACTIVITY_PERCENT_KEY = "percent"
private const val LIVE_ACTIVITY_PENDING_COUNT_KEY = "pendingCount"
internal const val COMPLETION_NOTIFICATION_IDENTIFIER = "offline-downloads-completed"
private const val OFFLINE_DIRECTORY = "offline"
private const val TRACKS_DIRECTORY = "tracks"
private const val ARTWORK_DIRECTORY = "artwork"
private const val STAGING_DIRECTORY = "offline-staging"
private const val TASK_TRACK = "track"
private const val TASK_ARTWORK = "artwork"
private const val TASK_SEPARATOR = ":"
private const val ARTWORK_SIZE = 1024
private const val STALE_PART_AGE_SECONDS = 24.0 * 60.0 * 60.0
