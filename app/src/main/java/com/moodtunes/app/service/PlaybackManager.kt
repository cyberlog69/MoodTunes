package com.moodtunes.app.service

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.moodtunes.app.data.local.db.dao.MoodHistoryDao
import com.moodtunes.app.data.local.db.dao.SongDao
import com.moodtunes.app.data.local.db.entity.MoodHistoryEntity
import com.moodtunes.app.data.local.preferences.PlaybackPreferencesRepository
import com.moodtunes.app.data.remote.OnlineStreamRepository
import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.platform.ConnectivityMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val onlineStreamRepository: OnlineStreamRepository,
    private val audioEffectsManager: AudioEffectsManager,
    private val playbackPreferencesRepository: PlaybackPreferencesRepository,
    private val songDao: SongDao,
    private val moodHistoryDao: MoodHistoryDao,
    private val connectivityMonitor: ConnectivityMonitor
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var mediaController: MediaController? = null

    init {
        connectivityMonitor.register()
        scope.launch {
            // Auto-retry failed streams the moment connectivity returns.
            connectivityMonitor.isOnline.collect { online ->
                if (online && _playbackError.value?.isRetryable == true) {
                    retryPlayback()
                }
            }
        }
    }

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playlist = MutableStateFlow<List<Song>>(emptyList())
    val playlist: StateFlow<List<Song>> = _playlist.asStateFlow()

    private val _currentMood = MutableStateFlow<MoodType?>(null)
    val currentMood: StateFlow<MoodType?> = _currentMood.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(playbackPreferencesRepository.playbackSpeed)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _isCrossfadeEnabled = MutableStateFlow(playbackPreferencesRepository.crossfadeEnabled)
    val isCrossfadeEnabled: StateFlow<Boolean> = _isCrossfadeEnabled.asStateFlow()

    private val _crossfadeDurationMs = MutableStateFlow(playbackPreferencesRepository.crossfadeDurationMs)
    val crossfadeDurationMs: StateFlow<Int> = _crossfadeDurationMs.asStateFlow()

    private val _smartShuffleEnabled = MutableStateFlow(false)
    val isSmartShuffleEnabled: StateFlow<Boolean> = _smartShuffleEnabled.asStateFlow()

    private val _playbackError = MutableStateFlow<PlaybackError?>(null)
    val playbackError: StateFlow<PlaybackError?> = _playbackError.asStateFlow()

    private var consecutiveAutoSkips = 0
    private var retryAttempts = 0
    private var retryJob: Job? = null

    private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingMs: StateFlow<Long?> = _sleepTimerRemainingMs.asStateFlow()

    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var fadeJob: Job? = null
    private var effectsRetryJob: Job? = null

    // Tracks the current mood listening session so it can be persisted as a
    // MoodHistory entry once the user switches queues or moods.
    private var moodSession: MoodSession? = null

    private data class MoodSession(
        val mood: MoodType,
        val startTime: Long,
        var songCount: Int,
        var listenedMs: Long
    )

    init {
        initController()
    }

    private fun initController() {
        val sessionToken = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                mediaController = controller
                controller.setPlaybackParameters(
                    PlaybackParameters(
                        playbackPreferencesRepository.playbackSpeed,
                        playbackPreferencesRepository.playbackPitch
                    )
                )
                attachListener(controller)
            } catch (e: Exception) {
                Timber.e(e, "Failed to connect to media session")
            }
        }, MoreExecutors.directExecutor())
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0, mood: MoodType? = null) {
        if (songs.isEmpty()) return
        // Rotate mood-session analytics: persist the previous mood's session
        // before switching queues, then open a new one for this queue.
        val previousMood = _currentMood.value
        if (previousMood != mood) {
            finalizeMoodSession()
            if (mood != null) {
                moodSession = MoodSession(
                    mood = mood,
                    startTime = System.currentTimeMillis(),
                    songCount = 1,
                    listenedMs = 0L
                )
            }
        }
        // Mood-weighted smart shuffle: reorder the queue to favor songs that
        // match the active mood, then play the requested song from its new spot.
        val orderedSongs = if (mood != null && _smartShuffleEnabled.value) {
            val targetId = songs.getOrNull(startIndex)?.id
            val ordered = com.moodtunes.app.domain.util.MoodScoring.smartOrder(songs, mood)
            ordered to ordered.indexOfFirst { it.id == targetId }.coerceAtLeast(0)
        } else {
            songs to startIndex
        }
        val effectiveSongs = orderedSongs.first
        val effectiveStart = orderedSongs.second

        _playlist.value = effectiveSongs
        _currentMood.value = mood
        _currentSong.value = effectiveSongs.getOrNull(effectiveStart)

        scope.launch {
            val controller = mediaController ?: run {
                delay(300)
                mediaController
            } ?: return@launch

            // Pre-resolve stream URLs asynchronously for instant low-latency playback
            val targetSong = effectiveSongs.getOrNull(effectiveStart)
            val resolvedUri = if (targetSong != null && targetSong.isStream && targetSong.uri.toString().contains("/streams/")) {
                val direct = onlineStreamRepository.resolveDirectStreamUrl(targetSong.uri.toString())
                Uri.parse(direct)
            } else {
                targetSong?.uri
            }

            val mediaItems = effectiveSongs.mapIndexed { index, song ->
                val itemUri = if (index == effectiveStart && resolvedUri != null) resolvedUri else song.uri
                MediaItem.Builder()
                    .setUri(itemUri)
                    .setMediaId(song.id.toString())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .setArtworkUri(song.albumArtUri)
                            .build()
                    )
                    .build()
            }

            controller.setMediaItems(mediaItems, effectiveStart, 0L)
            controller.prepare()
            controller.play()
            ensureAudioEffectsAttached()
        }
    }

    fun playPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
            ensureAudioEffectsAttached()
        }
    }

    fun pause() {
        mediaController?.pause()
    }

    fun play() {
        mediaController?.play()
        ensureAudioEffectsAttached()
    }

    fun skipNext() {
        mediaController?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        val controller = mediaController ?: return
        if (controller.currentPosition > 3000) {
            controller.seekTo(0L)
        } else {
            controller.seekToPreviousMediaItem()
        }
    }

    fun seekTo(positionFraction: Float) {
        val duration = _durationMs.value
        if (duration > 0) {
            mediaController?.seekTo((positionFraction * duration).toLong())
        }
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        val newMode = !controller.shuffleModeEnabled
        controller.shuffleModeEnabled = newMode
        _isShuffleEnabled.value = newMode
    }

    fun toggleSmartShuffle() {
        val enabled = !_smartShuffleEnabled.value
        _smartShuffleEnabled.value = enabled
        val mood = _currentMood.value
        val playlist = _playlist.value
        val current = _currentSong.value
        if (enabled && mood != null && playlist.size > 1) {
            val ordered = com.moodtunes.app.domain.util.MoodScoring.smartOrder(playlist, mood)
            val newIndex = ordered.indexOfFirst { it.id == current?.id }.coerceAtLeast(0)
            playSongs(ordered, newIndex, mood)
        }
    }

    fun toggleRepeat() {
        val controller = mediaController ?: return
        val nextMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = nextMode
        _repeatMode.value = nextMode
    }

    // ── Playback speed & pitch ───────────────────────────────────────────────
    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 3f)
        playbackPreferencesRepository.playbackSpeed = clamped
        _playbackSpeed.value = clamped
        val controller = mediaController ?: return
        controller.setPlaybackParameters(
            PlaybackParameters(clamped, controller.playbackParameters.pitch)
        )
    }

    fun setPlaybackPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.5f, 2f)
        playbackPreferencesRepository.playbackPitch = clamped
        val controller = mediaController ?: return
        controller.setPlaybackParameters(
            PlaybackParameters(controller.playbackParameters.speed, clamped)
        )
    }

    // ── Sleep timer ──────────────────────────────────────────────────────────
    fun startSleepTimer(minutes: Int) {
        if (minutes <= 0) {
            cancelSleepTimer()
            return
        }
        sleepTimerJob?.cancel()
        val endTime = System.currentTimeMillis() + minutes * 60_000L
        sleepTimerJob = scope.launch {
            while (isActive) {
                val remaining = endTime - System.currentTimeMillis()
                if (remaining <= 0) {
                    _sleepTimerRemainingMs.value = null
                    mediaController?.pause()
                    break
                }
                _sleepTimerRemainingMs.value = remaining
                delay(1_000)
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemainingMs.value = null
    }

    // ── Crossfade ────────────────────────────────────────────────────────────
    fun setCrossfadeEnabled(enabled: Boolean) {
        playbackPreferencesRepository.crossfadeEnabled = enabled
        _isCrossfadeEnabled.value = enabled
        if (!enabled) mediaController?.volume = 1f
    }

    fun setCrossfadeDurationMs(durationMs: Int) {
        val clamped = durationMs.coerceIn(0, 10_000)
        playbackPreferencesRepository.crossfadeDurationMs = clamped
        _crossfadeDurationMs.value = clamped
    }

    private fun fadeIn() {
        fadeJob?.cancel()
        val controller = mediaController ?: return
        if (!_isCrossfadeEnabled.value) {
            controller.volume = 1f
            return
        }
        fadeJob = scope.launch {
            val duration = _crossfadeDurationMs.value.coerceAtLeast(0)
            val steps = (duration / 50).coerceAtLeast(1)
            repeat(steps + 1) { step ->
                if (!isActive) return@launch
                controller.volume = step.toFloat() / steps
                delay(50)
            }
            controller.volume = 1f
        }
    }

    // ── Queue editor ─────────────────────────────────────────────────────────
    private fun createMediaItem(song: Song): MediaItem {
        return MediaItem.Builder()
            .setUri(song.uri)
            .setMediaId(song.id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(song.albumArtUri)
                    .build()
            )
            .build()
    }

    fun playSongAtIndex(index: Int) {
        val controller = mediaController ?: return
        val queue = _playlist.value
        if (index !in queue.indices) return
        controller.seekToDefaultPosition(index)
        controller.play()
        _currentSong.value = queue[index]
    }

    fun addToQueue(song: Song) {
        val queue = _playlist.value
        if (queue.isEmpty()) {
            playSongs(listOf(song), 0)
            return
        }
        val controller = mediaController ?: return
        val mediaItem = createMediaItem(song)
        controller.addMediaItem(mediaItem)
        _playlist.value = queue + song
    }

    fun playNext(song: Song) {
        val queue = _playlist.value
        if (queue.isEmpty()) {
            playSongs(listOf(song), 0)
            return
        }
        val controller = mediaController ?: return
        val currentIndex = controller.currentMediaItemIndex.coerceAtLeast(0)
        val insertIndex = (currentIndex + 1).coerceAtMost(queue.size)
        val mediaItem = createMediaItem(song)
        controller.addMediaItem(insertIndex, mediaItem)
        val mutable = queue.toMutableList()
        mutable.add(insertIndex, song)
        _playlist.value = mutable
    }

    fun removeFromQueue(index: Int) {
        val controller = mediaController ?: return
        val queue = _playlist.value
        if (index !in queue.indices) return
        controller.removeMediaItem(index)
        _playlist.value = queue.toMutableList().apply { removeAt(index) }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val controller = mediaController ?: return
        val queue = _playlist.value
        if (fromIndex !in queue.indices || toIndex !in queue.indices || fromIndex == toIndex) return
        controller.moveMediaItem(fromIndex, toIndex)
        _playlist.value = queue.toMutableList().apply {
            val item = removeAt(fromIndex)
            add(toIndex, item)
        }
    }

    fun clearQueue(keepCurrent: Boolean = true) {
        val controller = mediaController ?: return
        val queue = _playlist.value
        if (queue.isEmpty()) return

        if (keepCurrent) {
            val currentIndex = controller.currentMediaItemIndex
            val currentSong = queue.getOrNull(currentIndex)
            if (currentSong != null && currentIndex >= 0) {
                // Remove all items after currentIndex (back to front)
                for (i in queue.indices.reversed()) {
                    if (i != currentIndex) {
                        controller.removeMediaItem(i)
                    }
                }
                _playlist.value = listOf(currentSong)
            }
        } else {
            controller.clearMediaItems()
            _playlist.value = emptyList()
            _currentSong.value = null
            _isPlaying.value = false
        }
    }

    fun shuffleQueue() {
        val controller = mediaController ?: return
        val queue = _playlist.value
        if (queue.size <= 2) return

        val currentIndex = controller.currentMediaItemIndex
        val currentSong = queue.getOrNull(currentIndex) ?: return

        val upcoming = queue.filterIndexed { idx, _ -> idx != currentIndex }.shuffled()
        val newPlaylist = mutableListOf<Song>().apply {
            add(currentSong)
            addAll(upcoming)
        }
        playSongs(newPlaylist, 0, _currentMood.value)
    }

    // ── Equalizer & bass boost ───────────────────────────────────────────────
    fun toggleEqualizer(enabled: Boolean) = audioEffectsManager.toggleEqualizer(enabled)
    fun toggleBassBoost(enabled: Boolean) = audioEffectsManager.toggleBassBoost(enabled)
    fun setBassBoostStrength(strength: Short) = audioEffectsManager.setBassBoostStrength(strength)
    fun setBandLevel(bandIndex: Int, normalized: Float) = audioEffectsManager.setBandLevel(bandIndex, normalized)
    fun resetEqualizer() = audioEffectsManager.resetEqualizer()
    fun applyEqualizerPreset(presetIndex: Int) = audioEffectsManager.applyPreset(presetIndex)

    private fun ensureAudioEffectsAttached() {
        effectsRetryJob?.cancel()
        // Effect binding now lives in [MusicPlaybackService], where the player's real
        // audio session id is available on the ExoPlayer instance. The UI side only
        // keeps the retry job cancelled so a stale loop never runs here.
    }

    private fun attachListener(controller: MediaController) {
        _isPlaying.value = controller.isPlaying
        _isShuffleEnabled.value = controller.shuffleModeEnabled
        _repeatMode.value = controller.repeatMode

        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) startProgressTracking() else progressJob?.cancel()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = controller.currentMediaItemIndex
                val current = _playlist.value.getOrNull(index)
                if (current != null) {
                    _currentSong.value = current
                    // Record the listen for Discovery/Personalization.
                    recordPlay(current, reason)
                }
                _durationMs.value = controller.duration.coerceAtLeast(0L)
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    fadeIn()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _durationMs.value = controller.duration.coerceAtLeast(0L)
                    // Playback recovered — reset error state and counters.
                    consecutiveAutoSkips = 0
                    retryAttempts = 0
                    _playbackError.value = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                retryJob?.cancel()
                val code = error.errorCode
                val isNetworkError = code in NETWORK_ERROR_CODES
                val isFatalDecode = code in FATAL_ERROR_CODES
                val message = friendlyErrorMessage(code, error.message)

                when {
                    isFatalDecode && consecutiveAutoSkips < MAX_AUTO_SKIPS -> {
                        consecutiveAutoSkips++
                        _playbackError.value = PlaybackError(
                            message = "$message — skipping to next track",
                            isRetryable = false
                        )
                        Timber.w(error, "Playback decode error, auto-skipping (#%d)", consecutiveAutoSkips)
                        mediaController?.seekToNextMediaItem()
                    }
                    isNetworkError && connectivityMonitor.isOnline.value &&
                        retryAttempts < MAX_RETRY_ATTEMPTS -> {
                        retryAttempts++
                        _playbackError.value = PlaybackError(
                            message = "$message — retrying ($retryAttempts/$MAX_RETRY_ATTEMPTS)",
                            isRetryable = true
                        )
                        scheduleRetry()
                    }
                    else -> {
                        _playbackError.value = PlaybackError(
                            message = if (isNetworkError && !connectivityMonitor.isOnline.value) {
                                "No internet connection"
                            } else {
                                message
                            },
                            isRetryable = isNetworkError
                        )
                    }
                }
                Timber.e(error, "Playback error code=%d", code)
            }
        })
    }

    /** Retries the current item after a transient (usually network) error. */
    fun retryPlayback() {
        val controller = mediaController ?: return
        retryJob?.cancel()
        controller.prepare()
        controller.play()
        ensureAudioEffectsAttached()
        _playbackError.value = null
    }

    /** Manually skips past the errored item. */
    fun skipOnError() {
        mediaController?.seekToNextMediaItem()
    }

    private fun scheduleRetry() {
        retryJob?.cancel()
        val attempt = retryAttempts
        retryJob = scope.launch {
            delay(RETRY_BACKOFF_MS * (1L shl (attempt - 1)))
            retryPlayback()
        }
    }

    private fun friendlyErrorMessage(code: Int, rawMessage: String?): String {
        val fallback = rawMessage ?: "Playback failed"
        return when (code) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Network connection lost"
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_TIMEOUT -> "Network request timed out"
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Stream server returned an error"
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> "Stream server returned an unexpected response"
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> "This track can't be played on this device"
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "Missing permission to play this file"
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> "Live stream fell behind — restarting"
            else -> fallback
        }
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            var lastTickMs = System.currentTimeMillis()
            while (isActive) {
                mediaController?.let { controller ->
                    _currentPositionMs.value = controller.currentPosition.coerceAtLeast(0L)
                    _durationMs.value = controller.duration.coerceAtLeast(0L)
                }
                // Accumulate listening time for the active mood session (500 ms per tick).
                val now = System.currentTimeMillis()
                moodSession?.let { session ->
                    if (controllerIsPlaying()) {
                        session.listenedMs += now - lastTickMs
                    }
                }
                lastTickMs = now
                delay(500)
            }
        }
    }

    private fun controllerIsPlaying(): Boolean {
        val controller = mediaController ?: return _isPlaying.value
        return controller.isPlaying
    }

    /** Persists the current mood session (if any) to mood history. */
    private fun finalizeMoodSession() {
        val session = moodSession ?: return
        moodSession = null
        if (session.listenedMs < 10_000) return // ignore throwaway sessions
        scope.launch {
            runCatching {
                moodHistoryDao.insertMoodEntry(
                    MoodHistoryEntity(
                        moodTypeName = session.mood.name,
                        timestamp = session.startTime,
                        songCount = session.songCount,
                        durationListenedMs = session.listenedMs
                    )
                )
            }
        }
    }

    /** Increments play count and refreshes recency for the given song. */
    private fun recordPlay(song: Song, reason: Int) {
        // Count the song toward the active mood session unless this was just the
        // queue being (re)built — the first song is already counted when the
        // session opens.
        if (moodSession != null && reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
            moodSession?.songCount = moodSession!!.songCount + 1
        }
        if (song.isStream) return
        scope.launch {
            runCatching { songDao.incrementPlayCount(song.id, System.currentTimeMillis()) }
        }
    }

    companion object {
        private const val MAX_AUTO_SKIPS = 3
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_BACKOFF_MS = 1500L

        private val NETWORK_ERROR_CODES = setOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            PlaybackException.ERROR_CODE_TIMEOUT
        )

        private val FATAL_ERROR_CODES = setOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED
        )
    }
}

/** Describes a recoverable or terminal playback failure surfaced to the UI. */
data class PlaybackError(
    val message: String,
    val isRetryable: Boolean
)
