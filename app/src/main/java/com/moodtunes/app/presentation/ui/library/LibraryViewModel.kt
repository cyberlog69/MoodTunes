package com.moodtunes.app.presentation.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodtunes.app.data.local.preferences.UserPreferencesRepository
import com.moodtunes.app.data.remote.OnlineStreamRepository
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.usecase.GetAllSongsUseCase
import com.moodtunes.app.domain.usecase.GetFavoriteSongsUseCase
import com.moodtunes.app.domain.usecase.ToggleFavoriteUseCase
import com.moodtunes.app.service.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val isLoading: Boolean = true,
    val isLoadingOnline: Boolean = false,
    val allSongs: List<Song> = emptyList(),
    val favoriteSongs: List<Song> = emptyList(),
    val onlineStreamSongs: List<Song> = emptyList(),
    val searchQuery: String = "",
    val filteredSongs: List<Song> = emptyList(),
    val selectedTab: LibraryTab = LibraryTab.LOCAL,
    val selectedCategory: String = "Top Hits",
    val currentSongId: Long? = null,
    val isPlaying: Boolean = false
)

enum class LibraryTab(val label: String) {
    LOCAL("📁 Local Files"),
    ONLINE_STREAM("🌐 Online Stream"),
    FAVORITES("❤️ Favorites")
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getAllSongsUseCase: GetAllSongsUseCase,
    private val getFavoriteSongsUseCase: GetFavoriteSongsUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val playbackManager: PlaybackManager,
    private val onlineStreamRepository: OnlineStreamRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = combine(
        _uiState,
        playbackManager.currentSong,
        playbackManager.isPlaying
    ) { state, song, playing ->
        state.copy(
            currentSongId = song?.id,
            isPlaying = playing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState()
    )

    private val searchQuery = MutableStateFlow("")

    init {
        loadSongs()
        observeFavorites()
        observeSearch()
        loadOnlineStreamSongs()
    }

    private fun loadSongs() {
        viewModelScope.launch {
            val songs = getAllSongsUseCase()
            _uiState.update { it.copy(isLoading = false, allSongs = songs, filteredSongs = songs) }
        }
    }

    fun loadOnlineStreamSongs(category: String = _uiState.value.selectedCategory) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingOnline = true, selectedCategory = category) }
            try {
                val languages = userPreferencesRepository.settings.value.preferredLanguages
                val streamTracks = onlineStreamRepository.getGeneralTrendingSongs(languages, category, limit = 16)
                _uiState.update { it.copy(isLoadingOnline = false, onlineStreamSongs = streamTracks) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingOnline = false) }
            }
        }
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            getFavoriteSongsUseCase().collect { favorites ->
                _uiState.update { it.copy(favoriteSongs = favorites) }
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeSearch() {
        viewModelScope.launch {
            searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .collect { query ->
                    val all = _uiState.value.allSongs
                    val filtered = if (query.isBlank()) all
                    else all.filter { song ->
                        song.title.contains(query, ignoreCase = true) ||
                                song.artist.contains(query, ignoreCase = true) ||
                                song.album.contains(query, ignoreCase = true)
                    }
                    _uiState.update { it.copy(searchQuery = query, filteredSongs = filtered) }
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun onTabSelected(tab: LibraryTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        if (tab == LibraryTab.ONLINE_STREAM && _uiState.value.onlineStreamSongs.isEmpty()) {
            loadOnlineStreamSongs()
        }
    }

    fun onToggleFavorite(songId: Long) {
        viewModelScope.launch {
            toggleFavoriteUseCase(songId)
            loadSongs()
        }
    }

    fun onSongSelected(song: Song) {
        val displayed = when (_uiState.value.selectedTab) {
            LibraryTab.LOCAL -> _uiState.value.filteredSongs
            LibraryTab.ONLINE_STREAM -> _uiState.value.onlineStreamSongs
            LibraryTab.FAVORITES -> _uiState.value.favoriteSongs
        }
        val index = displayed.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        playbackManager.playSongs(displayed, index, mood = null)
    }
}
