package com.moodtunes.app.presentation.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodtunes.app.data.remote.OnlineStreamRepository
import com.moodtunes.app.data.remote.api.YouTubeSessionStore
import com.moodtunes.app.domain.model.Playlist
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.model.YouTubeItem
import com.moodtunes.app.domain.model.YouTubePage
import com.moodtunes.app.domain.model.YouTubeShelf
import com.moodtunes.app.domain.repository.IMusicRepository
import com.moodtunes.app.domain.repository.IPlaylistRepository
import com.moodtunes.app.domain.usecase.ToggleFavoriteUseCase
import com.moodtunes.app.service.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CollectionTab(val label: String, val requiresYouTube: Boolean) {
    SONGS("Liked", true),
    ALBUMS("Albums", true),
    ARTISTS("Artists", true),
    YT_PLAYLISTS("YT Playlists", true),
    FAVORITES("Favorites", false),
    PLAYLISTS("Playlists", false)
}

data class CollectionUiState(
    val isSignedIn: Boolean = false,
    val accountName: String = "",
    val selectedTab: CollectionTab = CollectionTab.SONGS,
    val shelves: List<YouTubeShelf> = emptyList(),
    val songs: List<Song> = emptyList(),
    val localPlaylists: List<Playlist> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentSong: Song? = null,
    val isPlaying: Boolean = false
)

@HiltViewModel
class CollectionViewModel @Inject constructor(
    private val onlineStreamRepository: OnlineStreamRepository,
    private val sessionStore: YouTubeSessionStore,
    private val musicRepository: IMusicRepository,
    private val playlistRepository: IPlaylistRepository,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val playbackManager: PlaybackManager
) : ViewModel() {

    private val _state = MutableStateFlow(CollectionUiState())

    val uiState: StateFlow<CollectionUiState> = combine(
        _state,
        sessionStore.isSignedIn,
        sessionStore.accountName,
        playbackManager.currentSong,
        playbackManager.isPlaying
    ) { state, signedIn, accountName, song, playing ->
        state.copy(
            isSignedIn = signedIn,
            accountName = accountName,
            currentSong = song,
            isPlaying = playing
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        CollectionUiState()
    )

    init {
        viewModelScope.launch {
            sessionStore.isSignedIn.collect { signedIn ->
                if (signedIn && _state.value.shelves.isEmpty() && _state.value.songs.isEmpty() &&
                    _state.value.selectedTab.requiresYouTube
                ) {
                    load(_state.value.selectedTab)
                }
            }
        }
    }

    fun selectTab(tab: CollectionTab) {
        _state.value = _state.value.copy(selectedTab = tab)
        load(tab)
    }

    fun refresh() = load(_state.value.selectedTab)

    private fun load(tab: CollectionTab) {
        if (tab == CollectionTab.FAVORITES) {
            viewModelScope.launch {
                _state.value = _state.value.copy(isLoading = true, error = null)
                val favorites = runCatching { musicRepository.getFavoriteSongs().first() }
                    .getOrDefault(emptyList())
                _state.value = _state.value.copy(
                    isLoading = false,
                    songs = favorites,
                    shelves = emptyList(),
                    localPlaylists = emptyList()
                )
            }
            return
        }

        if (tab == CollectionTab.PLAYLISTS) {
            viewModelScope.launch {
                _state.value = _state.value.copy(isLoading = true, error = null)
                val playlists = runCatching { playlistRepository.getPlaylists().first() }
                    .getOrDefault(emptyList())
                _state.value = _state.value.copy(
                    isLoading = false,
                    songs = emptyList(),
                    shelves = emptyList(),
                    localPlaylists = playlists
                )
            }
            return
        }

        if (!sessionStore.isSignedIn.value) {
            _state.value = _state.value.copy(
                isLoading = false,
                error = "Sign in to YouTube Music to see your liked songs, albums, artists and playlists."
            )
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                error = null,
                shelves = emptyList(),
                songs = emptyList(),
                localPlaylists = emptyList()
            )
            val page = runCatching {
                when (tab) {
                    CollectionTab.SONGS -> onlineStreamRepository.getLibraryLanding()
                    CollectionTab.ALBUMS -> onlineStreamRepository.getLikedAlbums()
                    CollectionTab.ARTISTS -> onlineStreamRepository.getLibraryArtists()
                    CollectionTab.YT_PLAYLISTS -> onlineStreamRepository.getLikedPlaylists()
                    else -> null
                }
            }.getOrNull()

            if (page == null) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Could not load your YouTube Music collection."
                )
                return@launch
            }

            val songs = if (tab == CollectionTab.SONGS) {
                val landingSongs = page.playableSongsOrShelfSongs()
                if (landingSongs.isNotEmpty()) {
                    landingSongs
                } else {
                    runCatching { onlineStreamRepository.getLibrarySongs() }
                        .getOrNull()
                        ?.playableSongsOrShelfSongs()
                        .orEmpty()
                }
            } else {
                emptyList()
            }

            _state.value = _state.value.copy(
                isLoading = false,
                shelves = if (songs.isEmpty()) page.shelves.filter { it.items.isNotEmpty() } else emptyList(),
                songs = songs
            )
        }
    }

    private fun YouTubePage.playableSongsOrShelfSongs(): List<Song> =
        (songs + shelves.flatMap { it.playableSongs })
            .distinctBy { it.videoId ?: it.id.toString() }

    fun playSong(songs: List<Song>, index: Int) {
        if (songs.isEmpty()) return
        playbackManager.playSongs(songs, index.coerceIn(0, songs.lastIndex))
    }

    fun playShelf(shelf: YouTubeShelf) {
        val songs = shelf.playableSongs
        if (songs.isNotEmpty()) playbackManager.playSongs(songs, 0)
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            val newFavorite = runCatching { toggleFavoriteUseCase(song) }
                .getOrDefault(!song.isFavorite)
            val onFavoritesTab = _state.value.selectedTab == CollectionTab.FAVORITES
            _state.value = _state.value.copy(
                songs = _state.value.songs
                    .map { if (it.id == song.id) it.copy(isFavorite = newFavorite) else it }
                    .filter { !onFavoritesTab || it.isFavorite }
            )
        }
    }

    fun playPause() = playbackManager.playPause()
}

/** Convenience for screens that only need typed catalogue rows. */
fun List<YouTubeShelf>.catalogueItems(): List<YouTubeItem> =
    flatMap { it.items }.filterNot { it is YouTubeItem.Track }.distinctBy { it.id }
