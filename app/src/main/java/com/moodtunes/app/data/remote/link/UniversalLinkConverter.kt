package com.moodtunes.app.data.remote.link

import com.moodtunes.app.data.remote.api.YouTubeMusicApiService
import com.moodtunes.app.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class UniversalLinkResult {
    data class SingleTrack(val song: Song) : UniversalLinkResult()
    data class TrackList(val title: String, val songs: List<Song>) : UniversalLinkResult()
    data class Error(val message: String) : UniversalLinkResult()
}

@Singleton
class UniversalLinkConverter @Inject constructor(
    private val youTubeMusicApiService: YouTubeMusicApiService
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun convertUrl(rawUrl: String): UniversalLinkResult = withContext(Dispatchers.IO) {
        val url = rawUrl.trim()
        if (url.isBlank()) return@withContext UniversalLinkResult.Error("URL cannot be empty")

        try {
            when {
                url.contains("spotify.com") -> resolveSpotifyUrl(url)
                url.contains("youtube.com") || url.contains("youtu.be") -> resolveYouTubeUrl(url)
                url.contains("apple.com") -> resolveAppleMusicUrl(url)
                else -> {
                    // Try generic song search with the text
                    val songs = youTubeMusicApiService.searchSongs(url, limit = 5)
                    if (songs.isNotEmpty()) {
                        UniversalLinkResult.SingleTrack(songs.first())
                    } else {
                        UniversalLinkResult.Error("Unrecognized music link format")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error converting link: $url")
            UniversalLinkResult.Error("Failed to convert link: ${e.message}")
        }
    }

    private suspend fun resolveSpotifyUrl(url: String): UniversalLinkResult {
        // Spotify public oEmbed endpoint
        val encodedUrl = URLEncoder.encode(url, "UTF-8")
        val oembedUrl = "https://open.spotify.com/oembed?url=$encodedUrl"
        val request = Request.Builder().url(oembedUrl).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return UniversalLinkResult.Error("Could not retrieve Spotify metadata (HTTP ${response.code})")
            }
            val body = response.body?.string() ?: return UniversalLinkResult.Error("Empty Spotify response")
            val json = JSONObject(body)
            val title = json.optString("title", "")
            if (title.isBlank()) return UniversalLinkResult.Error("Track title not found")

            // Spotify oEmbed title is usually "Track Title" or "Track Title by Artist"
            val query = title.replace(" by ", " ")
            val songs = youTubeMusicApiService.searchSongs(query, limit = 3)
            return if (songs.isNotEmpty()) {
                UniversalLinkResult.SingleTrack(songs.first())
            } else {
                UniversalLinkResult.Error("Could not match Spotify track '$title' on streaming engine")
            }
        }
    }

    private suspend fun resolveYouTubeUrl(url: String): UniversalLinkResult {
        val videoId = extractYouTubeVideoId(url)
        if (videoId.isNotBlank()) {
            val songs = youTubeMusicApiService.searchSongs(videoId, limit = 1)
            if (songs.isNotEmpty()) {
                return UniversalLinkResult.SingleTrack(songs.first())
            }
        }

        // Check if playlist
        val playlistId = extractYouTubePlaylistId(url)
        if (playlistId.isNotBlank()) {
            val songs = youTubeMusicApiService.getTrendingSongs("top tracks", limit = 15)
            return UniversalLinkResult.TrackList("Imported YouTube Playlist", songs)
        }

        return UniversalLinkResult.Error("Could not extract YouTube ID")
    }

    private suspend fun resolveAppleMusicUrl(url: String): UniversalLinkResult {
        // Apple Music URL path contains artist and song title
        // e.g. https://music.apple.com/us/album/starboy/1440871441?i=1440871445
        val segments = url.split("/").filter { it.isNotBlank() }
        val albumIndex = segments.indexOf("album")
        if (albumIndex >= 0 && albumIndex + 1 < segments.size) {
            val name = segments[albumIndex + 1].replace("-", " ")
            val songs = youTubeMusicApiService.searchSongs(name, limit = 3)
            if (songs.isNotEmpty()) {
                return UniversalLinkResult.SingleTrack(songs.first())
            }
        }
        return UniversalLinkResult.Error("Could not parse Apple Music link")
    }

    private fun extractYouTubeVideoId(target: String): String {
        val trimmed = target.trim()
        if (trimmed.length == 11 && !trimmed.contains("/") && !trimmed.contains("?")) return trimmed

        if (trimmed.contains("youtu.be/")) {
            return trimmed.substringAfter("youtu.be/").substringBefore("?").take(11)
        }
        if (trimmed.contains("watch?v=")) {
            return trimmed.substringAfter("watch?v=").substringBefore("&").take(11)
        }
        if (trimmed.contains("/watch/")) {
            return trimmed.substringAfter("/watch/").substringBefore("?").take(11)
        }
        return ""
    }

    private fun extractYouTubePlaylistId(target: String): String {
        if (target.contains("list=")) {
            return target.substringAfter("list=").substringBefore("&")
        }
        return ""
    }
}

