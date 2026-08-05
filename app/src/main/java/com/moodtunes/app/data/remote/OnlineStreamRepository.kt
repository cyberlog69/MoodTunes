package com.moodtunes.app.data.remote

import android.net.Uri
import android.util.Log
import com.moodtunes.app.BuildConfig
import com.moodtunes.app.domain.model.AudioFormat
import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles global & Indian ISP (Jio, Airtel, Vi, BSNL, ACT) ultra-low-latency music streaming
 * using parallel host failover pools across Audius Protocol and YouTube (via Piped/Invidious).
 *
 * Security:
 * - COPYRIGHT FIX (C3): Uses honest MoodTunes User-Agent — no browser impersonation.
 * - SECURITY FIX (S4): All resolved stream URLs are validated for https:// scheme before use.
 * - SECURITY FIX (S8): @Volatile on activeAudiusHost prevents thread-safety race conditions.
 * - SECURITY FIX (S10): No printStackTrace() in production; debug logging only.
 */
@Singleton
class OnlineStreamRepository @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // ─── Audius Host Discovery Pool (with Indian & Asia ISP mirrors) ─────────
    private val defaultAudiusHosts = listOf(
        "https://discoveryprovider.audius.co",
        "https://audius-dp.trendigo.com",
        "https://audius-discovery-1.cultur3lt.com",
        "https://discovery-provider.audius.co",
        "https://dn2.audius.co"
    )

    // ─── Piped (YouTube) API Fast Proxy Instances Pool ────────────────────────
    private val pipedInstances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://api.piped.privacydev.net",
        "https://pipedapi.tokhmi.xyz",
        "https://pipedapi.garudalinux.org",
        "https://piped-api.lunar.icu"
    )

    // SECURITY FIX (S8): @Volatile prevents stale cache reads across threads
    @Volatile private var activeAudiusHost: String? = null

    /**
     * Resolves active Audius discovery provider host.
     */
    private suspend fun getAudiusHost(): String = withContext(Dispatchers.IO) {
        activeAudiusHost?.let { return@withContext it }

        for (host in defaultAudiusHosts) {
            try {
                val request = Request.Builder()
                    .url("$host/v1/tracks/trending?app_name=MoodTunes&limit=1")
                    .header("User-Agent", USER_AGENT)
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        activeAudiusHost = host
                        return@withContext host
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Audius host failed: $host", e)
            }
        }

        defaultAudiusHosts.first()
    }

    /**
     * Searches Audius tracks by mood keyword and language preference.
     * Audius is a decentralised, royalty-free streaming protocol — legally safe to use.
     */
    suspend fun getAudiusTracksByMood(
        mood: MoodType,
        language: com.moodtunes.app.data.local.preferences.MusicLanguage = com.moodtunes.app.data.local.preferences.MusicLanguage.ALL,
        limit: Int = 8
    ): List<Song> = withContext(Dispatchers.IO) {
        val host = getAudiusHost()
        val songs = mutableListOf<Song>()
        val keyword = mood.keywords.firstOrNull() ?: mood.displayName
        val langPrefix = if (language != com.moodtunes.app.data.local.preferences.MusicLanguage.ALL) "${language.searchQueryPrefix} " else ""
        val searchQuery = "$langPrefix$keyword"

        try {
            val url = "$host/v1/tracks/search?query=${Uri.encode(searchQuery)}&app_name=MoodTunes&limit=$limit"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext emptyList()
                    val json = JSONObject(body)
                    val data = json.getJSONArray("data")

                    for (i in 0 until data.length()) {
                        val track = data.getJSONObject(i)
                        val trackId = track.getString("id")
                        val title = track.optString("title", "Unknown Track")
                        val userObj = track.optJSONObject("user")
                        val artist = userObj?.optString("name") ?: "Audius Artist"
                        val duration = track.optLong("duration", 180) * 1000L
                        val artworkObj = track.optJSONObject("artwork")
                        val artUri = artworkObj?.optString("480x480")
                            ?: artworkObj?.optString("150x150")

                        val streamUrl = "$host/v1/tracks/$trackId/stream?app_name=MoodTunes"

                        songs.add(
                            Song(
                                id = trackId.hashCode().toLong() and 0x7FFFFFFF,
                                title = title,
                                artist = artist,
                                album = "Audius Stream",
                                duration = duration,
                                uri = Uri.parse(streamUrl),
                                albumArtUri = artUri?.let { Uri.parse(it) },
                                genre = mood.displayName,
                                audioFormat = AudioFormat.STREAM,
                                isStream = true,
                                moodTags = listOf(mood)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Audius search failed for mood ${mood.displayName}", e)
        }
        songs
    }

    /**
     * Searches YouTube audio tracks via Piped instances pool with fast stream resolution.
     */
    suspend fun getYouTubeAudioTracksByMood(
        mood: MoodType,
        language: com.moodtunes.app.data.local.preferences.MusicLanguage = com.moodtunes.app.data.local.preferences.MusicLanguage.ALL,
        limit: Int = 6
    ): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val langPrefix = if (language != com.moodtunes.app.data.local.preferences.MusicLanguage.ALL) "${language.searchQueryPrefix} " else ""
        val query = "$langPrefix${mood.displayName} music"

        for (pipedBase in pipedInstances) {
            try {
                val url = "$pipedBase/search?q=${Uri.encode(query)}&filter=music"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use
                        val json = JSONObject(body)
                        val items = json.optJSONArray("items") ?: JSONArray()

                        for (i in 0 until items.length().coerceAtMost(limit)) {
                            val item = items.getJSONObject(i)
                            val type = item.optString("type")
                            if (type == "stream" || type == "music") {
                                val urlPath = item.optString("url", "")
                                val videoId = urlPath.replace("/watch?v=", "")
                                val title = item.optString("title", "YouTube Track")
                                val uploaderName = item.optString("uploaderName", "YouTube Music")
                                val thumbnail = item.optString("thumbnail", "")
                                val duration = item.optLong("duration", 200) * 1000L

                                if (videoId.isNotEmpty()) {
                                    val streamUrl = "$pipedBase/streams/$videoId"

                                    songs.add(
                                        Song(
                                            id = videoId.hashCode().toLong() and 0x7FFFFFFF,
                                            title = title,
                                            artist = uploaderName,
                                            album = "YouTube Stream",
                                            duration = duration,
                                            uri = Uri.parse(streamUrl),
                                            albumArtUri = if (thumbnail.isNotEmpty()) Uri.parse(thumbnail) else null,
                                            genre = mood.displayName,
                                            audioFormat = AudioFormat.STREAM,
                                            isStream = true,
                                            moodTags = listOf(mood)
                                        )
                                    )
                                }
                            }
                        }
                        if (songs.isNotEmpty()) return@withContext songs
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Piped instance failed: $pipedBase", e)
            }
        }

        songs
    }

    /**
     * Resolves direct audio stream URL in parallel to avoid playback delay when selected.
     * SECURITY FIX (S4): Validates resolved URL must use https:// scheme.
     */
    suspend fun resolveDirectStreamUrl(streamUrl: String): String = withContext(Dispatchers.IO) {
        if (!streamUrl.contains("/streams/")) return@withContext streamUrl

        try {
            val request = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext streamUrl
                    val json = JSONObject(body)
                    val audioStreams = json.optJSONArray("audioStreams") ?: JSONArray()
                    if (audioStreams.length() > 0) {
                        val firstStream = audioStreams.getJSONObject(0)
                        val directUrl = firstStream.optString("url")
                        // SECURITY FIX (S4): Only accept https:// URLs from proxy responses
                        if (directUrl.startsWith("https://")) {
                            return@withContext directUrl
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Stream URL resolution failed", e)
        }

        streamUrl
    }

    /** Parallel fetching of both Audius and YouTube online tracks for a mood and language */
    suspend fun fetchAllOnlineTracksForMood(
        mood: MoodType,
        language: com.moodtunes.app.data.local.preferences.MusicLanguage = com.moodtunes.app.data.local.preferences.MusicLanguage.ALL
    ): List<Song> = coroutineScope {
        val audiusDeferred = async { runCatching { getAudiusTracksByMood(mood, language) }.getOrDefault(emptyList()) }
        val ytDeferred = async { runCatching { getYouTubeAudioTracksByMood(mood, language) }.getOrDefault(emptyList()) }

        val audiusResult = audiusDeferred.await()
        val ytResult = ytDeferred.await()

        audiusResult + ytResult
    }

    /**
     * Fetches general trending music tracks based on the user's preferred languages and optional genre category.
     * Used by the Songs Hub — NOT restricted to mood keywords.
     */
    suspend fun getGeneralTrendingSongs(
        languages: Set<com.moodtunes.app.data.local.preferences.MusicLanguage> = setOf(com.moodtunes.app.data.local.preferences.MusicLanguage.ALL),
        categoryQuery: String = "Top Hits",
        limit: Int = 14
    ): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val selectedLangs = languages.filter { it != com.moodtunes.app.data.local.preferences.MusicLanguage.ALL }

        val queries = if (selectedLangs.isNotEmpty()) {
            selectedLangs.map { "${it.searchQueryPrefix} $categoryQuery" }
        } else {
            listOf("Top $categoryQuery", "Trending Hits", "Popular Music")
        }

        val host = getAudiusHost()

        for (q in queries) {
            // 1. Search Audius
            try {
                val audiusUrl = "$host/v1/tracks/search?query=${Uri.encode(q)}&app_name=MoodTunes&limit=${limit / 2}"
                val request = Request.Builder().url(audiusUrl).header("User-Agent", USER_AGENT).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null) {
                            val json = JSONObject(body)
                            val data = json.getJSONArray("data")
                            for (i in 0 until data.length()) {
                                val track = data.getJSONObject(i)
                                val trackId = track.getString("id")
                                val title = track.optString("title", "Unknown Track")
                                val userObj = track.optJSONObject("user")
                                val artist = userObj?.optString("name") ?: "Audius Artist"
                                val duration = track.optLong("duration", 180) * 1000L
                                val artworkObj = track.optJSONObject("artwork")
                                val artUri = artworkObj?.optString("480x480") ?: artworkObj?.optString("150x150")
                                val streamUrl = "$host/v1/tracks/$trackId/stream?app_name=MoodTunes"

                                songs.add(
                                    Song(
                                        id = trackId.hashCode().toLong() and 0x7FFFFFFF,
                                        title = title,
                                        artist = artist,
                                        album = "Online Stream",
                                        duration = duration,
                                        uri = Uri.parse(streamUrl),
                                        albumArtUri = artUri?.let { Uri.parse(it) },
                                        genre = categoryQuery,
                                        audioFormat = AudioFormat.STREAM,
                                        isStream = true
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "General Audius query failed: $q", e)
            }

            // 2. Search YouTube proxy
            for (pipedBase in pipedInstances) {
                try {
                    val ytUrl = "$pipedBase/search?q=${Uri.encode(q)}&filter=music"
                    val request = Request.Builder().url(ytUrl).header("User-Agent", USER_AGENT).build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (body != null) {
                                val json = JSONObject(body)
                                val items = json.optJSONArray("items") ?: JSONArray()
                                for (i in 0 until items.length().coerceAtMost(limit / 2)) {
                                    val item = items.getJSONObject(i)
                                    val type = item.optString("type")
                                    if (type == "stream" || type == "music") {
                                        val urlPath = item.optString("url", "")
                                        val videoId = urlPath.replace("/watch?v=", "")
                                        val title = item.optString("title", "YouTube Track")
                                        val uploaderName = item.optString("uploaderName", "YouTube Music")
                                        val thumbnail = item.optString("thumbnail", "")
                                        val duration = item.optLong("duration", 200) * 1000L

                                        if (videoId.isNotEmpty()) {
                                            val streamUrl = "$pipedBase/streams/$videoId"
                                            songs.add(
                                                Song(
                                                    id = videoId.hashCode().toLong() and 0x7FFFFFFF,
                                                    title = title,
                                                    artist = uploaderName,
                                                    album = "Online Stream",
                                                    duration = duration,
                                                    uri = Uri.parse(streamUrl),
                                                    albumArtUri = if (thumbnail.isNotEmpty()) Uri.parse(thumbnail) else null,
                                                    genre = categoryQuery,
                                                    audioFormat = AudioFormat.STREAM,
                                                    isStream = true
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "Piped instance failed for general query $q", e)
                }
                if (songs.isNotEmpty()) break
            }
        }

        songs.distinctBy { it.id }
    }

    companion object {
        private const val TAG = "OnlineStreamRepository"

        // COPYRIGHT FIX (C3): Honest, transparent User-Agent — no browser impersonation
        private const val USER_AGENT = "MoodTunes/1.0 (Android; Music Player App)"
    }
}
