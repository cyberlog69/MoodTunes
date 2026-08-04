package com.moodtunes.app.presentation.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.usecase.ToggleFavoriteUseCase
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
    val selectedMood: MoodType? = null
) {
    val progress: Float get() = if (durationMs > 0) currentPositionMs / durationMs.toFloat() else 0f
}

enum class RepeatMode { OFF, ONE, ALL }

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackManager: PlaybackManager,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase
) : ViewModel() {

    val uiState: StateFlow<PlayerUiState> = combine(
        playbackManager.playlist,
        playbackManager.currentSong,
        playbackManager.isPlaying,
        playbackManager.currentPositionMs,
        playbackManager.durationMs,
        playbackManager.currentMood,
        playbackManager.isShuffleEnabled,
        playbackManager.repeatMode
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
            selectedMood = mood
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlayerUiState()
    )

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

    fun toggleShuffle() {
        playbackManager.toggleShuffle()
    }

    fun toggleRepeat() {
        playbackManager.toggleRepeat()
    }

    fun toggleFavorite() {
        val song = uiState.value.currentSong ?: return
        viewModelScope.launch {
            toggleFavoriteUseCase(song.id)
        }
    }
}
