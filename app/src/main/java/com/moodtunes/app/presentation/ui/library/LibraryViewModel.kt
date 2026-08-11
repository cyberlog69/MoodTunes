package com.moodtunes.app.presentation.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodtunes.app.data.local.preferences.UserPreferencesRepository
import com.moodtunes.app.data.remote.OnlineStreamRepository
import com.moodtunes.app.domain.model.Playlist
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.usecase.AddSongToPlaylistUseCase
import com.moodtunes.app.domain.usecase.CreatePlaylistUseCase
import com.moodtunes.app.domain.usecase.GetAllSongsUseCase
import com.moodtunes.app.domain.usecase.GetFavoriteSongsUseCase
import com.moodtunes.app.domain.usecase.GetMostPlayedUseCase
import com.moodtunes.app.domain.usecase.GetPlaylistsUseCase
import com.moodtunes.app.domain.usecase.ToggleFavoriteUseCase
import com.moodtunes.app.data.remote.api.SubsonicApiService
import com.moodtunes.app.service.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumGroup(
    val name: String,
    val artist: String,
    val songs: List<Song>
)

data class LibraryUiState(
    val isLoading: Boolean = true,
    val isLoadingOnline: Boolean = false,
    val isLoadingServer: Boolean = false,
    val allSongs: List<Song> = emptyList(),
    val favoriteSongs: List<Song> = emptyList(),
    val mostPlayed: List<Song> = emptyList(),
    val onlineStreamSongs: List<Song> = emptyList(),
    val serverSongs: List<Song> = emptyList(),
    val serverError: String? = null,
    val playlists: List<Playlist> = emptyList(),
    val albums: List<AlbumGroup> = emptyList(),
    val artists: List<String> = emptyList(),
    val selectedAlbum: AlbumGroup? = null,
    val selectedArtist: String? = null,
    val searchQuery: String = "",
    val filteredSongs: List<Song> = emptyList(),
    val selectedTab: LibraryTab = LibraryTab.LOCAL,
    val selectedCategory: String = "Top Hits",
    val currentSongId: Long? = null,
    val isPlaying: Boolean = false
)

enum class LibraryTab(val label: String) {
    LOCAL("📁 Local"),
    ONLINE_STREAM("🌐 Online"),
    SERVER("🏠 Server"),
    FAVORITES("❤️ Favorites"),
    PLAYLISTS("📚 Playlists"),
    TOP_TRACKS("🔥 Top Tracks"),
    ALBUMS("💿 Albums"),
    ARTISTS("🎤 Artists")
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getAllSongsUseCase: GetAllSongsUseCase,
    private val getFavoriteSongsUseCase: GetFavoriteSongsUseCase,
    private val getMostPlayedUseCase: GetMostPlayedUseCase,
    private val getPlaylistsUseCase: GetPlaylistsUseCase,
    private val createPlaylistUseCase: CreatePlaylistUseCase,
    private val addSongToPlaylistUseCase: AddSongToPlaylistUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val playbackManager: PlaybackManager,
    private val onlineStreamRepository: OnlineStreamRepository,
    private val subsonicApiService: SubsonicApiService,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val tagEditorManager: com.moodtunes.app.data.local.mediastore.TagEditorManager
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
        observePlaylists()
        observeMostPlayed()
        loadOnlineStreamSongs()
    }

    private fun loadSongs() {
        viewModelScope.launch {
            val songs = getAllSongsUseCase()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    allSongs = songs,
                    filteredSongs = songs,
                    albums = groupByAlbum(songs),
                    artists = groupByArtist(songs)
                )
            }
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

    private fun observeMostPlayed() {
        viewModelScope.launch {
            getMostPlayedUseCase(limit = 100).collect { mostPlayed ->
                _uiState.update { it.copy(mostPlayed = mostPlayed) }
            }
        }
    }

    private fun observePlaylists() {
        viewModelScope.launch {
            getPlaylistsUseCase().collect { playlists ->
                _uiState.update { it.copy(playlists = playlists) }
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
                    _uiState.update {
                        it.copy(
                            searchQuery = query,
                            filteredSongs = filtered,
                            albums = groupByAlbum(filtered),
                            artists = groupByArtist(filtered)
                        )
                    }
                }
        }
    }

    private fun groupByAlbum(songs: List<Song>): List<AlbumGroup> =
        songs.groupBy { it.album to it.artist }
            .map { (key, group) -> AlbumGroup(name = key.first, artist = key.second, songs = group) }
            .sortedBy { it.name.lowercase() }

    private fun groupByArtist(songs: List<Song>): List<String> =
        songs.map { it.artist }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedBy { it.lowercase() }

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun onTabSelected(tab: LibraryTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        if (tab == LibraryTab.ONLINE_STREAM && _uiState.value.onlineStreamSongs.isEmpty()) {
            loadOnlineStreamSongs()
        } else if (tab == LibraryTab.SERVER && _uiState.value.serverSongs.isEmpty()) {
            loadServerSongs()
        }
    }

    fun loadServerSongs() {
        val settings = userPreferencesRepository.settings.value
        if (!settings.isNavidromeEnabled || settings.navidromeServerUrl.isBlank()) {
            _uiState.update {
                it.copy(
                    isLoadingServer = false,
                    serverSongs = emptyList(),
                    serverError = "Personal server is not configured. Go to Settings > Self-Hosted Music Server to connect."
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingServer = true, serverError = null) }
            try {
                val songs = subsonicApiService.getRandomSongs(
                    serverUrl = settings.navidromeServerUrl,
                    username = settings.navidromeUsername,
                    password = settings.navidromePassword,
                    size = 50
                )
                _uiState.update {
                    it.copy(
                        isLoadingServer = false,
                        serverSongs = songs,
                        serverError = if (songs.isEmpty()) "No tracks found on your server." else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingServer = false,
                        serverSongs = emptyList(),
                        serverError = "Failed to connect to server: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun onToggleFavorite(songId: Long) {
        viewModelScope.launch {
            toggleFavoriteUseCase(songId)
            loadSongs()
        }
    }

    fun onAlbumSelected(album: AlbumGroup) {
        _uiState.update { it.copy(selectedAlbum = album) }
    }

    fun onArtistSelected(artist: String) {
        _uiState.update { it.copy(selectedArtist = artist) }
    }

    fun clearGroupSelection() {
        _uiState.update { it.copy(selectedAlbum = null, selectedArtist = null) }
    }

    fun onCreatePlaylist(name: String, song: Song? = null) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = createPlaylistUseCase(name)
            if (song != null) {
                addSongToPlaylistUseCase(id, song)
            }
        }
    }

    fun onAddToPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch {
            addSongToPlaylistUseCase(playlistId, song)
        }
    }

    fun playNext(song: Song) {
        playbackManager.playNext(song)
    }

    fun addToQueue(song: Song) {
        playbackManager.addToQueue(song)
    }

    fun updateSongTags(song: Song, title: String, artist: String, album: String, genre: String?, artUri: Uri?) {
        viewModelScope.launch {
            val result = tagEditorManager.saveSongTags(song, title, artist, album, genre, artUri)
            if (result.isSuccess) {
                loadSongs()
            }
        }
    }

    fun onSongSelected(song: Song) {
        val state = _uiState.value
        val group = when (state.selectedTab) {
            LibraryTab.LOCAL -> state.filteredSongs
            LibraryTab.ONLINE_STREAM -> state.onlineStreamSongs
            LibraryTab.SERVER -> state.serverSongs
            LibraryTab.FAVORITES -> state.favoriteSongs
            LibraryTab.ALBUMS -> state.selectedAlbum?.songs ?: state.filteredSongs
            LibraryTab.ARTISTS -> state.selectedArtist?.let { artist ->
                state.filteredSongs.filter { it.artist == artist }
            } ?: state.filteredSongs
            LibraryTab.TOP_TRACKS -> state.mostPlayed
            LibraryTab.PLAYLISTS -> emptyList()
        }
        if (group.isEmpty()) return
        val index = group.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        playbackManager.playSongs(group, index, mood = null)
    }
}
