package com.moodtunes.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.moodtunes.app.platform.CrashHandler
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class MoodTunesApp : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }
        CrashHandler(this).install()
        createNotificationChannel()
        com.moodtunes.app.platform.UpdateCheckJobService.schedule(this)
    }

    /** Minimal release tree: tag-based, no extra metadata. */
    private class ReleaseTree : Timber.Tree() {
        override fun isLoggable(tag: String?, priority: Int): Boolean =
            priority >= android.util.Log.WARN

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (t != null) {
                if (priority == android.util.Log.ERROR) {
                    android.util.Log.e(tag, message, t)
                } else {
                    android.util.Log.w(tag, message, t)
                }
            } else {
                if (priority == android.util.Log.ERROR) {
                    android.util.Log.e(tag, message)
                } else {
                    android.util.Log.w(tag, message)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackChannel = NotificationChannel(
                PLAYBACK_NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }

            val downloadChannel = NotificationChannel(
                DOWNLOAD_NOTIFICATION_CHANNEL_ID,
                "Song Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of offline song downloads"
                setShowBadge(false)
            }

            val updateChannel = NotificationChannel(
                UPDATE_NOTIFICATION_CHANNEL_ID,
                "App Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when a new version of MoodTunes is released"
                setShowBadge(true)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(playbackChannel)
            manager.createNotificationChannel(downloadChannel)
            manager.createNotificationChannel(updateChannel)
        }
    }

    companion object {
        const val PLAYBACK_NOTIFICATION_CHANNEL_ID = "moodtunes_playback_channel"
        const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "moodtunes_download_channel"
        const val UPDATE_NOTIFICATION_CHANNEL_ID = "moodtunes_update_channel"
    }
}
