package com.moodtunes.app.presentation.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodtunes.app.data.remote.OnlineStreamRepository
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.model.YouTubePage
import com.moodtunes.app.domain.model.YouTubeShelf
import com.moodtunes.app.service.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiscoverUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val homeShelves: List<YouTubeShelf> = emptyList(),
    val chartShelves: List<YouTubeShelf> = emptyList(),
    val moodShelves: List<YouTubeShelf> = emptyList(),
    val releaseShelves: List<YouTubeShelf> = emptyList(),
    val currentSong: Song? = null,
    val isPlaying: Boolean = false
) {
    val isEmpty: Boolean
        get() = !isLoading && homeShelves.isEmpty() && chartShelves.isEmpty() &&
            moodShelves.isEmpty() && releaseShelves.isEmpty()
}

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val onlineStreamRepository: OnlineStreamRepository,
    private val playbackManager: PlaybackManager
) : ViewModel() {

    private val _state = MutableStateFlow(DiscoverUiState())

    val uiState: StateFlow<DiscoverUiState> = combine(
        _state,
        playbackManager.currentSong,
        playbackManager.isPlaying
    ) { state, currentSong, isPlaying ->
        state.copy(currentSong = currentSong, isPlaying = isPlaying)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DiscoverUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            val result = coroutineScope {
                val home = async { runCatching { onlineStreamRepository.getHome() }.getOrNull() }
                val charts = async { runCatching { onlineStreamRepository.getCharts() }.getOrNull() }
                val moods = async { runCatching { onlineStreamRepository.getMoodsAndGenres() }.getOrNull() }
                val releases = async { runCatching { onlineStreamRepository.getNewReleases() }.getOrNull() }
                listOf(home.await(), charts.await(), moods.await(), releases.await())
            }

            val homePage = result[0]
            val chartPage = result[1]
            val moodPage = result[2]
            val releasePage = result[3]

            _state.value = _state.value.copy(
                isLoading = false,
                homeShelves = homePage.shelves(),
                chartShelves = chartPage.shelves(),
                moodShelves = moodPage.shelves(),
                releaseShelves = releasePage.shelves(),
                error = if (listOf(homePage, chartPage, moodPage, releasePage).all { it == null }) {
                    "YouTube Music is unreachable right now. Check your connection and try again."
                } else null
            )
        }
    }

    private fun YouTubePage?.shelves(): List<YouTubeShelf> =
        this?.shelves.orEmpty().filter { it.items.isNotEmpty() }

    fun playShelf(shelf: YouTubeShelf, startIndex: Int = 0) {
        val songs = shelf.playableSongs
        if (songs.isNotEmpty()) {
            playbackManager.playSongs(songs, startIndex.coerceIn(0, songs.lastIndex))
        }
    }

    fun playSong(songs: List<Song>, index: Int) {
        if (songs.isNotEmpty()) {
            playbackManager.playSongs(songs, index.coerceIn(0, songs.lastIndex))
        }
    }

    fun playPause() = playbackManager.playPause()
}
