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
            val channel = NotificationChannel(
                PLAYBACK_NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val PLAYBACK_NOTIFICATION_CHANNEL_ID = "moodtunes_playback_channel"
    }
}
