package com.moodtunes.app.presentation.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodtunes.app.data.local.preferences.UserPreferencesRepository
import com.moodtunes.app.data.remote.OnlineStreamRepository
import com.moodtunes.app.domain.model.AudioFormat
import com.moodtunes.app.domain.model.Playlist
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.model.YouTubeItem
import com.moodtunes.app.domain.model.YouTubeSearchFilter
import com.moodtunes.app.domain.repository.IPlaylistRepository
import com.moodtunes.app.domain.usecase.AddSongToPlaylistUseCase
import com.moodtunes.app.domain.usecase.CreatePlaylistUseCase
import com.moodtunes.app.domain.usecase.ToggleFavoriteUseCase
import com.moodtunes.app.domain.util.SearchRank
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
    LYRICS("From the Lyrics"),
    PLAYLISTS("Playlists")
}

data class LyricSearchResult(
    val song: Song,
    val matchedSnippet: String,
    val source: String = "LRCLIB"
)

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
    val onlineSongs: List<Song> = emptyList(),
    val lyricResults: List<LyricSearchResult> = emptyList(),
    val matchedPlaylists: List<Playlist> = emptyList(),
    val artists: List<ArtistGroup> = emptyList(),
    val albums: List<AlbumGroup> = emptyList(),
    val youtubeTopResult: YouTubeItem? = null,
    val youtubeItems: List<YouTubeItem> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val isSearchingOnline: Boolean = false,
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val userPlaylists: List<Playlist> = emptyList()
) {
    val isEmpty: Boolean
        get() = query.isNotBlank() && !isSearchingOnline &&
                onlineSongs.isEmpty() && lyricResults.isEmpty() &&
                matchedPlaylists.isEmpty() && artists.isEmpty() && albums.isEmpty() &&
                youtubeTopResult == null && youtubeItems.isEmpty()
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val playlistRepository: IPlaylistRepository,
    private val onlineStreamRepository: OnlineStreamRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val playbackManager: PlaybackManager,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val addSongToPlaylistUseCase: AddSongToPlaylistUseCase,
    private val createPlaylistUseCase: CreatePlaylistUseCase,
    private val lrclibService: com.moodtunes.app.data.remote.api.LrclibService
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _selectedFilter = MutableStateFlow(SearchFilter.ALL)
    private val _onlineSongs = MutableStateFlow<List<Song>>(emptyList())
    private val _lyricResults = MutableStateFlow<List<LyricSearchResult>>(emptyList())
    private val _matchedPlaylists = MutableStateFlow<List<Playlist>>(emptyList())
    private val _artists = MutableStateFlow<List<ArtistGroup>>(emptyList())
    private val _albums = MutableStateFlow<List<AlbumGroup>>(emptyList())
    private val _youtubeTopResult = MutableStateFlow<YouTubeItem?>(null)
    private val _youtubeItems = MutableStateFlow<List<YouTubeItem>>(emptyList())
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    private val _recentSearches = MutableStateFlow(userPreferencesRepository.getRecentSearches())
    private val _isSearchingOnline = MutableStateFlow(false)

    private var allUserPlaylists: List<Playlist> = emptyList()
    private var searchJob: Job? = null

    val uiState: StateFlow<SearchUiState> = combine(
        combine(_query, _selectedFilter, _onlineSongs, _lyricResults) { q, filter, online, lyrics ->
            Tuple4(q, filter, online, lyrics)
        },
        combine(_matchedPlaylists, _artists, _albums, _recentSearches) { playlists, artists, albums, recent ->
            Tuple4(playlists, artists, albums, recent)
        },
        combine(_isSearchingOnline, playbackManager.currentSong, playbackManager.isPlaying, playlistRepository.getPlaylists()) { searchingOnline, song, isPlaying, playlists ->
            Tuple4(searchingOnline, song, isPlaying, playlists)
        },
        combine(_youtubeTopResult, _youtubeItems, _suggestions) { top, items, suggestions ->
            Tuple3(top, items, suggestions)
        }
    ) { (q, filter, online, lyrics), (playlists, artists, albums, recent), (searchingOnline, currentSong, isPlaying, userPlaylists), (top, youtubeItems, suggestions) ->
        this.allUserPlaylists = userPlaylists
        SearchUiState(
            query = q,
            selectedFilter = filter,
            onlineSongs = online,
            lyricResults = lyrics,
            matchedPlaylists = playlists,
            artists = artists,
            albums = albums,
            youtubeTopResult = top,
            youtubeItems = youtubeItems,
            suggestions = suggestions,
            recentSearches = recent,
            isSearchingOnline = searchingOnline,
            currentSong = currentSong,
            isPlaying = isPlaying,
            userPlaylists = userPlaylists
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchUiState())

    init {
        // Playlist matching is local; songs come from YouTube Music.
    }

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
        searchJob?.cancel()

        if (newQuery.isBlank()) {
            _onlineSongs.value = emptyList()
            _lyricResults.value = emptyList()
            _matchedPlaylists.value = emptyList()
            _artists.value = emptyList()
            _albums.value = emptyList()
            _youtubeTopResult.value = null
            _youtubeItems.value = emptyList()
            _suggestions.value = emptyList()
            _isSearchingOnline.value = false
            _recentSearches.value = userPreferencesRepository.getRecentSearches()
            return
        }

        searchJob = viewModelScope.launch {
            _isSearchingOnline.value = true

            // Instant local playlist matching
            matchPlaylists(newQuery)

            // Debounced online search + typeahead suggestions
            delay(350)
            _suggestions.value = runCatching {
                onlineStreamRepository.getSearchSuggestions(newQuery)
            }.getOrDefault(emptyList())
            performOnlineSearch(newQuery)
            _isSearchingOnline.value = false
        }
    }

    private fun matchPlaylists(query: String) {
        val q = query.trim().lowercase()
        _matchedPlaylists.value = allUserPlaylists.filter {
            it.name.lowercase().contains(q)
        }
    }

    private suspend fun performOnlineSearch(query: String) = coroutineScope {
        val selectedFilter = _selectedFilter.value
        val youTubeFilter = selectedFilter.toYouTubeFilter()

        val onlineDeferred = async {
            if (youTubeFilter == YouTubeSearchFilter.SONGS || youTubeFilter == YouTubeSearchFilter.ALL) {
                runCatching { onlineStreamRepository.searchSongs(query, 20) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
        }

        val typedDeferred = async {
            runCatching { onlineStreamRepository.searchPage(query, youTubeFilter) }.getOrNull()
        }

        val lyricsDeferred = async {
            if (selectedFilter == SearchFilter.ALL || selectedFilter == SearchFilter.LYRICS) {
                if (query.trim().length >= 3) {
                    runCatching {
                        val results = lrclibService.searchLyrics(query.trim())
                        results.take(8).mapNotNull { r ->
                            val matched = findMatchingLyricSnippet(r.plainLyrics ?: r.syncedLyrics ?: "", query.trim())
                            if (matched != null) {
                                val dummySong = Song(
                                    id = kotlin.math.abs("${r.artistName}_${r.trackName}".hashCode().toLong()),
                                    title = r.trackName,
                                    artist = r.artistName,
                                    album = r.albumName ?: "Single",
                                    duration = (r.durationSeconds ?: 200) * 1000L,
                                    uri = android.net.Uri.parse("online://search/${android.net.Uri.encode("${r.trackName} ${r.artistName}")}"),
                                    albumArtUri = null,
                                    isStream = true,
                                    audioFormat = AudioFormat.STREAM
                                )
                                LyricSearchResult(
                                    song = dummySong,
                                    matchedSnippet = matched,
                                    source = "LRCLIB"
                                )
                            } else null
                        }
                    }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }

        val onlineSongs = onlineDeferred.await()
        val typedPage = typedDeferred.await()
        val lyricMatches = lyricsDeferred.await()

        if (selectedFilter == SearchFilter.ALL || selectedFilter == SearchFilter.SONGS) {
            val allOnline = SearchRank.rankSongs(
                onlineSongs.distinctBy { it.id },
                query
            )
            _onlineSongs.value = allOnline
        } else {
            _onlineSongs.value = emptyList()
        }
        _lyricResults.value = lyricMatches

        val typedItems = typedPage?.allItems().orEmpty()
        val topResult = typedPage?.top
        _youtubeTopResult.value = topResult
        _youtubeItems.value = typedItems.filterNot {
            it is YouTubeItem.Track || it.id == topResult?.id
        }

        val allCombined = onlineSongs.distinctBy { it.id }
        val q = query.trim().lowercase()

        val matchedArtists = allCombined
            .filter { it.artist.lowercase().contains(q) }
            .groupBy { it.artist }
            .map { (artist, songs) -> ArtistGroup(artist, songs) }
        _artists.value = matchedArtists

        val matchedAlbums = allCombined
            .filter { it.album.lowercase().contains(q) && it.album.isNotBlank() && it.album != "YouTube Music" }
            .groupBy { "${it.album}___${it.artist}" }
            .map { (_, songs) ->
                val first = songs.first()
                AlbumGroup(first.album, first.artist, songs)
            }
        _albums.value = matchedAlbums
    }

    private fun SearchFilter.toYouTubeFilter(): YouTubeSearchFilter = when (this) {
        SearchFilter.SONGS -> YouTubeSearchFilter.SONGS
        SearchFilter.ARTISTS -> YouTubeSearchFilter.ARTISTS
        SearchFilter.ALBUMS -> YouTubeSearchFilter.ALBUMS
        SearchFilter.PLAYLISTS -> YouTubeSearchFilter.PLAYLISTS
        SearchFilter.LYRICS -> YouTubeSearchFilter.ALL
        SearchFilter.ALL -> YouTubeSearchFilter.ALL
    }

    private fun findMatchingLyricSnippet(lyrics: String, query: String): String? {
        if (lyrics.isBlank()) return null
        val cleanLyrics = lyrics.replace(Regex("\\[\\d+:\\d+(\\.\\d+)?\\]"), "")
        val lines = cleanLyrics.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        val qTerms = query.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }

        val exactMatch = lines.firstOrNull { it.contains(query, ignoreCase = true) }
        if (exactMatch != null) return exactMatch

        return lines.firstOrNull { line ->
            val lLower = line.lowercase()
            qTerms.count { term -> lLower.contains(term) } >= (qTerms.size / 2).coerceAtLeast(1)
        }
    }

    fun playLyricResult(lyricMatch: LyricSearchResult) {
        viewModelScope.launch {
            submitSearch(_query.value)
            val found = onlineStreamRepository.searchSongs("${lyricMatch.song.title} ${lyricMatch.song.artist}", limit = 1)
            if (found.isNotEmpty()) {
                playbackManager.playSongs(found, 0, autoplayRadio = true)
            } else {
                playbackManager.playSongs(listOf(lyricMatch.song), 0, autoplayRadio = true)
            }
        }
    }

    fun onFilterSelected(filter: SearchFilter) {
        if (_selectedFilter.value == filter) return
        _selectedFilter.value = filter
        val query = _query.value
        if (query.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                _isSearchingOnline.value = true
                performOnlineSearch(query)
                _isSearchingOnline.value = false
            }
        }
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
        // Search results are a launchpad, not a fixed queue: start a radio
        // station from the tapped song so it keeps playing related tracks.
        playbackManager.playSongs(songs, startIndex, autoplayRadio = true)
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
            toggleFavoriteUseCase(song)
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

    private data class Tuple3<A, B, C>(
        val a: A, val b: B, val c: C
    )

    private data class Tuple4<A, B, C, D>(
        val a: A, val b: B, val c: C, val d: D
    )
}
