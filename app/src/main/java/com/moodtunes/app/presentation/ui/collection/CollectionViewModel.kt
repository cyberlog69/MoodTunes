package com.moodtunes.app.presentation.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodtunes.app.data.remote.OnlineStreamRepository
import com.moodtunes.app.data.remote.api.YouTubeSessionStore
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.model.YouTubeItem
import com.moodtunes.app.domain.model.YouTubePage
import com.moodtunes.app.domain.model.YouTubeShelf
import com.moodtunes.app.service.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CollectionTab(val label: String) {
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    PLAYLISTS("Playlists")
}

data class CollectionUiState(
    val isSignedIn: Boolean = false,
    val accountName: String = "",
    val selectedTab: CollectionTab = CollectionTab.SONGS,
    val shelves: List<YouTubeShelf> = emptyList(),
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentSong: Song? = null,
    val isPlaying: Boolean = false
)

@HiltViewModel
class CollectionViewModel @Inject constructor(
    private val onlineStreamRepository: OnlineStreamRepository,
    private val sessionStore: YouTubeSessionStore,
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
                if (signedIn && _state.value.shelves.isEmpty() && _state.value.songs.isEmpty()) {
                    load(CollectionTab.SONGS)
                }
            }
        }
    }

    fun selectTab(tab: CollectionTab) {
        if (_state.value.selectedTab == tab && (_state.value.shelves.isNotEmpty() || _state.value.songs.isNotEmpty())) {
            _state.value = _state.value.copy(selectedTab = tab)
            return
        }
        _state.value = _state.value.copy(selectedTab = tab)
        load(tab)
    }

    fun refresh() = load(_state.value.selectedTab)

    private fun load(tab: CollectionTab) {
        if (!sessionStore.isSignedIn.value) {
            _state.value = _state.value.copy(
                isLoading = false,
                error = "Sign in to YouTube Music to see your collection."
            )
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, shelves = emptyList(), songs = emptyList())
            val page = runCatching {
                when (tab) {
                    CollectionTab.SONGS -> onlineStreamRepository.getLibraryLanding()
                    CollectionTab.ALBUMS -> onlineStreamRepository.getLikedAlbums()
                    CollectionTab.ARTISTS -> onlineStreamRepository.getLibraryArtists()
                    CollectionTab.PLAYLISTS -> onlineStreamRepository.getLikedPlaylists()
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

    fun playPause() = playbackManager.playPause()
}

/** Convenience for screens that only need typed catalogue rows. */
fun List<YouTubeShelf>.catalogueItems(): List<YouTubeItem> =
    flatMap { it.items }.filterNot { it is YouTubeItem.Track }.distinctBy { it.id }
