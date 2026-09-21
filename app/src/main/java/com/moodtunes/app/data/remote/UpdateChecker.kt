package com.moodtunes.app.data.remote

import android.content.Context
import com.moodtunes.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateCheckResult(
    val isUpdateAvailable: Boolean,
    val latestVersion: String,
    val currentVersion: String = "v${BuildConfig.VERSION_NAME}",
    val releaseNotes: String = "",
    val downloadUrl: String = "https://github.com/cyberlog69/MoodTunes/releases",
    val apkDownloadUrl: String = ""
)

@Singleton
class UpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun checkForUpdates(
        force: Boolean = false,
        currentVersion: String = BuildConfig.VERSION_NAME
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val lastCheckTime = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
        val now = System.currentTimeMillis()

        if (!force && (now - lastCheckTime < CACHE_DURATION_MILLIS)) {
            val cachedVersion = prefs.getString(KEY_CACHED_LATEST_VERSION, null)
            if (cachedVersion != null) {
                val isAvailable = prefs.getBoolean(KEY_CACHED_IS_AVAILABLE, false)
                val releaseNotes = prefs.getString(KEY_CACHED_RELEASE_NOTES, "") ?: ""
                val downloadUrl = prefs.getString(KEY_CACHED_DOWNLOAD_URL, "https://github.com/cyberlog69/MoodTunes/releases") ?: "https://github.com/cyberlog69/MoodTunes/releases"
                val apkDownloadUrl = prefs.getString(KEY_CACHED_APK_URL, "") ?: ""

                Timber.d("UpdateChecker: Using cached result for %s (isAvailable=%b)", cachedVersion, isAvailable)
                return@withContext UpdateCheckResult(
                    isUpdateAvailable = isAvailable,
                    latestVersion = cachedVersion,
                    currentVersion = "v$currentVersion",
                    releaseNotes = releaseNotes,
                    downloadUrl = downloadUrl,
                    apkDownloadUrl = apkDownloadUrl
                )
            }
        }

        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/cyberlog69/MoodTunes/releases/latest")
                .header("User-Agent", "MoodTunes/${BuildConfig.VERSION_NAME} (Android; Music Player App)")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val json = JSONObject(body)
                        val tagName = json.optString("tag_name", "v1.0.0").replace("v", "")
                        val bodyText = json.optString("body", "Bug fixes and performance improvements.")
                        val htmlUrl = json.optString("html_url", "")

                        // SECURITY FIX (S3): Validate download URL must be a GitHub URL — no MITM redirect
                        val safeDownloadUrl = if (htmlUrl.startsWith("https://github.com/cyberlog69/MoodTunes")) {
                            htmlUrl
                        } else {
                            "https://github.com/cyberlog69/MoodTunes/releases"
                        }

                        // Extract direct APK asset download URL
                        var directApkUrl = ""
                        val assets = json.optJSONArray("assets")
                        if (assets != null) {
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                val assetUrl = asset.optString("browser_download_url", "")
                                if (assetUrl.endsWith(".apk") && assetUrl.startsWith("https://github.com/cyberlog69/MoodTunes")) {
                                    directApkUrl = assetUrl
                                    break
                                }
                            }
                        }

                        // Sanitize version string: only allow digits and dots
                        val sanitizedVersion = tagName.filter { it.isDigit() || it == '.' }
                            .ifEmpty { "1.0.0" }

                        if (directApkUrl.isEmpty()) {
                            directApkUrl = "https://github.com/cyberlog69/MoodTunes/releases/download/v$sanitizedVersion/app-debug.apk"
                        }

                        val isNewer = isVersionNewer(currentVersion, sanitizedVersion)
                        val latestVersionTag = "v$sanitizedVersion"

                        // Save in cache
                        prefs.edit()
                            .putLong(KEY_LAST_CHECK_TIME, now)
                            .putBoolean(KEY_CACHED_IS_AVAILABLE, isNewer)
                            .putString(KEY_CACHED_LATEST_VERSION, latestVersionTag)
                            .putString(KEY_CACHED_RELEASE_NOTES, bodyText)
                            .putString(KEY_CACHED_DOWNLOAD_URL, safeDownloadUrl)
                            .putString(KEY_CACHED_APK_URL, directApkUrl)
                            .apply()

                        return@withContext UpdateCheckResult(
                            isUpdateAvailable = isNewer,
                            latestVersion = latestVersionTag,
                            currentVersion = "v$currentVersion",
                            releaseNotes = bodyText,
                            downloadUrl = safeDownloadUrl,
                            apkDownloadUrl = directApkUrl
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // SECURITY FIX (S9): No printStackTrace in production — debug-only logging
            Timber.w(e, "Update check failed")
        }

        UpdateCheckResult(
            isUpdateAvailable = false,
            latestVersion = "v$currentVersion",
            currentVersion = "v$currentVersion",
            releaseNotes = "You are running the latest version of MoodTunes."
        )
    }

    private fun isVersionNewer(current: String, latest: String): Boolean {
        val currParts = current.replace("v", "").split(".").mapNotNull { it.toIntOrNull() }
        val lateParts = latest.replace("v", "").split(".").mapNotNull { it.toIntOrNull() }

        for (i in 0 until maxOf(currParts.size, lateParts.size)) {
            val c = currParts.getOrNull(i) ?: 0
            val l = lateParts.getOrNull(i) ?: 0
            if (l > c) return true
            if (c > l) return false
        }
        return false
    }

    companion object {
        private const val PREFS_NAME = "moodtunes_update_checker_cache"
        private const val KEY_LAST_CHECK_TIME = "last_check_time"
        private const val KEY_CACHED_IS_AVAILABLE = "cached_is_available"
        private const val KEY_CACHED_LATEST_VERSION = "cached_latest_version"
        private const val KEY_CACHED_RELEASE_NOTES = "cached_release_notes"
        private const val KEY_CACHED_DOWNLOAD_URL = "cached_download_url"
        private const val KEY_CACHED_APK_URL = "cached_apk_url"

        private val CACHE_DURATION_MILLIS = TimeUnit.HOURS.toMillis(6)
    }
}
