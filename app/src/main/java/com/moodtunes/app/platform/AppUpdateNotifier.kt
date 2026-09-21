package com.moodtunes.app.platform

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.moodtunes.app.MainActivity
import com.moodtunes.app.MoodTunesApp
import com.moodtunes.app.data.remote.UpdateCheckResult
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun notifyUpdate(result: UpdateCheckResult, force: Boolean = false) {
        try {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                Timber.w("Notifications are disabled by the user; skipping update notification")
                return
            }

            val lastNotified = prefs.getString(KEY_LAST_NOTIFIED_VERSION, null)
            if (!force && lastNotified == result.latestVersion) {
                Timber.d("Update notification already shown for version %s", result.latestVersion)
                return
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_SHOW_UPDATE, true)
                putExtra(EXTRA_LATEST_VERSION, result.latestVersion)
                putExtra(EXTRA_RELEASE_NOTES, result.releaseNotes)
                putExtra(EXTRA_DOWNLOAD_URL, result.apkDownloadUrl.ifEmpty { result.downloadUrl })
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notesSummary = if (result.releaseNotes.isNotBlank()) {
                result.releaseNotes.take(300).trimEnd()
            } else {
                "Includes new features, bug fixes, and performance improvements."
            }

            val notification = NotificationCompat.Builder(context, MoodTunesApp.UPDATE_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("🎉 Update Available: ${result.latestVersion}")
                .setContentText("A new version of MoodTunes is ready. Tap to install!")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle("🎉 Update Available: ${result.latestVersion}")
                        .bigText("A new version of MoodTunes (${result.latestVersion}) is ready:\n\n$notesSummary\n\nTap here to download and install.")
                )
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)

            prefs.edit().putString(KEY_LAST_NOTIFIED_VERSION, result.latestVersion).apply()
            Timber.i("Posted update notification for %s", result.latestVersion)
        } catch (e: Exception) {
            Timber.e(e, "Failed to display update notification")
        }
    }

    fun clearNotification() {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Timber.w(e, "Failed to clear update notification")
        }
    }

    companion object {
        const val NOTIFICATION_ID = 2001
        const val EXTRA_SHOW_UPDATE = "extra_show_update"
        const val EXTRA_LATEST_VERSION = "extra_latest_version"
        const val EXTRA_RELEASE_NOTES = "extra_release_notes"
        const val EXTRA_DOWNLOAD_URL = "extra_download_url"

        private const val PREFS_NAME = "moodtunes_update_prefs"
        private const val KEY_LAST_NOTIFIED_VERSION = "last_notified_version"
    }
}
