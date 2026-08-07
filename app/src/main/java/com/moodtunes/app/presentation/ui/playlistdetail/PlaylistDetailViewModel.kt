package com.moodtunes.app.presentation.ui.playlistdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodtunes.app.domain.model.Playlist
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.usecase.*
import com.moodtunes.app.service.PlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistDetailUiState(
    val playlistId: Long = -1,
    val playlist: Playlist? = null,
    val isLoading: Boolean = true,
    val showPicker: Boolean = false,
    val pickerSongs: List<Song> = emptyList(),
    val pickerQuery: String = "",
    val currentSongId: Long? = null,
    val isPlaying: Boolean = false
)

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getPlaylistUseCase: GetPlaylistUseCase,
    private val getAllSongsUseCase: GetAllSongsUseCase,
    private val addSongToPlaylistUseCase: AddSongToPlaylistUseCase,
    private val removeSongFromPlaylistUseCase: RemoveSongFromPlaylistUseCase,
    private val moveSongInPlaylistUseCase: MoveSongInPlaylistUseCase,
    private val renamePlaylistUseCase: RenamePlaylistUseCase,
    private val deletePlaylistUseCase: DeletePlaylistUseCase,
    private val playbackManager: PlaybackManager
) : ViewModel() {

    private val playlistId: Long = savedStateHandle.get<Long>("playlistId") ?: -1L
    private val _showPicker = MutableStateFlow(false)
    private val _pickerSongs = MutableStateFlow<List<Song>>(emptyList())
    private val _pickerQuery = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(true)

    val uiState: StateFlow<PlaylistDetailUiState> = combine(
        getPlaylistUseCase(playlistId),
        playbackManager.currentSong,
        playbackManager.isPlaying,
        _showPicker,
        _pickerSongs,
        _pickerQuery,
        _isLoading
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val playlist = args[0] as Playlist?
        val currentSong = args[1] as Song?
        val playing = args[2] as Boolean
        val showPicker = args[3] as Boolean
        @Suppress("UNCHECKED_CAST")
        val pickerSongs = args[4] as List<Song>
        val pickerQuery = args[5] as String
        val isLoading = args[6] as Boolean
        PlaylistDetailUiState(
            playlistId = playlistId,
            playlist = playlist,
            isLoading = isLoading,
            showPicker = showPicker,
            pickerSongs = pickerSongs,
            pickerQuery = pickerQuery,
            currentSongId = currentSong?.id,
            isPlaying = playing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlaylistDetailUiState(playlistId = playlistId)
    )

    init {
        viewModelScope.launch {
            getPlaylistUseCase(playlistId).first()
            _isLoading.value = false
        }
    }

    fun playAll() {
        val playlist = uiState.value.playlist ?: return
        if (playlist.songs.isEmpty()) return
        playbackManager.playSongs(playlist.songs, 0, mood = null)
    }

    fun playSong(index: Int) {
        val playlist = uiState.value.playlist ?: return
        if (index !in playlist.songs.indices) return
        playbackManager.playSongs(playlist.songs, index, mood = null)
    }

    fun removeSong(songId: Long) {
        viewModelScope.launch {
            removeSongFromPlaylistUseCase(playlistId, songId)
        }
    }

    fun moveSong(fromPosition: Int, toPosition: Int) {
        viewModelScope.launch {
            moveSongInPlaylistUseCase(playlistId, fromPosition, toPosition)
        }
    }

    fun rename(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            renamePlaylistUseCase(playlistId, name.trim())
        }
    }

    fun delete() {
        viewModelScope.launch {
            deletePlaylistUseCase(playlistId)
        }
    }

    // ── Add-songs picker ─────────────────────────────────────────────────────
    fun openPicker() {
        viewModelScope.launch {
            val songs = getAllSongsUseCase()
            _pickerSongs.value = songs
            _showPicker.value = true
        }
    }

    fun closePicker() {
        _showPicker.value = false
    }

    @OptIn(FlowPreview::class)
    fun onPickerQueryChanged(query: String) {
        _pickerQuery.value = query
    }

    fun addPickedSongs(pickedIds: Set<Long>) {
        if (pickedIds.isEmpty()) return
        viewModelScope.launch {
            val picked = _pickerSongs.value.filter { it.id in pickedIds }
            picked.forEach { addSongToPlaylistUseCase(playlistId, it) }
            _showPicker.value = false
        }
    }
}
