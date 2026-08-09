package info.jukov.player.feature.download

import info.jukov.player.core.data.cache.CacheDao
import info.jukov.player.core.data.cache.OfflineArtworkEntity
import info.jukov.player.feature.auth.domain.AuthRepository
import info.jukov.player.feature.auth.domain.AuthState
import info.jukov.player.feature.auth.domain.accountKey
import info.jukov.player.feature.download.domain.DownloadState
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
    private var cancellationGeneration = 0L
    private val cancelledAccountTokens = MutableStateFlow<Set<String>>(emptySet())
    private val cancelledTaskDescriptions = MutableStateFlow<Set<String>>(emptySet())
    private val cancelledTaskIdentifiers = MutableStateFlow<Set<ULong>>(emptySet())
    private var backgroundCompletionHandler: (() -> Unit)? = null
    private var backgroundEventsFinished = false
    private var processingCallbacks = 0

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
        }
        trackIds.forEach { partUrl(accountKey, it).removeIfPresent() }
    }

    override suspend fun cancelAccount(accountKey: String) {
        advanceCancellationGeneration()
        val accountToken = safeComponent(accountKey)
        cancelledAccountTokens.update { it + accountToken }
        runOperationAndWait {
            cancelTasks { parseIosTaskDescription(it)?.accountToken == accountToken }
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
    ) {
        if (identifier != BACKGROUND_SESSION_IDENTIFIER) {
            completionHandler()
            return
        }
        delegateQueue.addOperationWithBlock {
            backgroundCompletionHandler = completionHandler
            completeBackgroundEventsIfReady()
            session.getAllTasksWithCompletionHandler { }
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
        submitOperation {
            val accountKey = accountKeyFor(metadata) ?: return@submitOperation
            if (!isTaskActive(metadata, downloadTask.taskIdentifier)) {
                return@submitOperation
            }
            dao.updateOfflineTrackProgress(
                accountKey = accountKey,
                trackId = metadata.id,
                downloadedBytes = totalBytesWritten.coerceAtLeast(0),
                expectedSize = totalBytesExpectedToWrite.takeIf { it > 0 },
            )
        }
    }

    internal fun didFinishDownloading(
        downloadTask: NSURLSessionDownloadTask,
        location: NSURL,
    ) {
        val metadata = parseIosTaskDescription(downloadTask.taskDescription) ?: return
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
                markTaskFailed(metadata, error.localizedDescription, task.taskIdentifier)
            } finally {
                endCallbackProcessing()
            }
        }
    }

    internal fun didFinishBackgroundEvents() {
        beginCallbackProcessing()
        submitOperation { endCallbackProcessing() }
        backgroundEventsFinished = true
        completeBackgroundEventsIfReady()
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
            dao.failPendingOfflineTracks(accountKey, "HTTPS is required on iOS")
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
                    client.buildUrl("download", currentSession, mapOf("id" to item.trackId)),
                ) ?: return@forEach
                val task = session.downloadTaskWithURL(url)
                task.taskDescription = description
                dao.updateOfflineTrackState(
                    accountKey, item.trackId, DownloadState.Downloading.name,
                    0, item.expectedSize, item.relativePath, null, null,
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
            }
        }
        dao.allOfflineTracks(accountKey)
            .filter { it.state == DownloadState.Completed.name }
            .forEach { scheduleArtwork(accountKey, it.trackId, generation) }
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
        if (statusCode !in 200..299 || contentType?.contains("json", ignoreCase = true) == true) {
            markTaskFailed(metadata, "HTTP $statusCode", task.taskIdentifier)
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
        if (!fileManager.moveItemAtURL(temporaryUrl, destination, error = null)) {
            markTaskFailed(
                metadata,
                "Cannot finalize file",
                task.taskIdentifier,
            )
            return
        }
        if (!isTaskActive(metadata, task.taskIdentifier)) {
            destination.removeIfPresent()
            return
        }
        val bytes = task.countOfBytesReceived.coerceAtLeast(0)
        dao.updateOfflineTrackState(
            accountKey, trackId, DownloadState.Completed.name, bytes,
            task.countOfBytesExpectedToReceive.takeIf { it > 0 }, relative(destination, accountKey),
            null, Clock.System.now().toEpochMilliseconds(),
        )
        if (!isTaskActive(metadata, task.taskIdentifier)) {
            destination.removeIfPresent()
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
                mapOf("id" to coverArtId, "size" to ARTWORK_SIZE.toString()),
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
        if (!fileManager.moveItemAtURL(temporaryUrl, destination, error = null)) {
            markTaskFailed(
                metadata,
                "Cannot finalize artwork",
                task.taskIdentifier,
            )
            return
        }
        if (!isTaskActive(metadata, task.taskIdentifier)) {
            destination.removeIfPresent()
            return
        }
        val committed = dao.upsertOfflineArtworkIfReferenced(
            OfflineArtworkEntity(
                accountKey, coverArtId, relative(destination, accountKey), contentType,
                task.countOfBytesReceived.coerceAtLeast(0), DownloadState.Completed.name,
                null, Clock.System.now().toEpochMilliseconds(),
            ),
        )
        if (!committed || !isTaskActive(metadata, task.taskIdentifier)) {
            destination.removeIfPresent()
        }
    }

    private suspend fun markTaskFailed(
        metadata: IosDownloadTaskMetadata,
        message: String,
        taskIdentifier: ULong,
    ) {
        if (!isTaskActive(metadata, taskIdentifier)) {
            return
        }
        val accountKey = accountKeyFor(metadata) ?: return
        when (metadata.kind) {
            TASK_TRACK -> {
                val item = dao.offlineTrack(accountKey, metadata.id) ?: return
                dao.updateOfflineTrackState(
                    accountKey, metadata.id, DownloadState.Failed.name, item.downloadedBytes,
                    item.expectedSize, item.relativePath, message, null,
                )
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
        processingCallbacks++
    }

    private fun endCallbackProcessing() {
        delegateQueue.addOperationWithBlock {
            processingCallbacks--
            completeBackgroundEventsIfReady()
        }
    }

    private fun completeBackgroundEventsIfReady() {
        if (!backgroundEventsFinished || processingCallbacks != 0) {
            return
        }
        val handler = backgroundCompletionHandler ?: return
        backgroundCompletionHandler = null
        backgroundEventsFinished = false
        NSOperationQueue.mainQueue.addOperationWithBlock(handler)
    }

    private fun requestNotificationAuthorization() {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
        ) { _, _ -> }
    }

    private fun postCompletionNotification(completedCount: Int) {
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

private const val BACKGROUND_SESSION_IDENTIFIER = "info.jukov.player.offline-downloads"
private const val NOTIFICATION_OPEN_DOWNLOADS_KEY = "openDownloads"
private const val COMPLETION_NOTIFICATION_IDENTIFIER = "offline-downloads-completed"
private const val OFFLINE_DIRECTORY = "offline"
private const val TRACKS_DIRECTORY = "tracks"
private const val ARTWORK_DIRECTORY = "artwork"
private const val STAGING_DIRECTORY = "offline-staging"
private const val TASK_TRACK = "track"
private const val TASK_ARTWORK = "artwork"
private const val TASK_SEPARATOR = ":"
private const val ARTWORK_SIZE = 1024
private const val STALE_PART_AGE_SECONDS = 24.0 * 60.0 * 60.0
