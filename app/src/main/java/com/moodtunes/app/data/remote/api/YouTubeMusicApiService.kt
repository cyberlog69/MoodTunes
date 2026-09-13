package com.moodtunes.app.data.remote.api

import android.net.Uri
import com.moodtunes.app.domain.model.AudioFormat
import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Direct client-side YouTube Music InnerTube API Service.
 * Provides:
 * - 100% full-track streaming (Opus 160 kbps / AAC) without preview limitations
 * - Global catalog search (songs, artists, albums, playlists)
 * - Curated mood playlists and top trending charts
 * - Auto-radio queue generation for infinite playback
 */
@Singleton
class YouTubeMusicApiService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // In-memory stream URL cache: videoId -> Pair(directStreamUrl, expiryMs)
    private val streamCache = ConcurrentHashMap<String, Pair<String, Long>>()

    companion object {
        private const val BASE_URL = "https://music.youtube.com/youtubei/v1"
        private const val WEB_CLIENT_VERSION = "1.20240101.01.00"
        private const val ANDROID_CLIENT_VERSION = "6.42.52"
        private const val IOS_CLIENT_VERSION = "19.29.1"

        // Search filter param targeting song results specifically
        private const val SONGS_FILTER_PARAM = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
    }

    /**
     * Searches YouTube Music for full-length songs matching [query].
     */
    suspend fun searchSongs(query: String, limit: Int = 20): List<Song> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            val bodyJson = JSONObject().apply {
                put("context", createWebContext())
                put("query", query.trim())
                put("params", SONGS_FILTER_PARAM)
            }

            val request = Request.Builder()
                .url("$BASE_URL/search")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .addHeader("X-YouTube-Client-Name", "67")
                .addHeader("X-YouTube-Client-Version", WEB_CLIENT_VERSION)
                .addHeader("Origin", "https://music.youtube.com")
                .addHeader("Referer", "https://music.youtube.com/")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("YouTube Music search returned HTTP ${response.code}")
                    return@withContext fallbackGeneralSearch(query, limit)
                }
                val responseStr = response.body?.string() ?: return@withContext emptyList()
                val parsed = parseSearchResults(JSONObject(responseStr), limit)
                if (parsed.isNotEmpty()) parsed else fallbackGeneralSearch(query, limit)
            }
        }.getOrElse { e ->
            Timber.e(e, "YouTube Music search failed for: $query")
            fallbackGeneralSearch(query, limit)
        }
    }

    /**
     * Fallback search without songs-only filter parameter in case of strict filters.
     */
    private suspend fun fallbackGeneralSearch(query: String, limit: Int): List<Song> = withContext(Dispatchers.IO) {
        runCatching {
            val bodyJson = JSONObject().apply {
                put("context", createWebContext())
                put("query", query.trim())
            }

            val request = Request.Builder()
                .url("$BASE_URL/search")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .addHeader("X-YouTube-Client-Name", "67")
                .addHeader("X-YouTube-Client-Version", WEB_CLIENT_VERSION)
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val responseStr = response.body?.string() ?: return@withContext emptyList()
                parseSearchResults(JSONObject(responseStr), limit)
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Fetches curated songs matching an emotional [mood].
     */
    suspend fun getSongsByMood(mood: MoodType, limit: Int = 20): List<Song> = withContext(Dispatchers.IO) {
        val query = when (mood) {
            MoodType.EUPHORIC -> "Top EDM Dance Festival Hits"
            MoodType.HAPPY -> "Feel Good Happy Pop Hits"
            MoodType.CALM -> "Relaxing Acoustic Coffeehouse Chill"
            MoodType.ENERGETIC -> "High Energy Workout Gym Motivation"
            MoodType.SAD -> "Emotional Sad Acoustic Songs"
            MoodType.SLEEP -> "Deep Sleep Ambient Piano Rain"
        }
        val tracks = searchSongs(query, limit)
        tracks.map { it.copy(moodTags = listOf(mood)) }
    }

    /**
     * Fetches trending and top chart songs.
     */
    suspend fun getTrendingSongs(category: String, limit: Int = 20): List<Song> = withContext(Dispatchers.IO) {
        val query = when (category.lowercase()) {
            "top hits", "top tracks" -> "Today's Top Hits"
            "new releases" -> "New Music Friday"
            "chill" -> "Lo-Fi Beats Chill"
            "party" -> "Party Dance Bangers"
            "regional", "bollywood" -> "Top Bollywood Songs"
            else -> category
        }
        searchSongs(query, limit)
    }

    /**
     * Resolves the direct, un-throttled HTTPS audio streaming URL (Opus/AAC) for a [videoId].
     * First checks in-memory cache, then queries InnerTube Player endpoint.
     */
    suspend fun resolveStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        val cleanId = extractVideoId(videoId)
        if (cleanId.isBlank()) return@withContext null

        // 1. Check in-memory cache
        val cached = streamCache[cleanId]
        val now = System.currentTimeMillis()
        if (cached != null && now < cached.second) {
            return@withContext cached.first
        }

        // 2. Query InnerTube Player API using ANDROID_MUSIC client context
        var streamUrl = fetchStreamWithClient(cleanId, isAndroid = true)

        // 3. Fallback to iOS client context if Android context fails
        if (streamUrl.isNullOrBlank()) {
            streamUrl = fetchStreamWithClient(cleanId, isAndroid = false)
        }

        if (!streamUrl.isNullOrBlank()) {
            // Default stream expiration is ~5 hours; cache for 4 hours
            val ttl = now + TimeUnit.HOURS.toMillis(4)
            streamCache[cleanId] = Pair(streamUrl, ttl)
        }

        streamUrl
    }

    private fun fetchStreamWithClient(videoId: String, isAndroid: Boolean): String? {
        return runCatching {
            val bodyJson = JSONObject().apply {
                put("context", if (isAndroid) createAndroidContext() else createIosContext())
                put("videoId", videoId)
            }

            val request = Request.Builder()
                .url("$BASE_URL/player")
                .apply {
                    if (isAndroid) {
                        addHeader("User-Agent", "com.google.android.apps.youtube.music/$ANDROID_CLIENT_VERSION (Linux; U; Android 14) gzip")
                        addHeader("X-YouTube-Client-Name", "21")
                        addHeader("X-YouTube-Client-Version", ANDROID_CLIENT_VERSION)
                    } else {
                        addHeader("User-Agent", "com.google.ios.youtubemusic/$IOS_CLIENT_VERSION (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X)")
                        addHeader("X-YouTube-Client-Name", "5")
                        addHeader("X-YouTube-Client-Version", IOS_CLIENT_VERSION)
                    }
                }
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val responseStr = response.body?.string() ?: return null
                val root = JSONObject(responseStr)
                val streamingData = root.optJSONObject("streamingData") ?: return null
                val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats") ?: return null

                // Filter for audio streams and find best bitrate
                var bestUrl: String? = null
                var maxBitrate = 0

                for (i in 0 until adaptiveFormats.length()) {
                    val format = adaptiveFormats.getJSONObject(i)
                    val mimeType = format.optString("mimeType", "")
                    val url = format.optString("url", "")

                    if (mimeType.startsWith("audio/") && url.isNotBlank()) {
                        val bitrate = format.optInt("bitrate", 0)
                        if (bitrate > maxBitrate) {
                            maxBitrate = bitrate
                            bestUrl = url
                        }
                    }
                }
                bestUrl
            }
        }.getOrNull()
    }

    /**
     * Fetches continuous radio queue recommendations for a given [videoId].
     */
    suspend fun getRadioQueue(videoId: String, limit: Int = 20): List<Song> = withContext(Dispatchers.IO) {
        val cleanId = extractVideoId(videoId)
        if (cleanId.isBlank()) return@withContext emptyList()

        runCatching {
            val bodyJson = JSONObject().apply {
                put("context", createWebContext())
                put("videoId", cleanId)
            }

            val request = Request.Builder()
                .url("$BASE_URL/next")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("X-YouTube-Client-Name", "67")
                .addHeader("X-YouTube-Client-Version", WEB_CLIENT_VERSION)
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val responseStr = response.body?.string() ?: return@withContext emptyList()
                parseRadioResults(JSONObject(responseStr), limit)
            }
        }.getOrDefault(emptyList())
    }

    // ─── JSON Parsing Helpers ──────────────────────────────────────────────────

    private fun parseSearchResults(root: JSONObject, limit: Int): List<Song> {
        val results = mutableListOf<Song>()
        runCatching {
            // Traverse tabbed search results
            val tabbed = root.optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return results

            for (s in 0 until tabbed.length()) {
                val section = tabbed.optJSONObject(s) ?: continue
                val items = section.optJSONObject("musicShelfRenderer")?.optJSONArray("contents")
                    ?: section.optJSONObject("musicCardShelfRenderer")?.let { card ->
                        JSONArray().apply { put(card) }
                    } ?: continue

                for (i in 0 until items.length()) {
                    if (results.size >= limit) break
                    val item = items.optJSONObject(i) ?: continue
                    val song = parseMusicItem(item)
                    if (song != null) {
                        results.add(song)
                    }
                }
            }
        }
        return results
    }

    private fun parseMusicItem(item: JSONObject): Song? {
        val renderer = item.optJSONObject("musicResponsiveListItemRenderer")
            ?: item.optJSONObject("musicCardShelfRenderer")
            ?: return null

        // 1. Extract Video ID
        var videoId = renderer.optJSONObject("playlistItemData")?.optString("videoId")
        if (videoId.isNullOrBlank()) {
            videoId = renderer.optJSONObject("navigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")
        }
        if (videoId.isNullOrBlank()) {
            videoId = extractVideoIdFromMenu(renderer)
        }
        if (videoId.isNullOrBlank()) return null

        // 2. Extract Title
        val flexColumns = renderer.optJSONArray("flexColumns")
        val title = if (flexColumns != null && flexColumns.length() > 0) {
            extractRunsText(flexColumns.optJSONObject(0))
        } else {
            renderer.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
        } ?: "Unknown Title"

        // 3. Extract Artist and Album
        var artist = "YouTube Music Artist"
        var album = "YouTube Music"

        if (flexColumns != null && flexColumns.length() > 1) {
            val subtitleCol = flexColumns.optJSONObject(1)
            val runs = subtitleCol?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")

            if (runs != null && runs.length() > 0) {
                artist = runs.optJSONObject(0)?.optString("text") ?: artist
                if (runs.length() > 2) {
                    val potentialAlbum = runs.optJSONObject(2)?.optString("text")
                    if (!potentialAlbum.isNullOrBlank() && !potentialAlbum.contains(":")) {
                        album = potentialAlbum
                    }
                }
            }
        }

        // 4. Extract High-Res Thumbnail
        val thumbnails = renderer.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")

        var artworkUri: Uri? = null
        if (thumbnails != null && thumbnails.length() > 0) {
            val lastThumb = thumbnails.optJSONObject(thumbnails.length() - 1)
            val rawUrl = lastThumb?.optString("url")
            if (!rawUrl.isNullOrBlank()) {
                // Upscale to high-res album art where available
                val upscaled = rawUrl.replace(Regex("=w\\d+-h\\d+"), "=w544-h544")
                artworkUri = Uri.parse(upscaled)
            }
        }

        // 5. Extract Duration
        var durationMs = 210_000L // 3:30 fallback
        if (flexColumns != null && flexColumns.length() > 1) {
            val runs = flexColumns.optJSONObject(1)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")
            if (runs != null) {
                for (r in 0 until runs.length()) {
                    val text = runs.optJSONObject(r)?.optString("text") ?: ""
                    if (text.contains(":") && text.length in 3..6) {
                        durationMs = parseDurationText(text)
                        break
                    }
                }
            }
        }

        // Generate stable 64-bit ID from videoId hash
        val stableId = (cleanPositiveHash(videoId) or (1L shl 60))

        return Song(
            id = stableId,
            title = title.trim(),
            artist = artist.trim(),
            album = album.trim(),
            duration = durationMs,
            uri = Uri.parse("https://music.youtube.com/watch?v=$videoId"),
            albumArtUri = artworkUri,
            genre = "YouTube Music",
            isStream = true,
            audioFormat = AudioFormat.STREAM
        )
    }

    private fun parseRadioResults(root: JSONObject, limit: Int): List<Song> {
        val results = mutableListOf<Song>()
        runCatching {
            val tabs = root.optJSONObject("contents")
                ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
                ?.optJSONObject("tabbedRenderer")
                ?.optJSONObject("watchNextTabbedResultsRenderer")
                ?.optJSONArray("tabs") ?: return results

            val queueContent = tabs.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("musicQueueRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("playlistPanelRenderer")
                ?.optJSONArray("contents") ?: return results

            for (i in 0 until queueContent.length()) {
                if (results.size >= limit) break
                val panelItem = queueContent.optJSONObject(i)?.optJSONObject("playlistPanelVideoRenderer") ?: continue
                val videoId = panelItem.optString("videoId")
                if (videoId.isBlank()) continue

                val title = panelItem.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Track"
                val artist = panelItem.optJSONObject("longBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Artist"
                val thumb = panelItem.optJSONObject("thumbnail")?.optJSONArray("thumbnails")?.let {
                    it.optJSONObject(it.length() - 1)?.optString("url")
                }

                results.add(
                    Song(
                        id = (cleanPositiveHash(videoId) or (1L shl 60)),
                        title = title,
                        artist = artist,
                        album = "Up Next Radio",
                        duration = 200_000L,
                        uri = Uri.parse("https://music.youtube.com/watch?v=$videoId"),
                        albumArtUri = thumb?.let { Uri.parse(it) },
                        genre = "Radio",
                        isStream = true,
                        audioFormat = AudioFormat.STREAM
                    )
                )
            }
        }
        return results
    }

    private fun extractVideoIdFromMenu(renderer: JSONObject): String? {
        val items = renderer.optJSONObject("menu")
            ?.optJSONObject("menuRenderer")
            ?.optJSONArray("items") ?: return null

        for (i in 0 until items.length()) {
            val serviceEndpoint = items.optJSONObject(i)
                ?.optJSONObject("menuNavigationItemRenderer")
                ?.optJSONObject("navigationEndpoint")
                ?: continue

            val id = serviceEndpoint.optJSONObject("watchEndpoint")?.optString("videoId")
            if (!id.isNullOrBlank()) return id
        }
        return null
    }

    private fun extractRunsText(flexCol: JSONObject?): String? {
        val runs = flexCol?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs") ?: return null
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text", ""))
        }
        return sb.toString().takeIf { it.isNotBlank() }
    }

    private fun parseDurationText(duration: String): Long {
        return runCatching {
            val parts = duration.split(":")
            if (parts.size == 2) {
                val mins = parts[0].toLong()
                val secs = parts[1].toLong()
                (mins * 60 + secs) * 1000L
            } else if (parts.size == 3) {
                val hrs = parts[0].toLong()
                val mins = parts[1].toLong()
                val secs = parts[2].toLong()
                (hrs * 3600 + mins * 60 + secs) * 1000L
            } else 210_000L
        }.getOrDefault(210_000L)
    }

    fun extractVideoId(rawUriOrId: String): String {
        if (!rawUriOrId.contains("/") && !rawUriOrId.contains("?") && rawUriOrId.length in 10..15) {
            return rawUriOrId
        }
        return runCatching {
            val uri = Uri.parse(rawUriOrId)
            uri.getQueryParameter("v")
                ?: uri.lastPathSegment
                ?: rawUriOrId
        }.getOrDefault(rawUriOrId)
    }

    private fun cleanPositiveHash(str: String): Long {
        return (str.hashCode().toLong() and 0xFFFFFFFFL).coerceAtLeast(1L)
    }

    // ─── InnerTube Client Contexts ─────────────────────────────────────────────

    private fun createWebContext(): JSONObject {
        return JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "WEB_REMIX")
                put("clientVersion", WEB_CLIENT_VERSION)
                put("hl", "en")
                put("gl", "US")
            })
        }
    }

    private fun createAndroidContext(): JSONObject {
        return JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "ANDROID_MUSIC")
                put("clientVersion", ANDROID_CLIENT_VERSION)
                put("androidSdkVersion", 34)
                put("hl", "en")
                put("gl", "US")
            })
        }
    }

    private fun createIosContext(): JSONObject {
        return JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "IOS")
                put("clientVersion", IOS_CLIENT_VERSION)
                put("deviceModel", "iPhone16,2")
                put("hl", "en")
                put("gl", "US")
            })
        }
    }
}
