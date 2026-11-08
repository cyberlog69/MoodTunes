package com.moodtunes.app.data.remote.api

import com.moodtunes.app.domain.model.YouTubePage
import com.moodtunes.app.domain.model.YouTubeRadioPage
import com.moodtunes.app.domain.model.YouTubeSearchFilter
import com.moodtunes.app.domain.model.YouTubeSearchPage
import com.moodtunes.app.domain.model.YouTubeSearchSection
import com.moodtunes.app.domain.model.YouTubeShelf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube Music's browse graph, exposed directly.
 *
 * The old [YouTubeMusicApiService] only knew `/search` and a single VISIONOS
 * player call. This client speaks the same InnerTube protocol as
 * music.youtube.com: `/browse` with continuation tokens, typed search filters,
 * search suggestions, `/next` radio queues and the signed-in library.
 */
@Singleton
class YouTubeInnerTubeClient @Inject constructor(
    private val http: OkHttpClient,
    private val sessionStore: YouTubeSessionStore
) {
    object BrowseId {
        const val HOME = "FEmusic_home"
        const val MOODS_AND_GENRES = "FEmusic_moods_and_genres"
        const val CHARTS = "FEmusic_charts"
        const val EXPLORE = "FEmusic_explore"
        const val NEW_RELEASES = "FEmusic_new_releases"
        const val HISTORY = "FEmusic_history"
        const val LIKED_PLAYLISTS = "FEmusic_liked_playlists"
        const val LIKED_ALBUMS = "FEmusic_liked_albums"
        const val LIBRARY_ARTISTS = "FEmusic_library_corpus_artists"
        const val LIBRARY_SONGS = "FEmusic_library_privately_owned_tracks"
        const val LIBRARY_ALBUMS = "FEmusic_library_privately_owned_releases"
        const val LIBRARY_LANDING = "FEmusic_library_landing"
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var cachedVisitorData: String? = null

    @Volatile
    private var visitorDataExpiry: Long = 0L

    // ── Discovery ────────────────────────────────────────────────────────────

    suspend fun home(): YouTubePage = browse(BrowseId.HOME)

    suspend fun moodsAndGenres(): YouTubePage = browse(BrowseId.MOODS_AND_GENRES)

    suspend fun charts(): YouTubePage = browse(BrowseId.CHARTS)

    suspend fun explore(): YouTubePage = browse(BrowseId.EXPLORE)

    suspend fun newReleases(): YouTubePage = browse(BrowseId.NEW_RELEASES)

    // ── Signed-in library ────────────────────────────────────────────────────

    suspend fun likedPlaylists(): YouTubePage = browse(BrowseId.LIKED_PLAYLISTS)

    suspend fun likedAlbums(): YouTubePage = browse(BrowseId.LIKED_ALBUMS)

    suspend fun libraryArtists(): YouTubePage = browse(BrowseId.LIBRARY_ARTISTS)

    suspend fun librarySongs(): YouTubePage = browse(BrowseId.LIBRARY_SONGS)

    suspend fun libraryLanding(): YouTubePage = browse(BrowseId.LIBRARY_LANDING)

    suspend fun libraryAlbums(): YouTubePage = browse(BrowseId.LIBRARY_ALBUMS)

    // ── Pages & continuations ────────────────────────────────────────────────

    suspend fun browse(browseId: String, params: String? = null): YouTubePage =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("context", clientContext())
                put("browseId", browseId)
                if (!params.isNullOrBlank()) put("params", decodeParam(params))
            }
            val root = post("browse", body)
                ?: return@withContext YouTubePage(browseId = browseId, title = "")
            YouTubeParser.page(browseId, root)
        }

    suspend fun browseContinuation(continuation: String): YouTubePage =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("context", clientContext())
                put("continuation", continuation)
            }
            val root = post("browse", body)
                ?: return@withContext YouTubePage(browseId = "", title = "")
            val shelves = runCatching { YouTubeParser.continuationShelves(root) }.getOrDefault(emptyList())
            val songs = runCatching { YouTubeParser.continuationSongs(root) }.getOrDefault(emptyList())
            YouTubePage(
                browseId = "",
                title = "",
                shelves = shelves,
                songs = songs,
                continuation = runCatching { YouTubeParser.continuation(root) }.getOrNull()
            )
        }

    // ── Search ───────────────────────────────────────────────────────────────

    suspend fun search(
        query: String,
        filter: YouTubeSearchFilter = YouTubeSearchFilter.ALL,
        continuation: String? = null
    ): YouTubeSearchPage = withContext(Dispatchers.IO) {
        if (query.isBlank() && continuation == null) {
            return@withContext YouTubeSearchPage(query = query)
        }

        val body = JSONObject().apply {
            put("context", clientContext())
            if (continuation != null) {
                put("continuation", continuation)
            } else {
                put("query", query.trim())
                filter.params?.let { put("params", decodeParam(it)) }
            }
        }
        val root = post("search", body)
            ?: return@withContext YouTubeSearchPage(query = query)

        runCatching { YouTubeParser.search(query, root, filter) }
            .getOrElse {
                Timber.w(it, "YouTube search parse failed for %s", query)
                YouTubeSearchPage(query = query)
            }
    }

    /** A continuation belongs to the same filter bucket as its first page. */
    suspend fun searchContinuation(
        query: String,
        filter: YouTubeSearchFilter,
        continuation: String
    ): YouTubeSearchPage = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("context", clientContext())
            put("continuation", continuation)
        }
        val root = post("search", body)
            ?: return@withContext YouTubeSearchPage(query = query)
        val items = runCatching { YouTubeParser.continuationShelves(root).flatMap { it.items } }
            .getOrDefault(emptyList())
        YouTubeSearchPage(
            query = query,
            sections = listOf(YouTubeSearchSection(filter, items)),
            continuation = runCatching { YouTubeParser.continuation(root) }.getOrNull()
        )
    }

    /**
     * Autocomplete suggestions from `/music/get_search_suggestions`. These are
     * cheap and give the search screen real typeahead instead of static chips.
     */
    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val body = JSONObject().apply {
            put("context", clientContext())
            put("input", query.trim())
        }
        val root = post("music/get_search_suggestions", body) ?: return@withContext emptyList()

        runCatching {
            root.findAll("searchSuggestionRenderer").mapNotNull { renderer ->
                renderer.obj("suggestion")?.text()
                    ?: renderer.obj("suggestion", "runs").text()
            }.distinct().take(8)
        }.getOrDefault(emptyList())
    }

    // ── Radio ────────────────────────────────────────────────────────────────

    suspend fun radio(videoId: String, playlistId: String? = null): YouTubeRadioPage =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("context", clientContext())
                put("videoId", videoId)
                put("isAudioOnly", true)
                if (!playlistId.isNullOrBlank()) {
                    put("playlistId", playlistId)
                    put("params", WATCH_NEXT_RADIO_PARAMS)
                }
            }
            val root = post("next", body)
                ?: return@withContext YouTubeRadioPage(emptyList())
            runCatching { YouTubeParser.radio(root) }.getOrDefault(YouTubeRadioPage(emptyList()))
        }

    suspend fun radioContinuation(token: String): YouTubeRadioPage =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("context", clientContext())
                put("continuation", token)
            }
            val root = post("next", body)
                ?: return@withContext YouTubeRadioPage(emptyList())
            runCatching { YouTubeParser.radio(root) }.getOrDefault(YouTubeRadioPage(emptyList()))
        }

    // ── Transport ────────────────────────────────────────────────────────────

    /**
     * Test seam: lets transport tests point the client at a MockWebServer
     * without changing production behaviour.
     */
    internal var baseUrlOverride: String? = null
    internal var webOriginOverride: String? = null

    private val baseUrl: String get() = baseUrlOverride ?: BASE_URL
    private val webOrigin: String get() = webOriginOverride ?: "https://music.youtube.com"

    private data class Attempt(val payload: JSONObject?, val code: Int?)

    private fun post(endpoint: String, body: JSONObject): JSONObject? {
        val first = postOnce(endpoint, body)
        if (first.payload != null) return first.payload
        val code = first.code
        if (code == null || code !in RETRYABLE_REJECTIONS) return null

        // A stale visitor id shows up as 400/401/403. Refresh it once and strip
        // the old value from the body so the retry is anonymous and clean.
        Timber.i("InnerTube %s returned HTTP %d; refreshing visitorData", endpoint, code)
        refreshVisitorData()
        body.optJSONObject("context")?.optJSONObject("client")?.remove("visitorData")
        return postOnce(endpoint, body).payload
    }

    private fun postOnce(endpoint: String, body: JSONObject): Attempt {
        val request = try {
            buildRequest(endpoint, body)
        } catch (e: Exception) {
            Timber.w(e, "Could not build InnerTube request for %s", endpoint)
            return Attempt(null, null)
        }

        return runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("InnerTube %s returned HTTP %d", endpoint, response.code)
                    return@use Attempt(null, response.code)
                }
                val payload = response.body?.string()?.takeIf { it.isNotBlank() }
                    ?: return@use Attempt(null, response.code)
                Attempt(JSONObject(payload), response.code)
            }
        }.getOrElse { error ->
            Timber.w(error, "InnerTube %s failed", endpoint)
            Attempt(null, null)
        }
    }

    private fun buildRequest(endpoint: String, body: JSONObject): Request {
        val visitor = visitorData()
        val builder = Request.Builder()
            .url("$baseUrl/$endpoint?prettyPrint=false")
            .post(body.toString().toRequestBody(jsonMediaType))
            .addHeader("User-Agent", WEB_USER_AGENT)
            .addHeader("X-YouTube-Client-Name", "67")
            .addHeader("X-YouTube-Client-Version", WEB_CLIENT_VERSION)
            .addHeader("Origin", "https://music.youtube.com")
            .addHeader("Referer", "https://music.youtube.com/")
            .addHeader("Accept-Language", "en-US,en;q=0.9")

        if (visitor.isNotBlank()) {
            builder.addHeader("X-Goog-Visitor-Id", visitor)
        }
        sessionStore.cookieHeader()?.let { builder.addHeader("Cookie", it) }
        sessionStore.sapisidHash()?.let { builder.addHeader("Authorization", it) }
        return builder.build()
    }

    private fun clientContext(): JSONObject {
        val visitor = visitorData()
        val client = JSONObject().apply {
            put("clientName", "WEB_REMIX")
            put("clientVersion", WEB_CLIENT_VERSION)
            put("hl", "en")
            put("gl", "US")
            put("originalUrl", "https://music.youtube.com/")
            put("platform", "DESKTOP")
            put("userAgent", WEB_USER_AGENT)
            if (visitor.isNotBlank()) put("visitorData", visitor)
        }
        return JSONObject().apply {
            put("client", client)
            put("request", JSONObject().put("useSsl", true))
            put("user", JSONObject().apply {
                put("lockedSafetyMode", false)
            })
        }
    }

    @Synchronized
    private fun refreshVisitorData(): String {
        cachedVisitorData = null
        visitorDataExpiry = 0L
        sessionStore.visitorData = null
        return visitorData()
    }

    @Synchronized
    private fun visitorData(): String {
        val now = System.currentTimeMillis()
        val cached = cachedVisitorData
        if (!cached.isNullOrBlank() && now < visitorDataExpiry) return cached

        sessionStore.visitorData?.let {
            cachedVisitorData = it
            visitorDataExpiry = now + TimeUnit.HOURS.toMillis(12)
            return it
        }

        val fetched = runCatching {
            val request = Request.Builder()
                .url("$webOrigin/")
                .addHeader("User-Agent", WEB_USER_AGENT)
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val html = response.body?.string() ?: return@use null
                Regex("\"visitorData\"\\s*:\\s*\"([^\"]+)\"")
                    .find(html)?.groupValues?.get(1)
                    ?.replace("\\u003d", "=")
                    ?.replace("\\u0026", "&")
            }
        }.getOrNull()

        if (!fetched.isNullOrBlank()) {
            sessionStore.visitorData = fetched
            cachedVisitorData = fetched
            visitorDataExpiry = now + TimeUnit.HOURS.toMillis(12)
            return fetched
        }
        return ""
    }

    private fun decodeParam(value: String): String = runCatching {
        URLDecoder.decode(value, "UTF-8")
    }.getOrDefault(value)

    private companion object {
        const val BASE_URL = "https://music.youtube.com/youtubei/v1"
        val RETRYABLE_REJECTIONS = setOf(400, 401, 403)
        const val WEB_CLIENT_VERSION = "1.20260928.13.00"
        const val WEB_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        // Generic radio params; the /next endpoint also accepts a bare videoId.
        const val WATCH_NEXT_RADIO_PARAMS = "wAEB"
    }
}
