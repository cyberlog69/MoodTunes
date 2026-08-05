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
 * using parallel host failover pools across:
 *   - Audius Protocol (decentralized royalty-free music)
 *   - Jamendo (Creative Commons 320kbps MP3s)
 *   - Internet Archive (millions of free/open-license audio files)
 *   - Radio Browser (35,000+ live global internet radio stations)
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
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // ─── Audius Host Discovery Pool (Updated 2026) ─────────────────────────────
    private val defaultAudiusHosts = listOf(
        "https://discoveryprovider.audius.co",
        "https://audius-dp.trendigo.com",
        "https://dn1.audius.co",
        "https://dn2.audius.co",
        "https://dn3.audius.co",
        "https://discovery-provider.audius.co"
    )

    // ─── Piped (YouTube) API Fast Proxy Instances Pool (Updated) ──────────────
    // Note: Piped instances are community-run and change frequently.
    // We use a larger pool with longer timeouts for better reliability.
    private val pipedInstances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.tokhmi.xyz",
        "https://api.piped.privacydev.net",
        "https://pipedapi.moomoo.me",
        "https://pipedapi.adminforge.de",
        "https://piped-api.lunar.icu",
        "https://pipedapi.reallyaweso.me"
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
     * Uses an expanded pool of instances for better reliability.
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
                    .header("Accept", "application/json")
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

    /** Parallel fetching of Audius, Internet Archive, and YouTube online tracks for a mood and language */
    suspend fun fetchAllOnlineTracksForMood(
        mood: MoodType,
        language: com.moodtunes.app.data.local.preferences.MusicLanguage = com.moodtunes.app.data.local.preferences.MusicLanguage.ALL
    ): List<Song> = coroutineScope {
        val audiusDeferred = async { runCatching { getAudiusTracksByMood(mood, language) }.getOrDefault(emptyList()) }
        val archiveDeferred = async { runCatching { getInternetArchiveTracksByMood(mood, language) }.getOrDefault(emptyList()) }
        val ytDeferred = async { runCatching { getYouTubeAudioTracksByMood(mood, language) }.getOrDefault(emptyList()) }

        val audiusResult = audiusDeferred.await()
        val archiveResult = archiveDeferred.await()
        val ytResult = ytDeferred.await()

        (audiusResult + archiveResult + ytResult).distinctBy { it.id }
    }

    /**
     * Searches Jamendo Music API for 320 kbps MP3 tracks.
     * FIXED: Now uses `tags` + `fuzzytags` parameters for better results.
     * 100% legal, royalty-free Creative Commons independent music.
     */
    suspend fun getJamendoTracks(
        languages: Set<com.moodtunes.app.data.local.preferences.MusicLanguage> = setOf(com.moodtunes.app.data.local.preferences.MusicLanguage.ALL),
        categoryQuery: String = "Top Hits",
        limit: Int = 10
    ): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val selectedLangs = languages.filter { it != com.moodtunes.app.data.local.preferences.MusicLanguage.ALL }

        // Build multiple search strategies for better results
        val searchStrategies = mutableListOf<String>()

        // Strategy 1: tag-based (most reliable on Jamendo)
        val cleanCategory = categoryQuery.replace(" ", "+").lowercase()
        searchStrategies.add(
            "https://api.jamendo.com/v3.0/tracks/?client_id=56d30c95&format=json&hasimage=true" +
            "&limit=$limit&audioformat=mp32&order=popularity_total" +
            "&tags=${Uri.encode(cleanCategory)}"
        )

        // Strategy 2: fuzzytags (fuzzy match on tags)
        searchStrategies.add(
            "https://api.jamendo.com/v3.0/tracks/?client_id=56d30c95&format=json&hasimage=true" +
            "&limit=$limit&audioformat=mp32&order=popularity_week" +
            "&fuzzytags=${Uri.encode(cleanCategory)}"
        )

        // Strategy 3: search (broadest but least precise)
        val langPrefix = if (selectedLangs.isNotEmpty()) selectedLangs.first().searchQueryPrefix + " " else ""
        val nameQuery = "$langPrefix$categoryQuery".trim()
        searchStrategies.add(
            "https://api.jamendo.com/v3.0/tracks/?client_id=56d30c95&format=json&hasimage=true" +
            "&limit=$limit&audioformat=mp32&order=popularity_total" +
            "&search=${Uri.encode(nameQuery)}"
        )

        // Strategy 4: trending fallback — no filter, just top tracks
        searchStrategies.add(
            "https://api.jamendo.com/v3.0/tracks/?client_id=56d30c95&format=json&hasimage=true" +
            "&limit=$limit&audioformat=mp32&order=popularity_week"
        )

        for (strategyUrl in searchStrategies) {
            if (songs.size >= limit) break
            try {
                val request = Request.Builder().url(strategyUrl).header("User-Agent", USER_AGENT).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null) {
                            val json = JSONObject(body)
                            val results = json.optJSONArray("results") ?: JSONArray()
                            for (i in 0 until results.length()) {
                                val track = results.getJSONObject(i)
                                val id = track.optString("id", "")
                                val name = track.optString("name", "Jamendo Track")
                                val artist = track.optString("artist_name", "Jamendo Artist")
                                val album = track.optString("album_name", "Jamendo Indie")
                                val audioUrl = track.optString("audio", "")
                                val image = track.optString("image", "")
                                val duration = track.optLong("duration", 180) * 1000L

                                if (audioUrl.startsWith("https://")) {
                                    songs.add(
                                        Song(
                                            id = id.hashCode().toLong() and 0x7FFFFFFF,
                                            title = name,
                                            artist = artist,
                                            album = album,
                                            duration = duration,
                                            uri = Uri.parse(audioUrl),
                                            albumArtUri = if (image.isNotEmpty()) Uri.parse(image) else null,
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
                if (songs.size >= 3) break
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Jamendo strategy failed: $strategyUrl", e)
            }
        }

        songs.distinctBy { it.id }
    }

    /**
     * NEW: Searches the Internet Archive (archive.org) for free/open-license audio.
     * Contains millions of songs: folk, classical, jazz, indie, world music.
     * Completely free with no API key required.
     */
    suspend fun getInternetArchiveTracksByMood(
        mood: MoodType,
        language: com.moodtunes.app.data.local.preferences.MusicLanguage = com.moodtunes.app.data.local.preferences.MusicLanguage.ALL,
        limit: Int = 8
    ): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val langPrefix = if (language != com.moodtunes.app.data.local.preferences.MusicLanguage.ALL) "${language.searchQueryPrefix} " else ""
        val keyword = mood.keywords.firstOrNull() ?: mood.displayName
        val query = "$langPrefix$keyword music"

        try {
            val encodedQuery = Uri.encode("$query AND mediatype:audio AND format:MP3")
            val url = "https://archive.org/advancedsearch.php" +
                "?q=$encodedQuery" +
                "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=creator&fl%5B%5D=description" +
                "&rows=$limit&page=1&output=json&sort%5B%5D=downloads+desc"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext emptyList()
                    val json = JSONObject(body)
                    val responseObj = json.optJSONObject("response") ?: return@withContext emptyList()
                    val docs = responseObj.optJSONArray("docs") ?: JSONArray()

                    for (i in 0 until docs.length()) {
                        val doc = docs.getJSONObject(i)
                        val identifier = doc.optString("identifier", "")
                        val title = doc.optString("title", "Archive Audio")
                        val creator = doc.optString("creator", "Internet Archive")

                        if (identifier.isNotEmpty()) {
                            // Stream URL for archive.org (direct MP3)
                            val streamUrl = "https://archive.org/download/$identifier/$identifier.mp3"
                            val artUrl = "https://archive.org/services/img/$identifier"

                            songs.add(
                                Song(
                                    id = identifier.hashCode().toLong() and 0x7FFFFFFF,
                                    title = title,
                                    artist = creator,
                                    album = "🌐 Internet Archive",
                                    duration = 0L,
                                    uri = Uri.parse(streamUrl),
                                    albumArtUri = Uri.parse(artUrl),
                                    genre = mood.displayName,
                                    audioFormat = AudioFormat.STREAM,
                                    isStream = true,
                                    moodTags = listOf(mood)
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Internet Archive search failed for mood ${mood.displayName}", e)
        }
        songs
    }

    /**
     * NEW: General Internet Archive search for the Songs Hub.
     */
    suspend fun getInternetArchiveTracks(
        languages: Set<com.moodtunes.app.data.local.preferences.MusicLanguage> = setOf(com.moodtunes.app.data.local.preferences.MusicLanguage.ALL),
        categoryQuery: String = "Top Hits",
        limit: Int = 10
    ): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val selectedLangs = languages.filter { it != com.moodtunes.app.data.local.preferences.MusicLanguage.ALL }
        val langPrefix = if (selectedLangs.isNotEmpty()) selectedLangs.first().searchQueryPrefix + " " else ""
        val query = "$langPrefix$categoryQuery"

        try {
            val encodedQuery = Uri.encode("$query AND mediatype:audio AND format:MP3")
            val url = "https://archive.org/advancedsearch.php" +
                "?q=$encodedQuery" +
                "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=creator" +
                "&rows=$limit&page=1&output=json&sort%5B%5D=downloads+desc"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext emptyList()
                    val json = JSONObject(body)
                    val responseObj = json.optJSONObject("response") ?: return@withContext emptyList()
                    val docs = responseObj.optJSONArray("docs") ?: JSONArray()

                    for (i in 0 until docs.length()) {
                        val doc = docs.getJSONObject(i)
                        val identifier = doc.optString("identifier", "")
                        val title = doc.optString("title", "Archive Audio")
                        val creator = doc.optString("creator", "Internet Archive")

                        if (identifier.isNotEmpty()) {
                            val streamUrl = "https://archive.org/download/$identifier/$identifier.mp3"
                            val artUrl = "https://archive.org/services/img/$identifier"

                            songs.add(
                                Song(
                                    id = identifier.hashCode().toLong() and 0x7FFFFFFF,
                                    title = title,
                                    artist = creator,
                                    album = "🌐 Internet Archive",
                                    duration = 0L,
                                    uri = Uri.parse(streamUrl),
                                    albumArtUri = Uri.parse(artUrl),
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
            if (BuildConfig.DEBUG) Log.w(TAG, "Internet Archive general search failed: $categoryQuery", e)
        }
        songs
    }

    /**
     * Fetches live 24/7 global internet radio stations via Radio Browser API.
     * Supports language filtering (Hindi, English, Punjabi, Spanish, Tamil, Telugu, K-Pop, etc.).
     */
    suspend fun getGlobalInternetRadioStations(
        languages: Set<com.moodtunes.app.data.local.preferences.MusicLanguage> = setOf(com.moodtunes.app.data.local.preferences.MusicLanguage.ALL),
        categoryQuery: String = "Top Hits",
        limit: Int = 12
    ): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val selectedLangs = languages.filter { it != com.moodtunes.app.data.local.preferences.MusicLanguage.ALL }
        val primaryLang = if (selectedLangs.isNotEmpty()) selectedLangs.first().displayName.lowercase() else ""

        val radioBrowserHosts = listOf(
            "https://de1.api.radio-browser.info",
            "https://at1.api.radio-browser.info",
            "https://nl1.api.radio-browser.info"
        )

        val endpoints = mutableListOf<String>()
        val baseHost = radioBrowserHosts.first()

        if (primaryLang.isNotEmpty()) {
            endpoints.add("$baseHost/json/stations/search?language=${Uri.encode(primaryLang)}&order=clickcount&reverse=true&limit=$limit")
        }
        endpoints.add("$baseHost/json/stations/search?tag=${Uri.encode(categoryQuery)}&order=clickcount&reverse=true&limit=$limit")
        endpoints.add("$baseHost/json/stations/search?name=${Uri.encode(categoryQuery)}&order=votes&reverse=true&limit=$limit")
        // Fallback: just top stations by click
        endpoints.add("$baseHost/json/stations?order=clickcount&reverse=true&limit=$limit")

        for (url in endpoints) {
            try {
                val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null) {
                            val stations = JSONArray(body)
                            for (i in 0 until stations.length()) {
                                val st = stations.getJSONObject(i)
                                val name = st.optString("name", "Live Radio").trim()
                                val streamUrl = st.optString("url_resolved", st.optString("url", ""))
                                val favicon = st.optString("favicon", "")
                                val codec = st.optString("codec", "MP3")
                                val bitrate = st.optInt("bitrate", 128)
                                val country = st.optString("country", "Global")

                                if (streamUrl.startsWith("http://") || streamUrl.startsWith("https://")) {
                                    songs.add(
                                        Song(
                                            id = (name + streamUrl).hashCode().toLong() and 0x7FFFFFFF,
                                            title = "📻 $name",
                                            artist = "$country • $codec ${bitrate}kbps",
                                            album = "Live Internet Radio 24/7",
                                            duration = 0L,
                                            uri = Uri.parse(streamUrl),
                                            albumArtUri = if (favicon.startsWith("http")) Uri.parse(favicon) else null,
                                            genre = "Live Radio",
                                            audioFormat = AudioFormat.STREAM,
                                            isStream = true
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                if (songs.isNotEmpty()) break
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Radio Browser endpoint failed: $url", e)
            }
        }

        songs.distinctBy { it.uri.toString() }
    }

    /**
     * Fetches general music & radio streams combining Audius, Jamendo, Internet Archive and Radio Browser.
     * Used by the Songs Hub.
     */
    suspend fun getGeneralTrendingSongs(
        languages: Set<com.moodtunes.app.data.local.preferences.MusicLanguage> = setOf(com.moodtunes.app.data.local.preferences.MusicLanguage.ALL),
        categoryQuery: String = "Top Hits",
        limit: Int = 14
    ): List<Song> = coroutineScope {
        if (categoryQuery.contains("Radio") || categoryQuery.contains("📻")) {
            return@coroutineScope getGlobalInternetRadioStations(languages, categoryQuery, limit)
        }

        val jamendoDeferred = async { runCatching { getJamendoTracks(languages, categoryQuery, limit = 8) }.getOrDefault(emptyList()) }
        val archiveDeferred = async { runCatching { getInternetArchiveTracks(languages, categoryQuery, limit = 8) }.getOrDefault(emptyList()) }
        val radioDeferred = async { runCatching { getGlobalInternetRadioStations(languages, categoryQuery, limit = 5) }.getOrDefault(emptyList()) }

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

            // 2. Try Piped (YouTube) — best effort, non-blocking
            for (pipedBase in pipedInstances) {
                try {
                    val ytUrl = "$pipedBase/search?q=${Uri.encode(q)}&filter=music"
                    val request = Request.Builder()
                        .url(ytUrl)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                        .build()
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
                    if (BuildConfig.DEBUG) Log.w(TAG, "Piped instance failed for general query $q at $pipedBase", e)
                }
                if (songs.size >= limit / 2) break
            }
        }

        val jamendoTracks = jamendoDeferred.await()
        val archiveTracks = archiveDeferred.await()
        val radioStations = radioDeferred.await()

        (songs + jamendoTracks + archiveTracks + radioStations).distinctBy { it.id }
    }

    companion object {
        private const val TAG = "OnlineStreamRepository"

        // COPYRIGHT FIX (C3): Honest, transparent User-Agent — no browser impersonation
        private const val USER_AGENT = "MoodTunes/1.0 (Android; Music Player App)"
    }
}
