package com.moodtunes.app.data.remote

import android.util.Log
import com.moodtunes.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateCheckResult(
    val isUpdateAvailable: Boolean,
    val latestVersion: String,
    val currentVersion: String = "1.0.0",
    val releaseNotes: String = "",
    val downloadUrl: String = "https://github.com/cyberlog69/MoodTunes/releases"
)

@Singleton
class UpdateChecker @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun checkForUpdates(currentVersion: String = "1.0.0"): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/cyberlog69/MoodTunes/releases/latest")
                .header("User-Agent", "MoodTunes/1.0 (Android; Music Player App)")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val json = JSONObject(body)
                        val tagName = json.optString("tag_name", "v1.0.0").replace("v", "")
                        val bodyText = json.optString("body", "Bug fixes and performance improvements.")
                            .take(500) // Limit release notes length
                        val htmlUrl = json.optString("html_url", "")

                        // SECURITY FIX (S3): Validate download URL must be a GitHub URL — no MITM redirect
                        val safeDownloadUrl = if (htmlUrl.startsWith("https://github.com/cyberlog69/MoodTunes")) {
                            htmlUrl
                        } else {
                            "https://github.com/cyberlog69/MoodTunes/releases"
                        }

                        // Sanitize version string: only allow digits and dots
                        val sanitizedVersion = tagName.filter { it.isDigit() || it == '.' }
                            .ifEmpty { "1.0.0" }

                        val isNewer = isVersionNewer(currentVersion, sanitizedVersion)
                        return@withContext UpdateCheckResult(
                            isUpdateAvailable = isNewer,
                            latestVersion = "v$sanitizedVersion",
                            currentVersion = "v$currentVersion",
                            releaseNotes = bodyText,
                            downloadUrl = safeDownloadUrl
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // SECURITY FIX (S9): No printStackTrace in production — debug-only logging
            if (BuildConfig.DEBUG) Log.w(TAG, "Update check failed", e)
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
        private const val TAG = "UpdateChecker"
    }
}
