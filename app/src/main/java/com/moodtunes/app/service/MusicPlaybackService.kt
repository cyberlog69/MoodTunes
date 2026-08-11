package com.moodtunes.app.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.moodtunes.app.MainActivity
import com.moodtunes.app.data.remote.OnlineStreamRepository
import com.moodtunes.app.domain.repository.IMusicRepository
import com.moodtunes.app.domain.repository.IPlaylistRepository
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import javax.inject.Inject

/**
 * Background service hosting ExoPlayer configured for Ultra-Low Latency High-Quality playback.
 * Features:
 * - 250ms Instant Start LoadControl buffer tuning
 * - FLAC, ALAC (Apple Lossless), WAV, AAC, and MP3 hardware/software decoding
 * - High-Resolution 24-bit audio pipeline output
 * - MediaSession background controls & notifications
 * - MediaLibrarySession browsing for Android Auto (moods, playlists, favorites, all songs)
 */
@AndroidEntryPoint
@OptIn(UnstableApi::class)
class MusicPlaybackService : MediaLibraryService() {

    companion object {
        private const val CACHE_DIR = "moodtunes_stream_cache"
        private const val MAX_CACHE_BYTES = 512L * 1024 * 1024 // 512 MB LRU cache
        private const val CACHE_SINK_FRAGMENT_BYTES = 1024L * 1024 // 1 MB write fragments
        private const val EFFECT_ATTACH_RETRIES = 6
    }

    @Inject lateinit var musicRepository: IMusicRepository
    @Inject lateinit var playlistRepository: IPlaylistRepository
    @Inject lateinit var onlineStreamRepository: OnlineStreamRepository
    @Inject lateinit var audioEffectsManager: AudioEffectsManager
    @Inject lateinit var playbackPreferencesRepository: PlaybackPreferencesRepository

    private var mediaSession: MediaLibraryService.MediaLibrarySession? = null
    private val callbackScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var simpleCache: SimpleCache? = null
    private lateinit var player: ExoPlayer
    private val mainHandler = Handler(Looper.getMainLooper())
    private val effectsAttachRunnable = object : Runnable {
        var attempts = 0
        override fun run() {
            val sessionId = player.audioSessionId
            if (sessionId != C.AUDIO_SESSION_ID_UNSET && audioEffectsManager.attach(sessionId)) return
            if (attempts < EFFECT_ATTACH_RETRIES) {
                attempts++
                mainHandler.postDelayed(this, 400)
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // 24-bit / 32-bit Float Audio Sink for audiophile-grade Lossless output
        val audioSink = DefaultAudioSink.Builder(this)
            .setEnableFloatOutput(true)
            .setEnableAudioTrackPlaybackParams(true)
            .build()

        // Decoders for FLAC, ALAC, WAV, AAC, MP3
        val renderersFactory = DefaultRenderersFactory(this).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableAudioFloatOutput(true)
        }

        // Ultra-Fast LoadControl: start playing within 250ms of receiving initial network bytes!
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 10_000,
                /* maxBufferMs = */ 30_000,
                /* bufferForPlaybackMs = */ 250,        // Start playback after 250ms buffer!
                /* bufferForPlaybackAfterRebufferMs = */ 500 // Rebuffer after 500ms!
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Offline stream cache: recently played streams are served from disk when
        // connectivity drops, keeping playback resilient without extra data use.
        val mediaSourceFactory: MediaSource.Factory = runCatching {
            val cache = SimpleCache(
                File(cacheDir, CACHE_DIR),
                LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
                StandaloneDatabaseProvider(this)
            )
            simpleCache = cache
            val cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(DefaultDataSource.Factory(this))
                .setCacheWriteDataSinkFactory(
                    CacheDataSink.Factory()
                        .setCache(cache) // Required: the write sink needs the same cache reference.
                        .setFragmentSize(CACHE_SINK_FRAGMENT_BYTES)
                )
            DefaultMediaSourceFactory(cacheDataSourceFactory)
        }.getOrElse { e ->
            Timber.w(e, "Offline cache unavailable; falling back to non-cached playback")
            DefaultMediaSourceFactory(DefaultDataSource.Factory(this))
        }

        player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true) // Pause when headphones disconnect
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
        runCatching {
            player.skipSilenceEnabled = playbackPreferencesRepository.skipSilenceEnabled
        }
        player.addListener(object : Player.Listener {
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                if (playbackState == Player.STATE_READY) attachEffectsWhenReady()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) attachEffectsWhenReady()
            }
        })

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaLibraryService.MediaLibrarySession.Builder(
            this,
            player,
            MusicLibraryCallback(musicRepository, playlistRepository, onlineStreamRepository, callbackScope)
        )
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibraryService.MediaLibrarySession? =
        mediaSession

    /**
     * Binds the equalizer/bass boost to the player's real audio session once it
     * exists. The session is created by ExoPlayer when the first AudioTrack is
     * built, so this must not run until playback has actually started. Runs on the
     * main (application) thread, which ExoPlayer requires for audio session reads.
     * Retries are fail-fast: devices without the legacy AudioFX effects simply
     * leave them off.
     */
    private fun attachEffectsWhenReady() {
        mainHandler.removeCallbacks(effectsAttachRunnable)
        effectsAttachRunnable.attempts = 0
        effectsAttachRunnable.run()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(effectsAttachRunnable)
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        simpleCache?.release()
        simpleCache = null
        callbackScope.cancel()
        super.onDestroy()
    }
}
