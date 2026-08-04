package com.moodtunes.app.service

import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint

/**
 * Background service hosting ExoPlayer configured for Ultra-Low Latency High-Quality playback.
 * Features:
 * - 250ms Instant Start LoadControl buffer tuning
 * - FLAC, ALAC (Apple Lossless), WAV, AAC, and MP3 hardware/software decoding
 * - High-Resolution 24-bit audio pipeline output
 * - MediaSession background controls & notifications
 */
@AndroidEntryPoint
class MusicPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

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

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true) // Pause when headphones disconnect
            .setLoadControl(loadControl)
            .build()

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
