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
import com.moodtunes.app.service.ReverbPreset
import com.moodtunes.app.service.VisualizerManager
import com.moodtunes.app.service.VisualizerMode
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
    val isVirtualizerEnabled: Boolean = false,
    val virtualizerStrength: Short = 0,
    val reverbPreset: ReverbPreset = ReverbPreset.NONE,
    val isSkipSilenceEnabled: Boolean = false,
    val visualizerMode: VisualizerMode = VisualizerMode.BARS,
    val fftBands: FloatArray = FloatArray(32) { 0f },
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
    private val visualizerManager: VisualizerManager,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val lyricsRepository: LyricsRepository,
    private val castPlaybackManager: CastPlaybackManager,
    private val audioOutputMonitor: AudioOutputMonitor
) : ViewModel() {

    private val _lyrics = MutableStateFlow<List<LyricsLine>>(emptyList())
    private val _isLyricsLoading = MutableStateFlow(false)
    private val _castMessage = MutableStateFlow<String?>(null)

    // Combine playback, effects, visualizer and casting states
    private val playbackCoreState = combine(
        playbackManager.playlist,
        playbackManager.currentSong,
        playbackManager.isPlaying,
        playbackManager.currentPositionMs,
        playbackManager.durationMs,
        playbackManager.currentMood,
        playbackManager.isShuffleEnabled,
        playbackManager.repeatMode
    ) { playlist, song, isPlaying, pos, dur, mood, shuffle, repeatModeInt ->
        val index = playlist.indexOfFirst { it.id == song?.id }.coerceAtLeast(0)
        val repeatMode = when (repeatModeInt) {
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            else -> RepeatMode.OFF
        }
        PlaybackCore(playlist, index, song, isPlaying, pos, dur, mood, shuffle, repeatMode)
    }

    private val effectsState = combine(
        audioEffectsManager.isEqualizerEnabled,
        audioEffectsManager.isBassBoostEnabled,
        audioEffectsManager.bassBoostStrength,
        audioEffectsManager.isVirtualizerEnabled,
        audioEffectsManager.virtualizerStrength,
        audioEffectsManager.reverbPreset,
        audioEffectsManager.bandLevels,
        audioEffectsManager.bandFrequencies,
        audioEffectsManager.presets
    ) { eqEn, bbEn, bbStr, virtEn, virtStr, rev, levels, freqs, presets ->
        EffectsState(eqEn, bbEn, bbStr, virtEn, virtStr, rev, levels, freqs, presets)
    }

    val uiState: StateFlow<PlayerUiState> = combine(
        playbackCoreState,
        effectsState,
        visualizerManager.currentMode,
        visualizerManager.fftBands,
        playbackManager.isSkipSilenceEnabled,
        playbackManager.playbackSpeed,
        playbackManager.isSmartShuffleEnabled,
        playbackManager.isCrossfadeEnabled,
        playbackManager.crossfadeDurationMs,
        playbackManager.sleepTimerRemainingMs,
        _lyrics,
        _isLyricsLoading,
        castPlaybackManager.isCasting,
        castPlaybackManager.castDeviceName,
        audioOutputMonitor.output,
        _castMessage,
        playbackManager.playbackError
    ) { args ->
        val core = args[0] as PlaybackCore
        val fx = args[1] as EffectsState
        val vizMode = args[2] as VisualizerMode
        val bands = args[3] as FloatArray
        val skipSilence = args[4] as Boolean
        val speed = args[5] as Float
        val smartShuffle = args[6] as Boolean
        val crossfade = args[7] as Boolean
        val crossfadeDur = args[8] as Int
        val sleepRemaining = args[9] as Long?
        @Suppress("UNCHECKED_CAST")
        val lyrics = args[10] as List<LyricsLine>
        val lyricsLoading = args[11] as Boolean
        val casting = args[12] as Boolean
        val castDevice = args[13] as String?
        val output = args[14] as AudioOutputInfo?
        val message = args[15] as String?
        val playbackError = args[16] as PlaybackError?

        PlayerUiState(
            songs = core.playlist,
            currentSongIndex = core.index,
            currentSong = core.song,
            isPlaying = core.isPlaying,
            currentPositionMs = core.pos,
            durationMs = core.dur,
            isShuffleEnabled = core.shuffle,
            repeatMode = core.repeatMode,
            selectedMood = core.mood,
            playbackSpeed = speed,
            isSmartShuffleEnabled = smartShuffle,
            isCrossfadeEnabled = crossfade,
            crossfadeDurationMs = crossfadeDur,
            sleepTimerRemainingMs = sleepRemaining,
            isEqualizerEnabled = fx.eqEnabled,
            isBassBoostEnabled = fx.bassEnabled,
            bassBoostStrength = fx.bassStrength,
            isVirtualizerEnabled = fx.virtEnabled,
            virtualizerStrength = fx.virtStrength,
            reverbPreset = fx.reverbPreset,
            isSkipSilenceEnabled = skipSilence,
            visualizerMode = vizMode,
            fftBands = bands,
            equalizerLevels = fx.levels,
            equalizerFrequencies = fx.freqs,
            equalizerPresets = fx.presets,
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

        // Trigger visualizer procedural updates when playing
        viewModelScope.launch {
            playbackManager.isPlaying.collectLatest { playing ->
                visualizerManager.startProceduralFallback(playing)
            }
        }
    }

    fun playPause() = playbackManager.playPause()
    fun skipNext() = playbackManager.skipNext()
    fun skipPrevious() = playbackManager.skipPrevious()
    fun seekTo(fraction: Float) = playbackManager.seekTo(fraction)

    fun toggleShuffle() = playbackManager.toggleShuffle()
    fun cycleRepeatMode() = playbackManager.cycleRepeatMode()
    fun toggleSmartShuffle() = playbackManager.toggleSmartShuffle()

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch {
            val isFav = toggleFavoriteUseCase(songId)
            playbackManager.updateFavorite(songId, isFav)
        }
    }

    fun setPlaybackSpeed(speed: Float) = playbackManager.setPlaybackSpeed(speed)
    fun setPlaybackPitch(pitch: Float) = playbackManager.setPlaybackPitch(pitch)

    fun startSleepTimer(minutes: Int) = playbackManager.startSleepTimer(minutes)
    fun cancelSleepTimer() = playbackManager.cancelSleepTimer()

    fun setCrossfadeEnabled(enabled: Boolean) = playbackManager.setCrossfadeEnabled(enabled)
    fun setCrossfadeDurationMs(durationMs: Int) = playbackManager.setCrossfadeDurationMs(durationMs)
    fun setSkipSilenceEnabled(enabled: Boolean) = playbackManager.setSkipSilenceEnabled(enabled)

    // ── Visualizer Controls ──────────────────────────────────────────────────
    fun cycleVisualizerMode() = visualizerManager.cycleMode()
    fun setVisualizerMode(mode: VisualizerMode) = visualizerManager.setMode(mode)

    // ── Queue Editor ─────────────────────────────────────────────────────────
    fun removeFromQueue(index: Int) = playbackManager.removeFromQueue(index)
    fun moveQueueItem(fromIndex: Int, toIndex: Int) = playbackManager.moveQueueItem(fromIndex, toIndex)
    fun playSongAtIndex(index: Int) = playbackManager.playSongAtIndex(index)
    fun addToQueue(song: Song) = playbackManager.addToQueue(song)
    fun playNext(song: Song) = playbackManager.playNext(song)
    fun clearQueue(keepCurrent: Boolean = true) = playbackManager.clearQueue(keepCurrent)
    fun shuffleQueue() = playbackManager.shuffleQueue()

    // ── Equalizer, Bass Boost, 3D Virtualizer & Reverb ──────────────────────
    fun toggleEqualizer(enabled: Boolean) = playbackManager.toggleEqualizer(enabled)
    fun toggleBassBoost(enabled: Boolean) = playbackManager.toggleBassBoost(enabled)
    fun setBassBoostStrength(strength: Short) = playbackManager.setBassBoostStrength(strength)
    fun toggleVirtualizer(enabled: Boolean) = playbackManager.toggleVirtualizer(enabled)
    fun setVirtualizerStrength(strength: Short) = playbackManager.setVirtualizerStrength(strength)
    fun setReverbPreset(preset: ReverbPreset) = playbackManager.setReverbPreset(preset)
    fun setBandLevel(bandIndex: Int, normalized: Float) = playbackManager.setBandLevel(bandIndex, normalized)
    fun resetEqualizer() = playbackManager.resetEqualizer()
    fun applyEqualizerPreset(presetIndex: Int) = playbackManager.applyEqualizerPreset(presetIndex)

    // ── Playback Error Recovery ──────────────────────────────────────────────
    fun retryPlayback() = playbackManager.retryPlayback()
    fun skipOnError() = playbackManager.skipOnError()

    // ── Casting ──────────────────────────────────────────────────────────────
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

    private data class PlaybackCore(
        val playlist: List<Song>,
        val index: Int,
        val song: Song?,
        val isPlaying: Boolean,
        val pos: Long,
        val dur: Long,
        val mood: MoodType?,
        val shuffle: Boolean,
        val repeatMode: RepeatMode
    )

    private data class EffectsState(
        val eqEnabled: Boolean,
        val bassEnabled: Boolean,
        val bassStrength: Short,
        val virtEnabled: Boolean,
        val virtStrength: Short,
        val reverbPreset: ReverbPreset,
        val levels: List<Float>,
        val freqs: List<Int>,
        val presets: List<String>
    )
}
