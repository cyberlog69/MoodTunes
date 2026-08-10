package com.moodtunes.app.data.remote.api

import android.net.Uri
import com.moodtunes.app.domain.model.AudioFormat
import com.moodtunes.app.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for interfacing with any Subsonic-compatible personal music server
 * (Navidrome, Airsonic, Gonic, LMS, Jellyfin).
 *
 * Implements Subsonic REST API v1.16.1 with secure token-based authentication (MD5 hash + salt).
 */
@Singleton
class SubsonicApiService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Tests server connectivity and authentication.
     */
    suspend fun ping(serverUrl: String, username: String, password: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val cleanUrl = sanitizeUrl(serverUrl)
        if (cleanUrl.isEmpty() || username.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Server URL and username cannot be empty"))
        }

        try {
            val authParams = buildAuthQueryParams(username, password)
            val url = "$cleanUrl/rest/ping.view?$authParams"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Server returned HTTP ${response.code}"))
                }

                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                val json = JSONObject(body)
                val subsonicResponse = json.optJSONObject("subsonic-response")
                    ?: return@withContext Result.failure(Exception("Invalid Subsonic response format"))

                val status = subsonicResponse.optString("status", "failed")
                if (status == "ok") {
                    val serverVersion = subsonicResponse.optString("version", "1.16.1")
                    Timber.d("Subsonic server connected successfully (version $serverVersion)")
                    Result.success(true)
                } else {
                    val error = subsonicResponse.optJSONObject("error")
                    val message = error?.optString("message", "Authentication failed") ?: "Server error"
                    Result.failure(Exception(message))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Subsonic ping failed")
            Result.failure(e)
        }
    }

    /**
     * Searches the self-hosted server for matching songs, artists, or albums.
     */
    suspend fun search(serverUrl: String, username: String, password: String, query: String, limit: Int = 20): List<Song> = withContext(Dispatchers.IO) {
        val cleanUrl = sanitizeUrl(serverUrl)
        if (cleanUrl.isEmpty() || username.isBlank() || query.isBlank()) return@withContext emptyList()

        try {
            val authParams = buildAuthQueryParams(username, password)
            val encodedQuery = Uri.encode(query.trim())
            val url = "$cleanUrl/rest/search3.view?$authParams&query=$encodedQuery&songCount=$limit&artistCount=0&albumCount=0"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                parseSongListResponse(cleanUrl, username, password, body)
            }
        } catch (e: Exception) {
            Timber.w(e, "Subsonic search failed: $query")
            emptyList()
        }
    }

    /**
     * Fetches random or recently added songs from your self-hosted server.
     */
    suspend fun getRandomSongs(serverUrl: String, username: String, password: String, size: Int = 30): List<Song> = withContext(Dispatchers.IO) {
        val cleanUrl = sanitizeUrl(serverUrl)
        if (cleanUrl.isEmpty() || username.isBlank()) return@withContext emptyList()

        try {
            val authParams = buildAuthQueryParams(username, password)
            val url = "$cleanUrl/rest/getRandomSongs.view?$authParams&size=$size"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                parseSongListResponse(cleanUrl, username, password, body)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to get random songs from Subsonic server")
            emptyList()
        }
    }

    /**
     * Builds the direct streaming URL for a specific song ID on the server.
     */
    fun buildStreamUrl(serverUrl: String, username: String, password: String, songId: String): String {
        val cleanUrl = sanitizeUrl(serverUrl)
        val authParams = buildAuthQueryParams(username, password)
        return "$cleanUrl/rest/stream.view?$authParams&id=$songId"
    }

    /**
     * Builds the high-resolution cover art URL for an album or song ID.
     */
    fun buildCoverArtUrl(serverUrl: String, username: String, password: String, coverArtId: String, size: Int = 500): String {
        val cleanUrl = sanitizeUrl(serverUrl)
        val authParams = buildAuthQueryParams(username, password)
        return "$cleanUrl/rest/getCoverArt.view?$authParams&id=$coverArtId&size=$size"
    }

    private fun parseSongListResponse(
        serverUrl: String,
        username: String,
        password: String,
        jsonString: String
    ): List<Song> {
        val songs = mutableListOf<Song>()
        try {
            val root = JSONObject(jsonString)
            val response = root.optJSONObject("subsonic-response") ?: return emptyList()

            // Could be inside searchResult3.song or randomSongs.song
            val songArray: JSONArray = response.optJSONObject("searchResult3")?.optJSONArray("song")
                ?: response.optJSONObject("randomSongs")?.optJSONArray("song")
                ?: response.optJSONObject("album")?.optJSONArray("song")
                ?: JSONArray()

            for (i in 0 until songArray.length()) {
                val item = songArray.optJSONObject(i) ?: continue
                val id = item.optString("id", "")
                if (id.isEmpty()) continue

                val title = item.optString("title", "Unknown Track")
                val artist = item.optString("artist", "Unknown Artist")
                val album = item.optString("album", "Self-Hosted")
                val durationSec = item.optLong("duration", 0L)
                val durationMs = durationSec * 1000L
                val suffix = item.optString("suffix", "").lowercase()
                val isFlac = suffix == "flac" || suffix == "alac" || suffix == "wav"

                val streamUrl = buildStreamUrl(serverUrl, username, password, id)
                val coverArtId = item.optString("coverArt", id)
                val coverUrl = if (coverArtId.isNotEmpty()) {
                    buildCoverArtUrl(serverUrl, username, password, coverArtId)
                } else null

                val songId = ("subsonic_${serverUrl}_$id").hashCode().toLong() and 0x7FFFFFFF

                songs.add(
                    Song(
                        id = songId,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = durationMs,
                        uri = Uri.parse(streamUrl),
                        albumArtUri = coverUrl?.let { Uri.parse(it) },
                        genre = "🏠 Self-Hosted",
                        audioFormat = if (isFlac) AudioFormat.FLAC else AudioFormat.MP3,
                        isStream = true,
                        isPreview = false
                    )
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing Subsonic song response")
        }
        return songs
    }

    private fun sanitizeUrl(url: String): String {
        return url.trim().trimEnd('/')
    }

    private fun buildAuthQueryParams(username: String, password: String): String {
        val salt = UUID.randomUUID().toString().replace("-", "").take(12)
        val token = md5("$password$salt")
        return "u=${Uri.encode(username.trim())}&t=$token&s=$salt&v=1.16.1&c=MoodTunes&f=json"
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val USER_AGENT = "MoodTunes/1.0 (Android; Subsonic Client)"
    }
}
