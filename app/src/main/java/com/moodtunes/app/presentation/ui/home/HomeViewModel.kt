package com.moodtunes.app.presentation.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodtunes.app.domain.model.MoodEntry
import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.usecase.GetSongsByMoodUseCase
import com.moodtunes.app.domain.usecase.SaveMoodHistoryUseCase
import com.moodtunes.app.service.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val selectedMood: MoodType? = null,
    val moodSongs: List<Song> = emptyList(),
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getSongsByMoodUseCase: GetSongsByMoodUseCase,
    private val saveMoodHistoryUseCase: SaveMoodHistoryUseCase,
    private val playbackManager: PlaybackManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = combine(
        _uiState,
        playbackManager.currentSong,
        playbackManager.isPlaying,
        playbackManager.currentMood
    ) { state, song, playing, mood ->
        state.copy(
            currentSong = song,
            isPlaying = playing,
            selectedMood = mood ?: state.selectedMood
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    private var sessionStartTime: Long = 0L

    fun onMoodSelected(mood: MoodType) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    selectedMood = mood,
                    error = null
                )
            }
            try {
                val songs = getSongsByMoodUseCase(mood)
                sessionStartTime = System.currentTimeMillis()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        moodSongs = songs
                    )
                }
                if (songs.isNotEmpty()) {
                    playbackManager.playSongs(songs, 0, mood)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Couldn't load songs: ${e.message}"
                    )
                }
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

    fun saveMoodSession() {
        val state = uiState.value
        val mood = state.selectedMood ?: return
        val durationMs = System.currentTimeMillis() - sessionStartTime
        if (durationMs < 10_000) return // ignore very short sessions

        viewModelScope.launch {
            saveMoodHistoryUseCase(
                MoodEntry(
                    moodType = mood,
                    timestamp = System.currentTimeMillis(),
                    songCount = state.moodSongs.size,
                    durationListenedMs = durationMs
                )
            )
        }
    }
}
