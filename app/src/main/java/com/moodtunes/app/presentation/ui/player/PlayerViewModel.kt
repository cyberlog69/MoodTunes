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

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        // Core playback state collections
        viewModelScope.launch {
            playbackManager.playlist.collectLatest { list ->
                _uiState.update { current ->
                    val idx = list.indexOfFirst { it.id == current.currentSong?.id }.coerceAtLeast(0)
                    current.copy(songs = list, currentSongIndex = idx)
                }
            }
        }

        viewModelScope.launch {
            playbackManager.currentSong.collectLatest { song ->
                _uiState.update { current ->
                    val idx = current.songs.indexOfFirst { it.id == song?.id }.coerceAtLeast(0)
                    current.copy(currentSong = song, currentSongIndex = idx)
                }
                if (song == null) {
                    _uiState.update { it.copy(lyrics = emptyList(), isLyricsLoading = false) }
                } else {
                    _uiState.update { it.copy(isLyricsLoading = true) }
                    val loaded = lyricsRepository.getLyrics(song)
                    _uiState.update { it.copy(lyrics = loaded, isLyricsLoading = false) }
                }
            }
        }

        viewModelScope.launch {
            playbackManager.isPlaying.collectLatest { playing ->
                _uiState.update { it.copy(isPlaying = playing) }
                visualizerManager.startProceduralFallback(playing)
            }
        }

        viewModelScope.launch {
            playbackManager.currentPositionMs.collectLatest { pos ->
                _uiState.update { it.copy(currentPositionMs = pos) }
            }
        }

        viewModelScope.launch {
            playbackManager.durationMs.collectLatest { dur ->
                _uiState.update { it.copy(durationMs = dur) }
            }
        }

        viewModelScope.launch {
            playbackManager.currentMood.collectLatest { mood ->
                _uiState.update { it.copy(selectedMood = mood) }
            }
        }

        viewModelScope.launch {
            playbackManager.isShuffleEnabled.collectLatest { shuffle ->
                _uiState.update { it.copy(isShuffleEnabled = shuffle) }
            }
        }

        viewModelScope.launch {
            playbackManager.repeatMode.collectLatest { modeInt ->
                val mode = when (modeInt) {
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    else -> RepeatMode.OFF
                }
                _uiState.update { it.copy(repeatMode = mode) }
            }
        }

        viewModelScope.launch {
            playbackManager.playbackSpeed.collectLatest { speed ->
                _uiState.update { it.copy(playbackSpeed = speed) }
            }
        }

        viewModelScope.launch {
            playbackManager.isSmartShuffleEnabled.collectLatest { ss ->
                _uiState.update { it.copy(isSmartShuffleEnabled = ss) }
            }
        }

        viewModelScope.launch {
            playbackManager.isCrossfadeEnabled.collectLatest { cf ->
                _uiState.update { it.copy(isCrossfadeEnabled = cf) }
            }
        }

        viewModelScope.launch {
            playbackManager.crossfadeDurationMs.collectLatest { dur ->
                _uiState.update { it.copy(crossfadeDurationMs = dur) }
            }
        }

        viewModelScope.launch {
            playbackManager.sleepTimerRemainingMs.collectLatest { sleep ->
                _uiState.update { it.copy(sleepTimerRemainingMs = sleep) }
            }
        }

        viewModelScope.launch {
            playbackManager.isSkipSilenceEnabled.collectLatest { skip ->
                _uiState.update { it.copy(isSkipSilenceEnabled = skip) }
            }
        }

        viewModelScope.launch {
            playbackManager.playbackError.collectLatest { err ->
                _uiState.update { it.copy(playbackError = err) }
            }
        }

        // Audio effects collections
        viewModelScope.launch {
            audioEffectsManager.isEqualizerEnabled.collectLatest { eq ->
                _uiState.update { it.copy(isEqualizerEnabled = eq) }
            }
        }

        viewModelScope.launch {
            audioEffectsManager.isBassBoostEnabled.collectLatest { bb ->
                _uiState.update { it.copy(isBassBoostEnabled = bb) }
            }
        }

        viewModelScope.launch {
            audioEffectsManager.bassBoostStrength.collectLatest { str ->
                _uiState.update { it.copy(bassBoostStrength = str) }
            }
        }

        viewModelScope.launch {
            audioEffectsManager.isVirtualizerEnabled.collectLatest { virt ->
                _uiState.update { it.copy(isVirtualizerEnabled = virt) }
            }
        }

        viewModelScope.launch {
            audioEffectsManager.virtualizerStrength.collectLatest { str ->
                _uiState.update { it.copy(virtualizerStrength = str) }
            }
        }

        viewModelScope.launch {
            audioEffectsManager.reverbPreset.collectLatest { rev ->
                _uiState.update { it.copy(reverbPreset = rev) }
            }
        }

        viewModelScope.launch {
            audioEffectsManager.bandLevels.collectLatest { levels ->
                _uiState.update { it.copy(equalizerLevels = levels) }
            }
        }

        viewModelScope.launch {
            audioEffectsManager.bandFrequencies.collectLatest { freqs ->
                _uiState.update { it.copy(equalizerFrequencies = freqs) }
            }
        }

        viewModelScope.launch {
            audioEffectsManager.presets.collectLatest { presets ->
                _uiState.update { it.copy(equalizerPresets = presets) }
            }
        }

        // Visualizer collections
        viewModelScope.launch {
            visualizerManager.currentMode.collectLatest { mode ->
                _uiState.update { it.copy(visualizerMode = mode) }
            }
        }

        viewModelScope.launch {
            visualizerManager.fftBands.collectLatest { bands ->
                _uiState.update { it.copy(fftBands = bands) }
            }
        }

        // Casting and Audio Output
        viewModelScope.launch {
            castPlaybackManager.isCasting.collectLatest { casting ->
                _uiState.update { it.copy(isCasting = casting) }
            }
        }

        viewModelScope.launch {
            castPlaybackManager.castDeviceName.collectLatest { name ->
                _uiState.update { it.copy(castDeviceName = name) }
            }
        }

        viewModelScope.launch {
            audioOutputMonitor.output.collectLatest { output ->
                _uiState.update { it.copy(audioOutput = output) }
            }
        }
    }

    fun playPause() = playbackManager.playPause()
    fun skipNext() = playbackManager.skipNext()
    fun skipPrevious() = playbackManager.skipPrevious()
    fun seekTo(fraction: Float) = playbackManager.seekTo(fraction)
    fun seekToPosition(positionMs: Long) = playbackManager.seekToPosition(positionMs)

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
            _uiState.update { it.copy(castMessage = "Stopped casting") }
            return
        }
        val song = state.currentSong ?: return
        if (!song.isStream) {
            _uiState.update { it.copy(castMessage = "Only online streams can be cast") }
            return
        }
        val started = castPlaybackManager.castQueue(state.songs, state.currentSongIndex)
        if (started) {
            playbackManager.pause()
            _uiState.update { it.copy(castMessage = "Casting to ${castPlaybackManager.castDeviceName.value ?: "Chromecast"}") }
        } else {
            _uiState.update { it.copy(castMessage = "No Chromecast device connected") }
        }
    }

    fun clearCastMessage() {
        _uiState.update { it.copy(castMessage = null) }
    }
}
