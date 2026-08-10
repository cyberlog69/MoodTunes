package com.moodtunes.app.presentation.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodtunes.app.data.local.preferences.MusicLanguage
import com.moodtunes.app.data.local.preferences.UserPreferencesRepository
import com.moodtunes.app.data.remote.OnlineStreamRepository
import com.moodtunes.app.data.remote.api.SubsonicApiService
import com.moodtunes.app.domain.model.Playlist
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.repository.IMusicRepository
import com.moodtunes.app.domain.repository.IPlaylistRepository
import com.moodtunes.app.domain.usecase.AddSongToPlaylistUseCase
import com.moodtunes.app.domain.usecase.CreatePlaylistUseCase
import com.moodtunes.app.domain.usecase.ToggleFavoriteUseCase
import com.moodtunes.app.service.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchFilter(val displayName: String) {
    ALL("All"),
    SONGS("Songs"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    PLAYLISTS("Playlists"),
    ONLINE("Online Streams")
}

data class ArtistGroup(
    val artistName: String,
    val songs: List<Song>
)

data class AlbumGroup(
    val albumName: String,
    val artistName: String,
    val songs: List<Song>
)

data class SearchUiState(
    val query: String = "",
    val selectedFilter: SearchFilter = SearchFilter.ALL,
    val localSongs: List<Song> = emptyList(),
    val onlineSongs: List<Song> = emptyList(),
    val matchedPlaylists: List<Playlist> = emptyList(),
    val artists: List<ArtistGroup> = emptyList(),
    val albums: List<AlbumGroup> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val isSearchingLocal: Boolean = false,
    val isSearchingOnline: Boolean = false,
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val userPlaylists: List<Playlist> = emptyList()
) {
    val isEmpty: Boolean
        get() = query.isNotBlank() && !isSearchingLocal && !isSearchingOnline &&
                localSongs.isEmpty() && onlineSongs.isEmpty() && matchedPlaylists.isEmpty() && artists.isEmpty() && albums.isEmpty()
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val musicRepository: IMusicRepository,
    private val playlistRepository: IPlaylistRepository,
    private val onlineStreamRepository: OnlineStreamRepository,
    private val subsonicApiService: SubsonicApiService,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val playbackManager: PlaybackManager,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val addSongToPlaylistUseCase: AddSongToPlaylistUseCase,
    private val createPlaylistUseCase: CreatePlaylistUseCase
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _selectedFilter = MutableStateFlow(SearchFilter.ALL)
    private val _localSongs = MutableStateFlow<List<Song>>(emptyList())
    private val _onlineSongs = MutableStateFlow<List<Song>>(emptyList())
    private val _matchedPlaylists = MutableStateFlow<List<Playlist>>(emptyList())
    private val _artists = MutableStateFlow<List<ArtistGroup>>(emptyList())
    private val _albums = MutableStateFlow<List<AlbumGroup>>(emptyList())
    private val _recentSearches = MutableStateFlow(userPreferencesRepository.getRecentSearches())
    private val _isSearchingLocal = MutableStateFlow(false)
    private val _isSearchingOnline = MutableStateFlow(false)

    private var allLocalSongs: List<Song> = emptyList()
    private var allUserPlaylists: List<Playlist> = emptyList()
    private var searchJob: Job? = null

    val uiState: StateFlow<SearchUiState> = combine(
        combine(_query, _selectedFilter, _localSongs, _onlineSongs, _matchedPlaylists) { q, filter, local, online, playlists ->
            Tuple5(q, filter, local, online, playlists)
        },
        combine(_artists, _albums, _recentSearches, _isSearchingLocal, _isSearchingOnline) { artists, albums, recent, searchingLocal, searchingOnline ->
            Tuple5(artists, albums, recent, searchingLocal, searchingOnline)
        },
        combine(playbackManager.currentSong, playbackManager.isPlaying, playlistRepository.getPlaylists()) { song, isPlaying, playlists ->
            Triple(song, isPlaying, playlists)
        }
    ) { (q, filter, local, online, playlists), (artists, albums, recent, searchingLocal, searchingOnline), (currentSong, isPlaying, userPlaylists) ->
        this.allUserPlaylists = userPlaylists
        SearchUiState(
            query = q,
            selectedFilter = filter,
            localSongs = local,
            onlineSongs = online,
            matchedPlaylists = playlists,
            artists = artists,
            albums = albums,
            recentSearches = recent,
            isSearchingLocal = searchingLocal,
            isSearchingOnline = searchingOnline,
            currentSong = currentSong,
            isPlaying = isPlaying,
            userPlaylists = userPlaylists
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchUiState())

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            allLocalSongs = musicRepository.getAllSongs()
        }
    }

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
        searchJob?.cancel()

        if (newQuery.isBlank()) {
            _localSongs.value = emptyList()
            _onlineSongs.value = emptyList()
            _matchedPlaylists.value = emptyList()
            _artists.value = emptyList()
            _albums.value = emptyList()
            _isSearchingLocal.value = false
            _isSearchingOnline.value = false
            _recentSearches.value = userPreferencesRepository.getRecentSearches()
            return
        }

        searchJob = viewModelScope.launch {
            _isSearchingLocal.value = true
            _isSearchingOnline.value = true

            // Instant local search
            performLocalSearch(newQuery)
            _isSearchingLocal.value = false

            // Debounced online search
            delay(400)
            performOnlineSearch(newQuery)
            _isSearchingOnline.value = false
        }
    }

    private fun performLocalSearch(query: String) {
        val q = query.trim().lowercase()

        // 1. Matching songs
        val matchedSongs = allLocalSongs.filter {
            it.title.lowercase().contains(q) ||
            it.artist.lowercase().contains(q) ||
            it.album.lowercase().contains(q) ||
            (it.genre?.lowercase()?.contains(q) == true)
        }
        _localSongs.value = matchedSongs

        // 2. Matching artists
        val matchedArtists = allLocalSongs
            .filter { it.artist.lowercase().contains(q) }
            .groupBy { it.artist }
            .map { (artist, songs) -> ArtistGroup(artist, songs) }
        _artists.value = matchedArtists

        // 3. Matching albums
        val matchedAlbums = allLocalSongs
            .filter { it.album.lowercase().contains(q) && it.album.isNotBlank() }
            .groupBy { "${it.album}___${it.artist}" }
            .map { (_, songs) ->
                val first = songs.first()
                AlbumGroup(first.album, first.artist, songs)
            }
        _albums.value = matchedAlbums

        // 4. Matching playlists
        val matchedPlaylists = allUserPlaylists.filter {
            it.name.lowercase().contains(q)
        }
        _matchedPlaylists.value = matchedPlaylists
    }

    private suspend fun performOnlineSearch(query: String) = coroutineScope {
        val settings = userPreferencesRepository.settings.value
        val preferredLangs = settings.preferredLanguages

        val onlineDeferred = async {
            onlineStreamRepository.getGeneralTrendingSongs(
                languages = preferredLangs,
                categoryQuery = query,
                limit = 16
            )
        }

        val serverDeferred = async {
            if (settings.isNavidromeEnabled && settings.navidromeServerUrl.isNotBlank()) {
                subsonicApiService.search(
                    serverUrl = settings.navidromeServerUrl,
                    username = settings.navidromeUsername,
                    password = settings.navidromePassword,
                    query = query,
                    limit = 10
                )
            } else {
                emptyList()
            }
        }

        val onlineSongs = onlineDeferred.await()
        val serverSongs = serverDeferred.await()

        _onlineSongs.value = (serverSongs + onlineSongs).distinctBy { it.id }
    }

    fun onFilterSelected(filter: SearchFilter) {
        _selectedFilter.value = filter
    }

    fun submitSearch(query: String) {
        val clean = query.trim()
        if (clean.isNotBlank()) {
            userPreferencesRepository.addRecentSearch(clean)
            _recentSearches.value = userPreferencesRepository.getRecentSearches()
        }
    }

    fun removeRecentSearch(query: String) {
        userPreferencesRepository.removeRecentSearch(query)
        _recentSearches.value = userPreferencesRepository.getRecentSearches()
    }

    fun clearRecentSearches() {
        userPreferencesRepository.clearRecentSearches()
        _recentSearches.value = emptyList()
    }

    // ── Playback & Queue Controls ────────────────────────────────────────────
    fun playSong(songs: List<Song>, startIndex: Int) {
        submitSearch(_query.value)
        playbackManager.playSongs(songs, startIndex)
    }

    fun playNext(song: Song) {
        playbackManager.playNext(song)
    }

    fun addToQueue(song: Song) {
        playbackManager.addToQueue(song)
    }

    fun playPause() {
        playbackManager.playPause()
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            toggleFavoriteUseCase(song.id)
            allLocalSongs = allLocalSongs.map {
                if (it.id == song.id) it.copy(isFavorite = !it.isFavorite) else it
            }
            _localSongs.value = _localSongs.value.map {
                if (it.id == song.id) it.copy(isFavorite = !it.isFavorite) else it
            }
            _onlineSongs.value = _onlineSongs.value.map {
                if (it.id == song.id) it.copy(isFavorite = !it.isFavorite) else it
            }
        }
    }

    fun addToPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch {
            addSongToPlaylistUseCase(playlistId, song)
        }
    }

    fun createPlaylist(name: String, initialSong: Song? = null) {
        viewModelScope.launch {
            val id = createPlaylistUseCase(name)
            if (initialSong != null) {
                addSongToPlaylistUseCase(id, initialSong)
            }
        }
    }

    private data class Tuple5<A, B, C, D, E>(
        val a: A, val b: B, val c: C, val d: D, val e: E
    )
}
