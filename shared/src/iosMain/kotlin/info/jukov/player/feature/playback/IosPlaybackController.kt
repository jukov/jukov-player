package info.jukov.player.feature.playback

import info.jukov.player.core.domain.AppError
import info.jukov.player.core.domain.LoadableState
import info.jukov.player.feature.playback.data.PlaybackStore
import info.jukov.player.feature.playback.domain.PlaybackController
import info.jukov.player.feature.playback.domain.PlaybackControllerFactory
import info.jukov.player.feature.playback.domain.PlaybackOrigin
import info.jukov.player.feature.playback.domain.PlaybackSnapshot
import info.jukov.player.feature.playback.domain.appendQueueItems
import info.jukov.player.feature.playback.domain.moveFutureQueueItem
import info.jukov.player.feature.playback.domain.moveFutureQueueItemsToTop
import info.jukov.player.feature.playback.domain.removeFutureQueueItems
import info.jukov.player.feature.track.domain.Track
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import platform.AVFAudio.*
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.*
import platform.MediaPlayer.MPMediaItemPropertyAlbumTitle
import platform.MediaPlayer.MPMediaItemPropertyArtwork
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPMediaItemArtwork
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandEvent
import platform.MediaPlayer.MPRemoteCommandHandlerStatusCommandFailed
import platform.MediaPlayer.MPRemoteCommandHandlerStatusNoSuchContent
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.UIKit.*
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_sync
import kotlin.math.roundToLong

object IosPlaybackControllerFactory : PlaybackControllerFactory {
    override fun create(playbackStore: PlaybackStore): PlaybackController =
        IosPlaybackController(playbackStore)
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class IosPlaybackController(
    private val playbackStore: PlaybackStore,
    private val player: AVQueuePlayer = AVQueuePlayer(),
    private val installSystemIntegrations: Boolean = true,
    private val audioSessionActivation: () -> Boolean = ::activateIosAudioSession,
) : PlaybackController {
    private val notificationCenter = NSNotificationCenter.defaultCenter
    private val nowPlayingCenter = MPNowPlayingInfoCenter.defaultCenter()
    private val remoteCommands = MPRemoteCommandCenter.sharedCommandCenter()
    private var queue = emptyList<Track>()
    private var currentIndex = -1
    private var origin: PlaybackOrigin = PlaybackOrigin.TrackList
    private var playWhenReady = false
    private var interruptionActive = false
    private var playbackError: AppError? = null
    private var artworkIdentity: String? = null
    private var artworkGeneration = 0L
    private var nowPlayingArtwork: MPMediaItemArtwork? = null
    private var playerItems = emptyList<AVPlayerItem>()
    private var pendingSeekPositionMs: Long? = null
    private var seekGeneration = 0L
    private var timeObserver: Any? = null
    private val notificationTokens = mutableListOf<Any>()

    private val _state = MutableStateFlow<LoadableState<PlaybackSnapshot>>(
        LoadableState.Loading(restoredSnapshot()),
    )
    override val state: StateFlow<LoadableState<PlaybackSnapshot>> = _state.asStateFlow()

    init {
        if (installSystemIntegrations) {
            installNotifications()
            installRemoteCommands()
            timeObserver = player.addPeriodicTimeObserverForInterval(
                interval = CMTimeMakeWithSeconds(0.5, preferredTimescale = 600),
                queue = dispatch_get_main_queue(),
            ) { handlePeriodicTimeUpdate() }
        }
        val saved = playbackStore.read()
        if (saved != null && saved.queue.isNotEmpty() && saved.currentIndex in saved.queue.indices) {
            queue = saved.queue
            currentIndex = saved.currentIndex
            origin = saved.origin
            rebuildPlayer(autoplay = false, positionMs = 0)
        } else {
            _state.value = LoadableState.Content(PlaybackSnapshot())
        }
    }

    override fun play(tracks: List<Track>, startIndex: Int) {
        play(tracks, startIndex, PlaybackOrigin.TrackList)
    }

    override fun play(tracks: List<Track>, startIndex: Int, origin: PlaybackOrigin) {
        if (tracks.isEmpty() || startIndex !in tracks.indices) {
            return
        }
        val playable = tracks.drop(startIndex)
        if (playable.any { it.streamUrl == null }) {
            fail(AppError.MissingTrackStreamUrl)
            return
        }
        invalidateNowPlayingArtwork()
        playbackError = null
        queue = playable
        currentIndex = 0
        this.origin = origin
        persist()
        publish(isLoading = true)
        rebuildPlayer(autoplay = true, positionMs = 0)
    }

    override fun playPause() {
        when (playbackToggleAction(playWhenReady)) {
            PlaybackToggleAction.Pause -> {
                playWhenReady = false
                player.pause()
                updatePlaybackState()
                return
            }
            PlaybackToggleAction.Play -> Unit
        }
        if (currentIndex !in queue.indices) {
            return
        }
        if (player.currentItem == null) {
            rebuildPlayer(autoplay = true, positionMs = 0)
            return
        }
        playWhenReady = true
        if (interruptionActive) {
            updatePlaybackState()
            return
        }
        if (!audioSessionActivation()) {
            playWhenReady = false
            fail(AppError.PlayerConnectionFailed)
            return
        }
        playbackError = null
        player.play()
        updatePlaybackState()
    }

    override fun addToQueue(tracks: List<Track>) {
        if (tracks.isEmpty()) {
            return
        }
        if (tracks.any { it.streamUrl == null }) {
            fail(AppError.MissingTrackStreamUrl)
            return
        }
        val previousQueueSize = queue.size
        val wasEmpty = queue.isEmpty()
        val playerWasExhausted = !wasEmpty && player.currentItem == null && playerItems.isEmpty()
        queue = appendQueueItems(queue, tracks)
        if (wasEmpty || playerWasExhausted) {
            currentIndex = indexAfterQueueAppend(
                previousQueueSize = previousQueueSize,
                currentIndex = currentIndex,
                playerWasExhausted = playerWasExhausted,
            )
            rebuildPlayer(autoplay = false, positionMs = 0)
        } else {
            tracks.forEach { track ->
                val item = track.toPlayerItem()
                player.insertItem(item, afterItem = null)
                playerItems = playerItems + item
            }
        }
        persist()
        publish()
    }

    override fun next() {
        if (currentIndex !in 0..<queue.lastIndex) {
            return
        }
        cancelPendingSeek()
        currentIndex++
        player.advanceToNextItem()
        playerItems = playerItems.drop(1)
        persist()
        updatePlaybackState(positionOverrideMs = 0)
    }

    override fun previous() {
        if (currentIndex !in queue.indices) {
            return
        }
        val positionMs = currentPositionMs()
        if (positionMs > PREVIOUS_RESTART_THRESHOLD_MS || currentIndex == 0) {
            seekTo(0)
            return
        }
        currentIndex--
        rebuildPlayer(autoplay = playWhenReady, positionMs = 0)
        persist()
    }

    override fun seekTo(positionMs: Long) {
        if (currentIndex !in queue.indices) {
            return
        }
        val duration = durationMs().takeIf { it > 0 } ?: queue[currentIndex].durationMs
        val target = positionMs.coerceIn(0, duration.coerceAtLeast(0))
        val generation = ++seekGeneration
        pendingSeekPositionMs = target
        val zeroTolerance = CMTimeMake(value = 0, timescale = 1)
        player.seekToTime(
            time = CMTimeMakeWithSeconds(target / 1_000.0, preferredTimescale = 600),
            toleranceBefore = zeroTolerance,
            toleranceAfter = zeroTolerance,
        ) { _ ->
            NSOperationQueue.mainQueue.addOperationWithBlock {
                if (generation == seekGeneration) {
                    pendingSeekPositionMs = null
                    updatePlaybackState()
                }
            }
        }
        updatePlaybackState(positionOverrideMs = target)
    }

    override fun playAt(index: Int) {
        if (index !in queue.indices || index < currentIndex) {
            return
        }
        currentIndex = index
        rebuildPlayer(autoplay = true, positionMs = 0)
        persist()
    }

    override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val changed = moveFutureQueueItem(queue, currentIndex, fromIndex, toIndex)
        if (changed == queue) {
            return
        }
        queue = changed
        rebuildPlayer(autoplay = playWhenReady, positionMs = currentPositionMs())
        persist()
    }

    override fun moveQueueItemsToTop(indices: Set<Int>) {
        val changed = moveFutureQueueItemsToTop(queue, currentIndex, indices)
        if (changed == queue) {
            return
        }
        queue = changed
        rebuildPlayer(autoplay = playWhenReady, positionMs = currentPositionMs())
        persist()
    }

    override fun removeQueueItems(indices: Set<Int>) {
        val changed = removeFutureQueueItems(queue, currentIndex, indices)
        if (changed == queue) {
            return
        }
        queue = changed
        rebuildPlayer(autoplay = playWhenReady, positionMs = currentPositionMs())
        persist()
    }

    override fun stopAndClear() {
        player.pause()
        playWhenReady = false
        player.removeAllItems()
        playerItems = emptyList()
        cancelPendingSeek()
        queue = emptyList()
        currentIndex = -1
        origin = PlaybackOrigin.TrackList
        interruptionActive = false
        playbackError = null
        invalidateNowPlayingArtwork()
        playbackStore.clear()
        nowPlayingCenter.nowPlayingInfo = null
        runCatching { AVAudioSession.sharedInstance().setActive(active = false, error = null) }
        _state.value = LoadableState.Content(PlaybackSnapshot())
    }

    private fun restoredSnapshot(): PlaybackSnapshot {
        val saved = playbackStore.read() ?: return PlaybackSnapshot()
        if (saved.queue.isEmpty()) {
            return PlaybackSnapshot()
        }
        val index = saved.currentIndex.coerceIn(saved.queue.indices)
        return PlaybackSnapshot(
            queue = saved.queue,
            currentIndex = index,
            durationMs = saved.queue[index].durationMs,
            origin = saved.origin,
        )
    }

    private fun rebuildPlayer(autoplay: Boolean, positionMs: Long) {
        playbackError = null
        playWhenReady = autoplay
        cancelPendingSeek()
        player.pause()
        player.removeAllItems()
        playerItems = emptyList()
        if (currentIndex !in queue.indices) {
            publish()
            return
        }
        playerItems = queue.drop(currentIndex).map { track ->
            track.toPlayerItem().also { player.insertItem(it, afterItem = null) }
        }
        if (positionMs > 0) {
            player.seekToTime(CMTimeMakeWithSeconds(positionMs / 1_000.0, preferredTimescale = 600))
        }
        if (autoplay && interruptionActive) {
            updatePlaybackState(positionOverrideMs = positionMs)
            return
        }
        if (autoplay) {
            if (!audioSessionActivation()) {
                playWhenReady = false
                updatePlaybackState(positionOverrideMs = positionMs)
                fail(AppError.PlayerConnectionFailed)
                return
            }
            player.play()
        }
        updatePlaybackState(positionOverrideMs = positionMs)
    }

    private fun Track.toPlayerItem(): AVPlayerItem {
        val url = NSURL.URLWithString(requireNotNull(streamUrl))
            ?: error("Invalid playback URL for track $id")
        return AVPlayerItem(uRL = url)
    }

    private fun installNotifications() {
        notificationTokens += notificationCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { notification -> onItemEnded(notification) }
        notificationTokens += notificationCenter.addObserverForName(
            name = AVPlayerItemFailedToPlayToEndTimeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { notification -> onItemFailed(notification) }
        notificationTokens += notificationCenter.addObserverForName(
            name = AVAudioSessionInterruptionNotification,
            `object` = AVAudioSession.sharedInstance(),
            queue = NSOperationQueue.mainQueue,
        ) { notification -> onAudioInterruption(notification) }
        notificationTokens += notificationCenter.addObserverForName(
            name = AVAudioSessionRouteChangeNotification,
            `object` = AVAudioSession.sharedInstance(),
            queue = NSOperationQueue.mainQueue,
        ) { notification -> onRouteChanged(notification) }
    }

    private fun onItemEnded(notification: NSNotification?) {
        if (notification?.`object` != playerItems.firstOrNull()) {
            return
        }
        handleCurrentItemEnded()
    }

    internal fun handleCurrentItemEnded() {
        val completedPositionMs = terminalPlaybackPositionMs(
            nativeDurationMs = durationMs(),
            trackDurationMs = queue.getOrNull(currentIndex)?.durationMs,
        )
        playerItems = playerItems.drop(1)
        cancelPendingSeek()
        if (currentIndex < queue.lastIndex) {
            currentIndex++
            persist()
            updatePlaybackState(positionOverrideMs = 0)
        } else {
            playWhenReady = false
            updatePlaybackState(positionOverrideMs = completedPositionMs)
        }
    }

    private fun onItemFailed(notification: NSNotification?) {
        if (!shouldPublishPlaybackFailure(
                isCurrentItem = notification?.`object` === player.currentItem,
                hasError = true,
            )
        ) {
            return
        }
        handleCurrentItemFailed()
    }

    internal fun handleCurrentItemFailed() {
        playWhenReady = false
        player.pause()
        publish(isLoading = false)
        fail(AppError.PlaybackFailed)
    }

    private fun onAudioInterruption(notification: NSNotification?) {
        val userInfo = notification?.userInfo ?: return
        val type = (userInfo[AVAudioSessionInterruptionTypeKey] as? NSNumber)?.unsignedIntegerValue
        val options = (userInfo[AVAudioSessionInterruptionOptionKey] as? NSNumber)
            ?.unsignedIntegerValue ?: 0u
        handleAudioInterruption(type, options)
    }

    internal fun handleAudioInterruption(type: ULong?, options: ULong) {
        when (type) {
            AVAudioSessionInterruptionTypeBegan -> {
                interruptionActive = true
                updatePlaybackState()
            }
            AVAudioSessionInterruptionTypeEnded -> {
                interruptionActive = false
                val shouldResume = shouldResumeAfterInterruption(
                    playWhenReady = playWhenReady,
                    systemAllowsResume =
                        options and AVAudioSessionInterruptionOptionShouldResume != 0uL,
                )
                if (shouldResume) {
                    if (!audioSessionActivation()) {
                        playWhenReady = false
                        fail(AppError.PlayerConnectionFailed)
                        return
                    }
                    playbackError = null
                    player.play()
                } else {
                    playWhenReady = false
                }
                updatePlaybackState()
            }
        }
    }

    private fun onRouteChanged(notification: NSNotification?) {
        val reason = (notification?.userInfo?.get(AVAudioSessionRouteChangeReasonKey) as? NSNumber)
            ?.unsignedIntegerValue
        if (reason == AVAudioSessionRouteChangeReasonOldDeviceUnavailable) {
            playWhenReady = false
            player.pause()
            updatePlaybackState()
        }
    }

    private fun installRemoteCommands() {
        remoteCommands.playCommand.addTargetWithHandler { onMainQueue { remotePlay() } }
        remoteCommands.pauseCommand.addTargetWithHandler { onMainQueue { remotePause() } }
        remoteCommands.togglePlayPauseCommand.addTargetWithHandler { onMainQueue { remoteToggle() } }
        remoteCommands.nextTrackCommand.addTargetWithHandler { onMainQueue { remoteNext() } }
        remoteCommands.previousTrackCommand.addTargetWithHandler { onMainQueue { remotePrevious() } }
        remoteCommands.changePlaybackPositionCommand.addTargetWithHandler { event ->
            onMainQueue { remoteSeek(event) }
        }
    }

    private fun onMainQueue(command: () -> Long): Long {
        if (NSThread.isMainThread) {
            return command()
        }
        var result = MPRemoteCommandHandlerStatusCommandFailed
        dispatch_sync(dispatch_get_main_queue()) {
            result = command()
        }
        return result
    }

    internal fun remotePlay(): Long {
        if (currentIndex !in queue.indices) {
            return MPRemoteCommandHandlerStatusNoSuchContent
        }
        if (!playWhenReady) {
            playPause()
        }
        return MPRemoteCommandHandlerStatusSuccess
    }

    internal fun remotePause(): Long {
        if (currentIndex !in queue.indices) {
            return MPRemoteCommandHandlerStatusNoSuchContent
        }
        if (playWhenReady) {
            playPause()
        }
        return MPRemoteCommandHandlerStatusSuccess
    }

    internal fun remoteToggle(): Long {
        if (currentIndex !in queue.indices) {
            return MPRemoteCommandHandlerStatusNoSuchContent
        }
        playPause()
        return MPRemoteCommandHandlerStatusSuccess
    }

    internal fun remoteNext(): Long {
        if (currentIndex !in 0..<queue.lastIndex) {
            return MPRemoteCommandHandlerStatusNoSuchContent
        }
        next()
        return MPRemoteCommandHandlerStatusSuccess
    }

    internal fun remotePrevious(): Long {
        if (currentIndex !in queue.indices) {
            return MPRemoteCommandHandlerStatusNoSuchContent
        }
        previous()
        return MPRemoteCommandHandlerStatusSuccess
    }

    private fun remoteSeek(event: MPRemoteCommandEvent?): Long {
        val changeEvent = event as? MPChangePlaybackPositionCommandEvent
            ?: return MPRemoteCommandHandlerStatusCommandFailed
        seekTo((changeEvent.positionTime * 1_000).roundToLong())
        return MPRemoteCommandHandlerStatusSuccess
    }

    internal fun handlePeriodicTimeUpdate() {
        updatePlaybackState()
    }

    private fun updatePlaybackState(positionOverrideMs: Long? = null) {
        synchronizeCurrentPlayerItem()
        val currentItem = player.currentItem
        if (shouldPublishPlaybackFailure(
                isCurrentItem = currentItem != null,
                hasError = currentItem?.error != null || currentItem?.status == AVPlayerItemStatusFailed,
            )
        ) {
            playWhenReady = false
            player.pause()
            publish(isLoading = false)
            fail(AppError.PlaybackFailed)
            return
        }
        publish(
            positionMs = playbackPositionMs(
                positionOverrideMs = positionOverrideMs,
                pendingSeekPositionMs = pendingSeekPositionMs,
                currentPositionMs = currentPositionMs(),
            ),
            isLoading = currentIndex in queue.indices && playWhenReady && !interruptionActive &&
                player.currentItem?.isPlaybackLikelyToKeepUp() == false,
        )
    }

    private fun synchronizeCurrentPlayerItem() {
        val currentItem = player.currentItem ?: return
        val offset = playerItems.indexOfFirst { item -> item == currentItem }
        if (offset <= 0) {
            return
        }
        val synchronizedIndex = currentIndex + offset
        if (synchronizedIndex !in queue.indices) {
            return
        }
        currentIndex = synchronizedIndex
        playerItems = playerItems.drop(offset)
        cancelPendingSeek()
        persist()
    }

    private fun cancelPendingSeek() {
        seekGeneration++
        pendingSeekPositionMs = null
    }

    private fun publish(
        positionMs: Long = currentPositionMs(),
        isLoading: Boolean = false,
    ) {
        val duration = durationMs().takeIf { it > 0 }
            ?: queue.getOrNull(currentIndex)?.durationMs
            ?: 0
        val snapshot = PlaybackSnapshot(
            queue = queue,
            currentIndex = currentIndex,
            positionMs = positionMs.coerceAtLeast(0),
            durationMs = duration,
            isPlaying = playWhenReady && !interruptionActive,
            isLoading = isLoading,
            origin = origin,
        )
        _state.value = playbackLoadableState(snapshot, playbackError)
        updateNowPlaying(snapshot)
    }

    private fun updateNowPlaying(snapshot: PlaybackSnapshot) {
        if (!installSystemIntegrations) {
            return
        }
        val track = snapshot.currentTrack
        if (track == null) {
            nowPlayingCenter.nowPlayingInfo = null
            return
        }
        val identity = track.artworkIdentity()
        val trackChanged = artworkIdentity != identity
        if (trackChanged) {
            artworkIdentity = identity
            artworkGeneration++
            nowPlayingArtwork = null
        }
        val info = mutableMapOf<Any?, Any?>(
            MPMediaItemPropertyTitle to track.title,
            MPMediaItemPropertyArtist to track.artist,
            MPMediaItemPropertyAlbumTitle to (track.album ?: ""),
            MPMediaItemPropertyPlaybackDuration to snapshot.durationMs / 1_000.0,
            MPNowPlayingInfoPropertyElapsedPlaybackTime to snapshot.positionMs / 1_000.0,
            MPNowPlayingInfoPropertyPlaybackRate to if (snapshot.isPlaying) {
                1.0
            } else {
                0.0
            },
        )
        nowPlayingArtwork?.let { info[MPMediaItemPropertyArtwork] = it }
        nowPlayingCenter.nowPlayingInfo = info
        remoteCommands.nextTrackCommand.enabled = snapshot.hasNext
        remoteCommands.previousTrackCommand.enabled = snapshot.hasPrevious || snapshot.positionMs > 0
        if (trackChanged) {
            loadNowPlayingArtwork(track, artworkGeneration, identity)
        }
    }

    private fun loadNowPlayingArtwork(track: Track, generation: Long, identity: String) {
        val url = track.coverArtUrl?.let(NSURL::URLWithString) ?: return
        platform.Foundation.NSURLSession.sharedSession.dataTaskWithURL(url) { data, _, _ ->
            val image = data?.let { UIImage.imageWithData(it) } ?: return@dataTaskWithURL
            NSOperationQueue.mainQueue.addOperationWithBlock {
                val currentIdentity = queue.getOrNull(currentIndex)?.artworkIdentity()
                if (!isCurrentArtworkRequest(
                        submittedGeneration = generation,
                        currentGeneration = artworkGeneration,
                        requestedIdentity = identity,
                        currentIdentity = currentIdentity,
                    )
                ) {
                    return@addOperationWithBlock
                }
                val artwork = MPMediaItemArtwork(boundsSize = image.size) { image }
                nowPlayingArtwork = artwork
                val info = nowPlayingCenter.nowPlayingInfo?.toMutableMap() ?: return@addOperationWithBlock
                info[MPMediaItemPropertyArtwork] = artwork
                nowPlayingCenter.nowPlayingInfo = info
            }
        }.resume()
    }

    private fun invalidateNowPlayingArtwork() {
        artworkGeneration++
        artworkIdentity = null
        nowPlayingArtwork = null
    }

    private fun Track.artworkIdentity(): String = "$id|${coverArtUrl.orEmpty()}"

    private fun currentPositionMs(): Long = player.currentTime().validSeconds().toMilliseconds()

    private fun durationMs(): Long =
        (player.currentItem?.duration?.validSeconds() ?: 0.0).toMilliseconds()

    private fun CValue<platform.CoreMedia.CMTime>.validSeconds(): Double {
        val seconds = CMTimeGetSeconds(this)
        return if (seconds.isFinite() && seconds >= 0) {
            seconds
        } else {
            0.0
        }
    }

    private fun Double.toMilliseconds(): Long = (this * 1_000).roundToLong().coerceAtLeast(0)

    private fun persist() {
        if (queue.isEmpty()) {
            playbackStore.clear()
        } else {
            playbackStore.write(queue, currentIndex, origin)
        }
    }

    private fun fail(error: AppError) {
        playbackError = error
        _state.update { LoadableState.Failure(error, it.content) }
    }

}

internal fun indexAfterQueueAppend(
    previousQueueSize: Int,
    currentIndex: Int,
    playerWasExhausted: Boolean,
): Int = if (previousQueueSize == 0 || playerWasExhausted) {
    previousQueueSize
} else {
    currentIndex
}

internal enum class PlaybackToggleAction { Play, Pause }

internal fun playbackToggleAction(playWhenReady: Boolean): PlaybackToggleAction =
    if (playWhenReady) {
        PlaybackToggleAction.Pause
    } else {
        PlaybackToggleAction.Play
    }

internal fun terminalPlaybackPositionMs(nativeDurationMs: Long, trackDurationMs: Long?): Long =
    nativeDurationMs.takeIf { it > 0 } ?: trackDurationMs?.coerceAtLeast(0) ?: 0

internal fun playbackPositionMs(
    positionOverrideMs: Long?,
    pendingSeekPositionMs: Long?,
    currentPositionMs: Long,
): Long = positionOverrideMs ?: pendingSeekPositionMs ?: currentPositionMs

internal fun playbackLoadableState(
    snapshot: PlaybackSnapshot,
    error: AppError?,
): LoadableState<PlaybackSnapshot> = if (error != null) {
    LoadableState.Failure(error, snapshot)
} else {
    LoadableState.Content(snapshot)
}

internal fun shouldResumeAfterInterruption(
    playWhenReady: Boolean,
    systemAllowsResume: Boolean,
): Boolean = playWhenReady && systemAllowsResume

internal fun shouldPublishPlaybackFailure(isCurrentItem: Boolean, hasError: Boolean): Boolean =
    isCurrentItem && hasError

internal fun isCurrentArtworkRequest(
    submittedGeneration: Long,
    currentGeneration: Long,
    requestedIdentity: String,
    currentIdentity: String?,
): Boolean = submittedGeneration == currentGeneration && requestedIdentity == currentIdentity

private const val PREVIOUS_RESTART_THRESHOLD_MS = 3_000L

@OptIn(ExperimentalForeignApi::class)
private fun activateIosAudioSession(): Boolean {
    val audioSession = AVAudioSession.sharedInstance()
    if (!audioSession.setCategory(AVAudioSessionCategoryPlayback, error = null) ||
        !audioSession.setActive(active = true, error = null)
    ) {
        return false
    }
    return true
}
