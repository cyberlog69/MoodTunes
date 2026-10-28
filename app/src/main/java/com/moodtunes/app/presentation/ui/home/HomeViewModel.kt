package com.moodtunes.app.presentation.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodtunes.app.data.local.download.SongDownloadManager
import com.moodtunes.app.data.local.preferences.MusicLanguage
import com.moodtunes.app.data.local.preferences.UserPreferencesRepository
import com.moodtunes.app.domain.model.MoodEntry
import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.model.Playlist
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.repository.IMoodRepository
import com.moodtunes.app.domain.repository.IMusicRepository
import com.moodtunes.app.domain.repository.IPlaylistRepository
import com.moodtunes.app.domain.usecase.AddSongToPlaylistUseCase
import com.moodtunes.app.domain.usecase.CreatePlaylistUseCase
import com.moodtunes.app.domain.usecase.GetForYouSongsUseCase
import com.moodtunes.app.domain.usecase.GetRecentlyPlayedUseCase
import com.moodtunes.app.domain.usecase.GetSongsByMoodUseCase
import com.moodtunes.app.domain.usecase.SaveMoodHistoryUseCase
import com.moodtunes.app.domain.usecase.ToggleFavoriteUseCase
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
    val favoriteSongs: List<Song> = emptyList(),
    val downloadedSongsCount: Int = 0,
    val totalListeningTimeFormatted: String = "3h 52m",
    val topMood: MoodType? = null,
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val userPlaylists: List<Playlist> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getSongsByMoodUseCase: GetSongsByMoodUseCase,
    private val saveMoodHistoryUseCase: SaveMoodHistoryUseCase,
    private val getRecentlyPlayedUseCase: GetRecentlyPlayedUseCase,
    private val getForYouSongsUseCase: GetForYouSongsUseCase,
    private val playlistRepository: IPlaylistRepository,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val addSongToPlaylistUseCase: AddSongToPlaylistUseCase,
    private val createPlaylistUseCase: CreatePlaylistUseCase,
    private val moodRepository: IMoodRepository,
    private val musicRepository: IMusicRepository,
    private val playbackManager: PlaybackManager,
    private val preferencesRepository: UserPreferencesRepository,
    private val downloadManager: SongDownloadManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = combine(
        _uiState,
        playbackManager.currentSong,
        playbackManager.isPlaying,
        playbackManager.currentMood,
        combine(
            preferencesRepository.settings.map { it.preferredLanguage }.distinctUntilChanged(),
            playlistRepository.getPlaylists()
        ) { language, playlists -> language to playlists }
    ) { state, song, playing, mood, (language, playlists) ->
        state.copy(
            currentSong = song,
            isPlaying = playing,
            selectedMood = mood,
            selectedLanguage = language,
            userPlaylists = playlists
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    private var sessionStartTime: Long = 0L

    init {
        // Collect recently played
        viewModelScope.launch {
            getRecentlyPlayedUseCase(20).collect { songs ->
                _uiState.update { it.copy(recentlyPlayed = songs) }
            }
        }

        // Collect For You songs
        viewModelScope.launch {
            runCatching { getForYouSongsUseCase(20) }
                .onSuccess { recs -> _uiState.update { it.copy(forYou = recs) } }
        }

        // Collect favorite songs
        viewModelScope.launch {
            musicRepository.getFavoriteSongs().collect { favs ->
                _uiState.update { it.copy(favoriteSongs = favs) }
            }
        }

        // Collect downloaded songs count
        viewModelScope.launch {
            downloadManager.downloadedSongIds.collect { downloadedIds ->
                _uiState.update { it.copy(downloadedSongsCount = downloadedIds.size) }
            }
        }

        // Fetch Replay statistics
        refreshReplayStats()
    }

    private fun refreshReplayStats() {
        viewModelScope.launch {
            runCatching {
                val totalMs = moodRepository.getTotalListeningTimeMs()
                val topMood = moodRepository.getTopMood()
                val formattedTime = formatListeningTime(totalMs)
                _uiState.update {
                    it.copy(
                        totalListeningTimeFormatted = formattedTime,
                        topMood = topMood
                    )
                }
            }
        }
    }

    private fun formatListeningTime(totalMs: Long): String {
        if (totalMs <= 0L) return "3h 52m"
        val totalMinutes = totalMs / 60_000L
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
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

    fun clearMood() {
        _uiState.update { it.copy(selectedMood = null, moodSongs = emptyList()) }
        playbackManager.clearMood()
    }

    fun playSong(song: Song) {
        _uiState.update { it.copy(selectedMood = null) }
        playbackManager.clearMood()

        val quickPicks = (uiState.value.forYou.ifEmpty { uiState.value.recentlyPlayed }).take(12)
        val qIndex = quickPicks.indexOfFirst { it.id == song.id }
        if (qIndex >= 0) {
            playbackManager.playSongs(quickPicks, qIndex, mood = null)
            return
        }

        val rIndex = uiState.value.recentlyPlayed.indexOfFirst { it.id == song.id }
        if (rIndex >= 0) {
            playbackManager.playSongs(uiState.value.recentlyPlayed, rIndex, mood = null)
            return
        }

        playbackManager.playSongs(listOf(song), 0, mood = null)
    }

    fun playForYou(index: Int) {
        _uiState.update { it.copy(selectedMood = null) }
        playbackManager.clearMood()
        val songs = uiState.value.forYou
        if (index in songs.indices) {
            playbackManager.playSongs(songs, index, mood = null)
        }
    }

    fun playRecentlyPlayed(index: Int) {
        _uiState.update { it.copy(selectedMood = null) }
        playbackManager.clearMood()
        val songs = uiState.value.recentlyPlayed
        if (index in songs.indices) {
            playbackManager.playSongs(songs, index, mood = null)
        }
    }

    fun playFavoriteSongs() {
        _uiState.update { it.copy(selectedMood = null) }
        playbackManager.clearMood()
        val songs = uiState.value.favoriteSongs
        if (songs.isNotEmpty()) {
            playbackManager.playSongs(songs, 0, mood = null)
        }
    }

    fun playNext(song: Song) {
        playbackManager.playNext(song)
    }

    fun addToQueue(song: Song) {
        playbackManager.addToQueue(song)
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            toggleFavoriteUseCase(song.id)
        }
    }

    fun addToPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch {
            addSongToPlaylistUseCase(playlistId, song)
        }
    }

    fun createPlaylist(name: String, song: Song? = null) {
        viewModelScope.launch {
            val id = createPlaylistUseCase(name)
            if (song != null) {
                addSongToPlaylistUseCase(id, song)
            }
        }
    }

    fun skipNext() {
        playbackManager.skipNext()
    }

    fun skipPrevious() {
        playbackManager.skipPrevious()
    }

    fun downloadSong(song: Song) = downloadManager.downloadSong(song)
    fun isDownloaded(songId: Long): Boolean = downloadManager.isDownloaded(songId)

    fun saveMoodSession() {
        val state = uiState.value
        val mood = state.selectedMood ?: return
        val durationMs = System.currentTimeMillis() - sessionStartTime
        if (durationMs < 10_000) return

        viewModelScope.launch {
            saveMoodHistoryUseCase(
                MoodEntry(
                    moodType = mood,
                    timestamp = System.currentTimeMillis(),
                    songCount = state.moodSongs.size,
                    durationListenedMs = durationMs
                )
            )
            refreshReplayStats()
        }
    }
}
