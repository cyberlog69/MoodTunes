package com.moodtunes.app.platform

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.util.UnstableApi
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.service.MediaItemFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chromecast integration built on Media3's [CastPlayer]. Requires the cast
 * framework to be configured (see DefaultCastOptionsProvider in the manifest)
 * and an active cast session (established via the MediaRouteButton on the
 * player screen). Only online streams are castable.
 */
@Singleton
class CastPlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

    private val _castDeviceName = MutableStateFlow<String?>(null)
    val castDeviceName: StateFlow<String?> = _castDeviceName.asStateFlow()

    private val castContext: com.google.android.gms.cast.framework.CastContext? by lazy {
        try {
            com.google.android.gms.cast.framework.CastContext.getSharedInstance(context)
        } catch (e: Exception) {
            null
        }
    }

    @OptIn(UnstableApi::class)
    private val castPlayer: CastPlayer? by lazy {
        val ctx = castContext ?: return@lazy null
        CastPlayer(ctx).apply {
            setSessionAvailabilityListener(object : SessionAvailabilityListener {
                override fun onCastSessionAvailable() {
                    refreshCastState()
                }

                override fun onCastSessionUnavailable() {
                    refreshCastState()
                }
            })
        }
    }

    @OptIn(UnstableApi::class)
    fun castQueue(songs: List<Song>, startIndex: Int): Boolean {
        val player = castPlayer ?: return false
        if (!player.isCastSessionAvailable()) return false
        val items = songs.map { MediaItemFactory.songToMediaItem(it) }
        player.setMediaItems(items, startIndex, 0L)
        player.prepare()
        player.play()
        refreshCastState()
        return true
    }

    @OptIn(UnstableApi::class)
    fun disconnect() {
        castContext?.sessionManager?.endCurrentSession(true)
        castPlayer?.stop()
        refreshCastState()
    }

    private fun refreshCastState() {
        val session = castContext?.sessionManager?.currentCastSession
        _isCasting.value = session != null
        _castDeviceName.value = session?.castDevice?.friendlyName
    }
}
