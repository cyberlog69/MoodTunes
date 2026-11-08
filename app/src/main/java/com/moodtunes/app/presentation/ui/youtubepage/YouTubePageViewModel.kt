package com.moodtunes.app.presentation.ui.youtubepage

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodtunes.app.data.remote.OnlineStreamRepository
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.model.YouTubePage
import com.moodtunes.app.domain.model.YouTubeShelf
import com.moodtunes.app.service.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class YouTubePageUiState(
    val browseId: String = "",
    val title: String = "",
    val subtitle: String? = null,
    val thumbnail: Uri? = null,
    val shelves: List<YouTubeShelf> = emptyList(),
    val songs: List<Song> = emptyList(),
    val continuation: String? = null,
    val isLoading: Boolean = true,
    val isAppending: Boolean = false,
    val error: String? = null,
    val isAlbum: Boolean = false,
    val isArtist: Boolean = false,
    val isPlaylist: Boolean = false
) {
    val playable: Boolean get() = songs.isNotEmpty()
}

@HiltViewModel
class YouTubePageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val onlineStreamRepository: OnlineStreamRepository,
    private val playbackManager: PlaybackManager
) : ViewModel() {

    private val browseId: String = savedStateHandle.get<String>("browseId").orEmpty()
    private val params: String? = savedStateHandle.get<String>("params")
        ?.takeIf { it.isNotBlank() && it != "null" }

    private val _state = MutableStateFlow(YouTubePageUiState(browseId = browseId))

    val uiState: StateFlow<YouTubePageUiState> = _state.asStateFlow()

    val currentSong = playbackManager.currentSong
    val isPlaying = playbackManager.isPlaying

    init {
        load()
    }

    fun load() {
        if (browseId.isBlank()) {
            _state.value = _state.value.copy(
                isLoading = false,
                error = "This YouTube Music page is missing an id."
            )
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val page = runCatching {
                onlineStreamRepository.getBrowsePage(browseId, params)
            }.getOrNull()
            _state.value = if (page == null) {
                _state.value.copy(
                    isLoading = false,
                    error = "Could not load this page from YouTube Music."
                )
            } else {
                page.toState()
            }
        }
    }

    fun loadMore() {
        val continuation = _state.value.continuation ?: return
        if (_state.value.isAppending) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isAppending = true)
            val next = runCatching {
                onlineStreamRepository.getBrowseContinuation(continuation)
            }.getOrNull()
            if (next == null) {
                _state.value = _state.value.copy(isAppending = false)
                return@launch
            }

            val current = _state.value
            val mergedSongs = (current.songs + next.songs)
                .distinctBy { it.videoId ?: it.id.toString() }
            val mergedShelves = mergeShelves(current.shelves, next.shelves)
            _state.value = current.copy(
                songs = mergedSongs,
                shelves = mergedShelves,
                // A repeated token means there is no further page; clearing it
                // stops "Load more" from refetching the same content forever.
                continuation = next.continuation?.takeIf { it != continuation },
                isAppending = false
            )
        }
    }

    private fun mergeShelves(
        current: List<YouTubeShelf>,
        next: List<YouTubeShelf>
    ): List<YouTubeShelf> {
        if (next.isEmpty()) return current
        val onlySongs = next.size == 1 && next.first().title.isBlank()
        if (onlySongs && current.isNotEmpty()) {
            val last = current.last()
            return current.dropLast(1) + last.copy(items = last.items + next.first().items)
        }
        return current + next
    }

    private fun YouTubePage.toState(): YouTubePageUiState = YouTubePageUiState(
        browseId = browseId,
        title = title,
        subtitle = subtitle,
        thumbnail = thumbnail,
        shelves = shelves,
        songs = songs,
        continuation = continuation,
        isLoading = false,
        isAlbum = isAlbum,
        isArtist = isArtist,
        isPlaylist = isPlaylist
    )

    fun playSong(songs: List<Song>, index: Int) {
        if (songs.isEmpty()) return
        playbackManager.playSongs(songs, index.coerceIn(0, songs.lastIndex))
    }

    fun playAll(shuffle: Boolean = false) {
        val songs = _state.value.songs
        if (songs.isEmpty()) return
        val ordered = if (shuffle) songs.shuffled() else songs
        playbackManager.playSongs(ordered, 0)
    }

    fun playPause() = playbackManager.playPause()
}
