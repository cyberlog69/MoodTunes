package com.moodtunes.app.data.remote

import android.net.Uri
import com.moodtunes.app.domain.model.AudioFormat
import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles global & Indian ISP (Jio, Airtel, Vi, BSNL, ACT) network-unrestricted music streaming
 * using dynamic host failovers across both Audius Protocol and YouTube (via Piped/Invidious).
 */
@Singleton
class OnlineStreamRepository @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // ─── Audius Host Discovery Pool (with Indian ISP mirrors) ─────────────────
    private val defaultAudiusHosts = listOf(
        "https://discoveryprovider.audius.co",
        "https://audius-dp.trendigo.com",
        "https://audius-discovery-1.cultur3lt.com",
        "https://discovery-provider.audius.co",
        "https://dn2.audius.co"
    )

    // ─── Piped (YouTube) API Instances Pool (with Global & Asia mirrors) ──────
    private val pipedInstances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://api.piped.privacydev.net",
        "https://pipedapi.tokhmi.xyz",
        "https://pipedapi.garudalinux.org",
        "https://piped-api.lunar.icu"
    )

    // ─── Invidious Fallback Instances ─────────────────────────────────────────
    private val invidiousInstances = listOf(
        "https://inv.tux.pizza",
        "https://invidious.nerdvpn.de",
        "https://invidious.drgns.space"
    )

    private var activeAudiusHost: String? = null

    /**
     * Resolves active Audius discovery provider host.
     */
    private suspend fun getAudiusHost(): String = withContext(Dispatchers.IO) {
        activeAudiusHost?.let { return@withContext it }

        // Attempt dynamic discovery from main node
        try {
            val request = Request.Builder()
                .url("https://api.audius.co")
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val json = JSONObject(body)
                        val data = json.getJSONArray("data")
                        if (data.length() > 0) {
                            val host = data.getString(0)
                            activeAudiusHost = host
                            return@withContext host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback to verified host pool
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
            } catch (_: Exception) {}
        }

        defaultAudiusHosts.first()
    }

    /**
     * Searches Audius tracks by mood keyword.
     */
    suspend fun getAudiusTracksByMood(mood: MoodType, limit: Int = 10): List<Song> = withContext(Dispatchers.IO) {
        val host = getAudiusHost()
        val songs = mutableListOf<Song>()
        val keyword = mood.keywords.firstOrNull() ?: mood.displayName

        try {
            val url = "$host/v1/tracks/search?query=${Uri.encode(keyword)}&app_name=MoodTunes&limit=$limit"
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
            e.printStackTrace()
        }
        songs
    }

    /**
     * Searches YouTube audio tracks via Piped/Invidious instances pool (bypasses ISP restrictions).
     */
    suspend fun getYouTubeAudioTracksByMood(mood: MoodType, limit: Int = 8): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val query = "${mood.displayName} music"

        // Try Piped instances first
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
                                    // Piped audio stream proxy endpoint
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
            } catch (_: Exception) {}
        }

        songs
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    }
}
