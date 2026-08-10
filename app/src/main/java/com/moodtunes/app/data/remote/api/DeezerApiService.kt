package com.moodtunes.app.data.remote.api

import android.net.Uri
import com.moodtunes.app.domain.model.AudioFormat
import com.moodtunes.app.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeezerApiService @Inject constructor() {

    private val client = OkHttpClient()

    suspend fun searchTracks(query: String, limit: Int = 10): List<Song> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://api.deezer.com/search/track?q=$encodedQuery&limit=$limit"
        fetchDeezerSongs(url)
    }

    suspend fun getChartTracks(limit: Int = 10): List<Song> = withContext(Dispatchers.IO) {
        val url = "https://api.deezer.com/chart/0/tracks?limit=$limit"
        fetchDeezerSongs(url)
    }

    private fun fetchDeezerSongs(url: String): List<Song> {
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
                    Timber.e("Deezer API error: ${response.code}")
                    return emptyList()
                }

                val responseBody = response.body?.string()
                if (responseBody.isNullOrEmpty()) return emptyList()

                val jsonObject = JSONObject(responseBody)
                val dataArray = jsonObject.optJSONArray("data") ?: return emptyList()

                for (i in 0 until dataArray.length()) {
                    val trackObj = dataArray.optJSONObject(i) ?: continue

                    val trackId = trackObj.optLong("id", 0L)
                    if (trackId == 0L) continue
                    val title = trackObj.optString("title", "Unknown Title")
                    val durationSeconds = trackObj.optInt("duration", 0)
                    val previewUrl = trackObj.optString("preview")
                    
                    if (previewUrl.isEmpty()) continue

                    val artistObj = trackObj.optJSONObject("artist")
                    val artist = artistObj?.optString("name", "Unknown Artist") ?: "Unknown Artist"

                    val albumObj = trackObj.optJSONObject("album")
                    val albumTitle = albumObj?.optString("title")
                    val coverMedium = albumObj?.optString("cover_medium")

                    val songId = (trackId.hashCode().toLong()) and 0x7FFFFFFF

                    songs.add(
                        Song(
                            id = songId,
                            title = title,
                            artist = artist,
                            album = albumTitle ?: "Deezer Preview",
                            duration = durationSeconds * 1000L,
                            uri = Uri.parse(previewUrl),
                            albumArtUri = if (!coverMedium.isNullOrEmpty()) Uri.parse(coverMedium) else null,
                            isStream = true,
                            isPreview = true,
                            audioFormat = AudioFormat.STREAM
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception in Deezer API fetch")
        }
        return songs
    }
}
