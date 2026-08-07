package com.moodtunes.app.presentation.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodtunes.app.data.local.preferences.MusicLanguage
import com.moodtunes.app.data.local.preferences.UserPreferencesRepository
import com.moodtunes.app.domain.model.MoodEntry
import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.usecase.GetForYouSongsUseCase
import com.moodtunes.app.domain.usecase.GetRecentlyPlayedUseCase
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
    val selectedLanguage: MusicLanguage = MusicLanguage.ALL,
    val moodSongs: List<Song> = emptyList(),
    val recentlyPlayed: List<Song> = emptyList(),
    val forYou: List<Song> = emptyList(),
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getSongsByMoodUseCase: GetSongsByMoodUseCase,
    private val saveMoodHistoryUseCase: SaveMoodHistoryUseCase,
    private val getRecentlyPlayedUseCase: GetRecentlyPlayedUseCase,
    private val getForYouSongsUseCase: GetForYouSongsUseCase,
    private val playbackManager: PlaybackManager,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = combine(
        _uiState,
        playbackManager.currentSong,
        playbackManager.isPlaying,
        playbackManager.currentMood,
        preferencesRepository.settings.map { it.preferredLanguage }.distinctUntilChanged()
    ) { state, song, playing, mood, language ->
        state.copy(
            currentSong = song,
            isPlaying = playing,
            selectedMood = mood ?: state.selectedMood,
            selectedLanguage = language
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    private var sessionStartTime: Long = 0L

    init {
        viewModelScope.launch {
            getRecentlyPlayedUseCase(20).collect { songs ->
                _uiState.update { it.copy(recentlyPlayed = songs) }
            }
        }
        viewModelScope.launch {
            runCatching { getForYouSongsUseCase(20) }
                .onSuccess { recs -> _uiState.update { it.copy(forYou = recs) } }
        }
    }

    fun onLanguageSelected(language: MusicLanguage) {
        preferencesRepository.updatePreferredLanguage(language)
        val currentMood = uiState.value.selectedMood
        if (currentMood != null) {
            onMoodSelected(currentMood)
        }
    }

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

    fun playForYou(index: Int) {
        val songs = uiState.value.forYou
        if (index in songs.indices) {
            playbackManager.playSongs(songs, index)
        }
    }

    fun playRecentlyPlayed(index: Int) {
        val songs = uiState.value.recentlyPlayed
        if (index in songs.indices) {
            playbackManager.playSongs(songs, index)
        }
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
