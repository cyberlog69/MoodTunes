package com.moodtunes.app.data.remote.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

data class LrclibResult(
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val syncedLyrics: String?,
    val plainLyrics: String?,
    val durationSeconds: Int? = null,
    val isWordSynced: Boolean = false
)

@Singleton
class LrclibService @Inject constructor() {

    private val client = OkHttpClient()

    /**
     * Sanitizes noisy track titles from YouTube Music (removes official video, ft., remastered, etc.)
     */
    fun sanitizeTitle(rawTitle: String): String {
        return rawTitle
            .replace(Regex("(?i)\\s*\\(?(official\\s*(music)?\\s*video|audio|lyrics?|lyric video|visualizer|4k|hd|remaster(ed)?|clean|explicit|slowed\\s*\\+?\\s*reverb)\\)?"), "")
            .replace(Regex("(?i)\\s*\\[?(official\\s*(music)?\\s*video|audio|lyrics?|lyric video|visualizer|4k|hd|remaster(ed)?|clean|explicit)\\]?"), "")
            .replace(Regex("(?i)\\s*\\(?feat\\.?\\s+[^)]+\\)?"), "")
            .replace(Regex("(?i)\\s*\\[?feat\\.?\\s+[^]]+\\]?"), "")
            .replace(Regex("(?i)\\s*\\(?ft\\.?\\s+[^)]+\\)?"), "")
            .replace(Regex("(?i)\\s*\\[?ft\\.?\\s+[^]]+\\]?"), "")
            .trim()
    }

    /**
     * Fetches word-by-word or line-synced lyrics with intelligent fallbacks:
     * 1. BetterLyrics TTML API (word-by-word timestamps)
     * 2. LRCLIB exact GET (`/api/get`)
     * 3. LRCLIB search fallback (`/api/search`) with duration tolerance
     */
    suspend fun getLyrics(trackName: String, artistName: String, durationSeconds: Int? = null): LrclibResult? = withContext(Dispatchers.IO) {
        val cleanTitle = sanitizeTitle(trackName).ifBlank { trackName }
        val cleanArtist = sanitizeTitle(artistName).ifBlank { artistName }

        // 1. Try BetterLyrics TTML word-by-word API first
        val ttmlResult = fetchBetterLyricsTTML(cleanTitle, cleanArtist, durationSeconds)
        if (ttmlResult != null && !ttmlResult.syncedLyrics.isNullOrBlank()) {
            return@withContext ttmlResult
        }

        // 2. Try LRCLIB exact match
        val exactResult = fetchLrclibExact(cleanTitle, cleanArtist, durationSeconds)
        if (exactResult != null && !exactResult.syncedLyrics.isNullOrBlank()) {
            return@withContext exactResult
        }

        // 3. Fallback to LRCLIB search query
        val searchResult = searchLyricsFallback(cleanTitle, cleanArtist, durationSeconds)
        if (searchResult != null) {
            return@withContext searchResult
        }

        // If exact match gave at least plain lyrics, return that
        exactResult
    }

    private fun fetchBetterLyricsTTML(trackName: String, artistName: String, durationSeconds: Int?): LrclibResult? {
        val t = URLEncoder.encode(trackName, "UTF-8")
        val a = URLEncoder.encode(artistName, "UTF-8")
        var url = "https://lyrics-api.boidu.dev/getLyrics?s=$t&a=$a"
        if (durationSeconds != null && durationSeconds > 0) {
            url += "&d=$durationSeconds"
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; MoodTunes)")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val ttml = json.optString("ttml")
                if (ttml.isNotBlank() && ttml.contains("<p")) {
                    LrclibResult(
                        trackName = trackName,
                        artistName = artistName,
                        albumName = null,
                        syncedLyrics = ttml,
                        plainLyrics = null,
                        durationSeconds = durationSeconds,
                        isWordSynced = true
                    )
                } else null
            }
        } catch (e: Exception) {
            Timber.d("BetterLyrics TTML not available for $trackName: ${e.message}")
            null
        }
    }

    private fun fetchLrclibExact(trackName: String, artistName: String, durationSeconds: Int?): LrclibResult? {
        val t = URLEncoder.encode(trackName, "UTF-8")
        val a = URLEncoder.encode(artistName, "UTF-8")
        var url = "https://lrclib.net/api/get?track_name=$t&artist_name=$a"
        if (durationSeconds != null && durationSeconds > 0) {
            url += "&duration=$durationSeconds"
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MoodTunes/1.4.0 (Android; Music Player)")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                parseLrclibResult(JSONObject(body))
            }
        } catch (e: Exception) {
            Timber.d("LRCLIB exact fetch failed for $trackName: ${e.message}")
            null
        }
    }

    private suspend fun searchLyricsFallback(trackName: String, artistName: String, durationSeconds: Int?): LrclibResult? {
        val query = "$trackName $artistName"
        val candidates = searchLyrics(query)
        if (candidates.isEmpty()) return null

        // Filter and sort: prefer synced lyrics within duration tolerance
        return candidates.minByOrNull { candidate ->
            var score = 0
            if (candidate.syncedLyrics.isNullOrBlank()) score += 100
            if (durationSeconds != null && candidate.durationSeconds != null) {
                score += kotlin.math.abs(candidate.durationSeconds - durationSeconds)
            }
            score
        }
    }

    suspend fun searchLyrics(query: String): List<LrclibResult> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://lrclib.net/api/search?q=$encodedQuery"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MoodTunes/1.4.0 (Android; Music Player)")
            .build()

        val results = mutableListOf<LrclibResult>()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val jsonArray = JSONArray(body)
                for (i in 0 until jsonArray.length()) {
                    val jsonObj = jsonArray.optJSONObject(i) ?: continue
                    val result = parseLrclibResult(jsonObj)
                    if (result != null) results.add(result)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Exception in LRCLIB search")
        }
        results
    }

    private fun parseLrclibResult(jsonObject: JSONObject): LrclibResult? {
        val trackName = jsonObject.optString("trackName")
        val artistName = jsonObject.optString("artistName")
        if (trackName.isEmpty() || artistName.isEmpty()) return null

        val albumName = jsonObject.optString("albumName").takeIf { it.isNotEmpty() }
        val syncedLyrics = jsonObject.optString("syncedLyrics").takeIf { it.isNotEmpty() }
        val plainLyrics = jsonObject.optString("plainLyrics").takeIf { it.isNotEmpty() }
        val duration = jsonObject.optInt("duration", -1).takeIf { it > 0 }

        return LrclibResult(
            trackName = trackName,
            artistName = artistName,
            albumName = albumName,
            syncedLyrics = syncedLyrics,
            plainLyrics = plainLyrics,
            durationSeconds = duration,
            isWordSynced = syncedLyrics?.contains("<") == true
        )
    }
}
