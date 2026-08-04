package com.moodtunes.app.service

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var mediaController: MediaController? = null

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playlist = MutableStateFlow<List<Song>>(emptyList())
    val playlist: StateFlow<List<Song>> = _playlist.asStateFlow()

    private val _currentMood = MutableStateFlow<MoodType?>(null)
    val currentMood: StateFlow<MoodType?> = _currentMood.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private var progressJob: Job? = null

    init {
        initController()
    }

    private fun initController() {
        val sessionToken = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                mediaController = controller
                attachListener(controller)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0, mood: MoodType? = null) {
        if (songs.isEmpty()) return
        _playlist.value = songs
        _currentMood.value = mood
        _currentSong.value = songs.getOrNull(startIndex)

        val controller = mediaController
        if (controller != null) {
            val mediaItems = songs.map { song ->
                MediaItem.Builder()
                    .setUri(song.uri)
                    .setMediaId(song.id.toString())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .setArtworkUri(song.albumArtUri)
                            .build()
                    )
                    .build()
            }

            controller.setMediaItems(mediaItems, startIndex, 0L)
            controller.prepare()
            controller.play()
        } else {
            // Controller connecting asynchronously; retry after a short delay
            scope.launch {
                delay(300)
                mediaController?.let { ctrl ->
                    val mediaItems = songs.map { song ->
                        MediaItem.Builder()
                            .setUri(song.uri)
                            .setMediaId(song.id.toString())
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(song.title)
                                    .setArtist(song.artist)
                                    .setAlbumTitle(song.album)
                                    .setArtworkUri(song.albumArtUri)
                                    .build()
                            )
                            .build()
                    }
                    ctrl.setMediaItems(mediaItems, startIndex, 0L)
                    ctrl.prepare()
                    ctrl.play()
                }
            }
        }
    }

    fun playPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun skipNext() {
        mediaController?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        val controller = mediaController ?: return
        if (controller.currentPosition > 3000) {
            controller.seekTo(0L)
        } else {
            controller.seekToPreviousMediaItem()
        }
    }

    fun seekTo(positionFraction: Float) {
        val duration = _durationMs.value
        if (duration > 0) {
            mediaController?.seekTo((positionFraction * duration).toLong())
        }
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        val newMode = !controller.shuffleModeEnabled
        controller.shuffleModeEnabled = newMode
        _isShuffleEnabled.value = newMode
    }

    fun toggleRepeat() {
        val controller = mediaController ?: return
        val nextMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = nextMode
        _repeatMode.value = nextMode
    }

    private fun attachListener(controller: MediaController) {
        _isPlaying.value = controller.isPlaying
        _isShuffleEnabled.value = controller.shuffleModeEnabled
        _repeatMode.value = controller.repeatMode

        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) startProgressTracking() else progressJob?.cancel()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = controller.currentMediaItemIndex
                val current = _playlist.value.getOrNull(index)
                if (current != null) {
                    _currentSong.value = current
                }
                _durationMs.value = controller.duration.coerceAtLeast(0L)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _durationMs.value = controller.duration.coerceAtLeast(0L)
                }
            }
        })
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    _currentPositionMs.value = controller.currentPosition.coerceAtLeast(0L)
                    _durationMs.value = controller.duration.coerceAtLeast(0L)
                }
                delay(500)
            }
        }
    }
}
