package com.moodtunes.app.platform

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.moodtunes.app.service.MusicPlaybackService
import java.util.concurrent.TimeUnit

/**
 * Helper for UI surfaces outside the app's own ViewModels (home widget, quick
 * settings tile, action callbacks) to bind a [MediaController] to the app's
 * [MusicPlaybackService] session and control playback.
 */
object PlayerConnection {

    private const val CONNECT_TIMEOUT_MS = 3_000L

    fun connect(context: Context): MediaController? = try {
        val token = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.get(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    } catch (e: Exception) {
        null
    }

    fun playPause(context: Context) {
        connect(context)?.run {
            if (isPlaying) pause() else play()
            release()
        }
    }

    fun next(context: Context) {
        connect(context)?.run {
            seekToNextMediaItem()
            release()
        }
    }

    fun previous(context: Context) {
        connect(context)?.run {
            seekToPreviousMediaItem()
            release()
        }
    }
}
