package com.moodtunes.app.data.remote.api

import android.net.Uri
import com.moodtunes.app.domain.model.AudioFormat
import com.moodtunes.app.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class iTunesApiService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    @Volatile private var requestCount = 0
    @Volatile private var cycleStartTime = System.currentTimeMillis()

    private suspend fun checkRateLimit() {
        val now = System.currentTimeMillis()
        if (now - cycleStartTime > 60_000) {
            cycleStartTime = now
            requestCount = 0
        }
        if (requestCount >= 20) {
            val waitTime = 60_000 - (now - cycleStartTime)
            if (waitTime > 0) {
                Timber.w("Rate limit reached. Waiting for ${waitTime}ms")
                delay(waitTime)
            }
            cycleStartTime = System.currentTimeMillis()
            requestCount = 0
        }
        requestCount++
    }

    suspend fun searchTracks(query: String, limit: Int = 10): List<Song> = withContext(Dispatchers.IO) {
        checkRateLimit()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://itunes.apple.com/search?term=$encodedQuery&media=music&entity=song&limit=$limit"
        fetchSongs(url)
    }

    suspend fun searchTracksByGenre(genre: String, limit: Int = 10): List<Song> = withContext(Dispatchers.IO) {
        checkRateLimit()
        val encodedGenre = URLEncoder.encode(genre, "UTF-8")
        val url = "https://itunes.apple.com/search?term=$encodedGenre&media=music&entity=song&limit=$limit"
        fetchSongs(url)
    }

    private fun fetchSongs(url: String): List<Song> {
        if (!url.startsWith("https://")) {
            Timber.e("URL must start with https://")
            return emptyList()
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MoodTunes/1.0 (Android; Music Player App)")
            .build()
            
        val songs = mutableListOf<Song>()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.e("iTunes API error: ${response.code}")
                    return emptyList()
                }

                val responseBody = response.body?.string()
                if (responseBody.isNullOrEmpty()) return emptyList()

                val jsonObject = JSONObject(responseBody)
                val resultsArray = jsonObject.optJSONArray("results") ?: return emptyList()

                for (i in 0 until resultsArray.length()) {
                    val trackObj = resultsArray.optJSONObject(i) ?: continue

                    val trackId = trackObj.optInt("trackId", 0)
                    if (trackId == 0) continue
                    val title = trackObj.optString("trackName", "Unknown Title")
                    val artist = trackObj.optString("artistName", "Unknown Artist")
                    val collectionName = trackObj.optString("collectionName", "iTunes Preview")
                    val previewUrl = trackObj.optString("previewUrl")
                    val artworkUrl100 = trackObj.optString("artworkUrl100")
                    val trackTimeMillis = trackObj.optLong("trackTimeMillis", 0L)
                    val primaryGenreName = trackObj.optString("primaryGenreName")

                    if (previewUrl.isEmpty()) continue

                    val artworkUrl600 = artworkUrl100.replace("100x100", "600x600")

                    val songId = (trackId.hashCode().toLong()) and 0x7FFFFFFF

                    songs.add(
                        Song(
                            id = songId,
                            title = title,
                            artist = artist,
                            album = collectionName,
                            duration = trackTimeMillis,
                            uri = Uri.parse(previewUrl),
                            albumArtUri = if (artworkUrl600.isNotEmpty()) Uri.parse(artworkUrl600) else null,
                            genre = if (primaryGenreName.isNotEmpty()) primaryGenreName else null,
                            isStream = true,
                            isPreview = true,
                            audioFormat = AudioFormat.STREAM
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception in iTunes API fetch")
        }
        return songs
    }
}
