package com.moodtunes.app.presentation.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.moodtunes.app.data.local.lyrics.LyricsRepository
import com.moodtunes.app.domain.model.LyricsLine
import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.usecase.ToggleFavoriteUseCase
import com.moodtunes.app.platform.AudioOutputInfo
import com.moodtunes.app.platform.AudioOutputMonitor
import com.moodtunes.app.platform.CastPlaybackManager
import com.moodtunes.app.service.AudioEffectsManager
import com.moodtunes.app.service.PlaybackError
import com.moodtunes.app.service.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val songs: List<Song> = emptyList(),
    val currentSongIndex: Int = 0,
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val selectedMood: MoodType? = null,
    val playbackSpeed: Float = 1f,
    val isSmartShuffleEnabled: Boolean = false,
    val isCrossfadeEnabled: Boolean = false,
    val crossfadeDurationMs: Int = 1500,
    val sleepTimerRemainingMs: Long? = null,
    val isEqualizerEnabled: Boolean = false,
    val isBassBoostEnabled: Boolean = false,
    val bassBoostStrength: Short = 0,
    val equalizerLevels: List<Float> = emptyList(),
    val equalizerFrequencies: List<Int> = emptyList(),
    val equalizerPresets: List<String> = emptyList(),
    val lyrics: List<LyricsLine> = emptyList(),
    val isLyricsLoading: Boolean = false,
    val isCasting: Boolean = false,
    val castDeviceName: String? = null,
    val audioOutput: AudioOutputInfo? = null,
    val castMessage: String? = null,
    val playbackError: PlaybackError? = null
) {
    val progress: Float get() = if (durationMs > 0) currentPositionMs / durationMs.toFloat() else 0f
}

enum class RepeatMode { OFF, ONE, ALL }

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackManager: PlaybackManager,
    private val audioEffectsManager: AudioEffectsManager,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val lyricsRepository: LyricsRepository,
    private val castPlaybackManager: CastPlaybackManager,
    private val audioOutputMonitor: AudioOutputMonitor
) : ViewModel() {

    private val _lyrics = MutableStateFlow<List<LyricsLine>>(emptyList())
    private val _isLyricsLoading = MutableStateFlow(false)
    private val _castMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PlayerUiState> = combine(
        playbackManager.playlist,
        playbackManager.currentSong,
        playbackManager.isPlaying,
        playbackManager.currentPositionMs,
        playbackManager.durationMs,
        playbackManager.currentMood,
        playbackManager.isShuffleEnabled,
        playbackManager.repeatMode,
        playbackManager.playbackSpeed,
        playbackManager.isSmartShuffleEnabled,
        playbackManager.isCrossfadeEnabled,
        playbackManager.crossfadeDurationMs,
        playbackManager.sleepTimerRemainingMs,
        audioEffectsManager.isEqualizerEnabled,
        audioEffectsManager.isBassBoostEnabled,
        audioEffectsManager.bassBoostStrength,
        audioEffectsManager.bandLevels,
        audioEffectsManager.bandFrequencies,
        audioEffectsManager.presets,
        _lyrics,
        _isLyricsLoading,
        castPlaybackManager.isCasting,
        castPlaybackManager.castDeviceName,
        audioOutputMonitor.output,
        _castMessage,
        playbackManager.playbackError
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val playlist = args[0] as List<Song>
        val song = args[1] as? Song
        val isPlaying = args[2] as Boolean
        val pos = args[3] as Long
        val dur = args[4] as Long
        val mood = args[5] as? MoodType
        val shuffle = args[6] as Boolean
        val repeatModeInt = args[7] as Int
        val speed = args[8] as Float
        val smartShuffle = args[9] as Boolean
        val crossfade = args[10] as Boolean
        val crossfadeDur = args[11] as Int
        val sleepRemaining = args[12] as Long?
        val eqEnabled = args[13] as Boolean
        val bassEnabled = args[14] as Boolean
        val bassStrength = args[15] as Short
        @Suppress("UNCHECKED_CAST")
        val eqLevels = args[16] as List<Float>
        @Suppress("UNCHECKED_CAST")
        val eqFreqs = args[17] as List<Int>
        @Suppress("UNCHECKED_CAST")
        val eqPresets = args[18] as List<String>
        @Suppress("UNCHECKED_CAST")
        val lyrics = args[19] as List<LyricsLine>
        val lyricsLoading = args[20] as Boolean
        val casting = args[21] as Boolean
        val castDevice = args[22] as String?
        val output = args[23] as AudioOutputInfo?
        val message = args[24] as String?
        val playbackError = args[25] as PlaybackError?

        val index = playlist.indexOfFirst { it.id == song?.id }.coerceAtLeast(0)
        val repeatMode = when (repeatModeInt) {
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            else -> RepeatMode.OFF
        }
        PlayerUiState(
            songs = playlist,
            currentSongIndex = index,
            currentSong = song,
            isPlaying = isPlaying,
            currentPositionMs = pos,
            durationMs = dur,
            isShuffleEnabled = shuffle,
            repeatMode = repeatMode,
            selectedMood = mood,
            playbackSpeed = speed,
            isSmartShuffleEnabled = smartShuffle,
            isCrossfadeEnabled = crossfade,
            crossfadeDurationMs = crossfadeDur,
            sleepTimerRemainingMs = sleepRemaining,
            isEqualizerEnabled = eqEnabled,
            isBassBoostEnabled = bassEnabled,
            bassBoostStrength = bassStrength,
            equalizerLevels = eqLevels,
            equalizerFrequencies = eqFreqs,
            equalizerPresets = eqPresets,
            lyrics = lyrics,
            isLyricsLoading = lyricsLoading,
            isCasting = casting,
            castDeviceName = castDevice,
            audioOutput = output,
            castMessage = message,
            playbackError = playbackError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlayerUiState()
    )

    init {
        viewModelScope.launch {
            playbackManager.currentSong
                .collectLatest { song ->
                    if (song == null) {
                        _lyrics.value = emptyList()
                        return@collectLatest
                    }
                    _isLyricsLoading.value = true
                    _lyrics.value = lyricsRepository.getLyrics(song)
                    _isLyricsLoading.value = false
                }
        }
    }

    fun playPause() {
        playbackManager.playPause()
    }

    fun skipNext() {
        playbackManager.skipNext()
    }

    fun skipPrevious() {
        playbackManager.skipPrevious()
    }

    fun seekTo(fraction: Float) {
        playbackManager.seekTo(fraction)
    }

    fun seekToPosition(positionMs: Long) {
        playbackManager.seekToPosition(positionMs)
    }

    fun toggleShuffle() {
        playbackManager.toggleShuffle()
    }

    fun toggleRepeat() {
        playbackManager.toggleRepeat()
    }

    fun toggleSmartShuffle() {
        playbackManager.toggleSmartShuffle()
    }

    fun toggleFavorite() {
        val song = uiState.value.currentSong ?: return
        viewModelScope.launch {
            toggleFavoriteUseCase(song.id)
        }
    }

    // ── Playback speed ───────────────────────────────────────────────────────
    fun setPlaybackSpeed(speed: Float) {
        playbackManager.setPlaybackSpeed(speed)
    }

    // ── Sleep timer ──────────────────────────────────────────────────────────
    fun startSleepTimer(minutes: Int) {
        playbackManager.startSleepTimer(minutes)
    }

    fun cancelSleepTimer() {
        playbackManager.cancelSleepTimer()
    }

    // ── Crossfade ────────────────────────────────────────────────────────────
    fun setCrossfadeEnabled(enabled: Boolean) {
        playbackManager.setCrossfadeEnabled(enabled)
    }

    fun setCrossfadeDurationMs(durationMs: Int) {
        playbackManager.setCrossfadeDurationMs(durationMs)
    }

    // ── Queue editor ─────────────────────────────────────────────────────────
    fun removeFromQueue(index: Int) {
        playbackManager.removeFromQueue(index)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        playbackManager.moveQueueItem(fromIndex, toIndex)
    }

    fun playSongAtIndex(index: Int) {
        playbackManager.playSongAtIndex(index)
    }

    fun addToQueue(song: Song) {
        playbackManager.addToQueue(song)
    }

    fun playNext(song: Song) {
        playbackManager.playNext(song)
    }

    fun clearQueue(keepCurrent: Boolean = true) {
        playbackManager.clearQueue(keepCurrent)
    }

    fun shuffleQueue() {
        playbackManager.shuffleQueue()
    }

    // ── Equalizer & bass boost ───────────────────────────────────────────────
    fun toggleEqualizer(enabled: Boolean) {
        playbackManager.toggleEqualizer(enabled)
    }

    fun toggleBassBoost(enabled: Boolean) {
        playbackManager.toggleBassBoost(enabled)
    }

    fun setBassBoostStrength(strength: Short) {
        playbackManager.setBassBoostStrength(strength)
    }

    fun setBandLevel(bandIndex: Int, normalized: Float) {
        playbackManager.setBandLevel(bandIndex, normalized)
    }

    fun resetEqualizer() {
        playbackManager.resetEqualizer()
    }

    fun applyEqualizerPreset(presetIndex: Int) {
        playbackManager.applyEqualizerPreset(presetIndex)
    }

    // ── Playback error recovery ───────────────────────────────────────────────
    fun retryPlayback() {
        playbackManager.retryPlayback()
    }

    fun skipOnError() {
        playbackManager.skipOnError()
    }

    // ── Casting ───────────────────────────────────────────────────────────────
    fun onCastClicked() {
        val state = uiState.value
        if (state.isCasting) {
            castPlaybackManager.disconnect()
            playbackManager.play()
            _castMessage.value = "Stopped casting"
            return
        }
        val song = state.currentSong ?: return
        if (!song.isStream) {
            _castMessage.value = "Only online streams can be cast"
            return
        }
        val started = castPlaybackManager.castQueue(state.songs, state.currentSongIndex)
        if (started) {
            playbackManager.pause()
            _castMessage.value = "Casting to ${castPlaybackManager.castDeviceName.value ?: "Chromecast"}"
        } else {
            _castMessage.value = "No Chromecast device connected"
        }
    }

    fun clearCastMessage() {
        _castMessage.value = null
    }
}
