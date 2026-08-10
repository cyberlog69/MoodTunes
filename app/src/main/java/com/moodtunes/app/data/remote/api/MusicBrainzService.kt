package com.moodtunes.app.data.remote.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

data class MusicBrainzResult(
    val mbid: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val tags: List<String>
)

@Singleton
class MusicBrainzService @Inject constructor() {

    private val client = OkHttpClient()
    
    @Volatile private var lastRequestTime = 0L

    private suspend fun rateLimitWait() {
        val elapsed = System.currentTimeMillis() - lastRequestTime
        if (elapsed < 1100) delay(1100 - elapsed)
        lastRequestTime = System.currentTimeMillis()
    }

    suspend fun searchRecordings(query: String, limit: Int = 5): List<MusicBrainzResult> = withContext(Dispatchers.IO) {
        rateLimitWait()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://musicbrainz.org/ws/2/recording/?query=$encodedQuery&fmt=json&limit=$limit"
        fetchMusicBrainzResults(url)
    }

    suspend fun enrichSongMetadata(title: String, artist: String): MusicBrainzResult? = withContext(Dispatchers.IO) {
        val query = "recording:\"$title\" AND artist:\"$artist\""
        val results = searchRecordings(query, limit = 1)
        results.firstOrNull()
    }

    private fun fetchMusicBrainzResults(url: String): List<MusicBrainzResult> {
        if (!url.startsWith("https://")) {
            Timber.e("URL must start with https://")
            return emptyList()
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MoodTunes/1.0 (Android; Music Player App)")
            .build()
            
        val resultsList = mutableListOf<MusicBrainzResult>()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.e("MusicBrainz API error: ${response.code}")
                    return emptyList()
                }

                val responseBody = response.body?.string()
                if (responseBody.isNullOrEmpty()) return emptyList()

                val jsonObject = JSONObject(responseBody)
                val recordingsArray = jsonObject.optJSONArray("recordings") ?: return emptyList()

                for (i in 0 until recordingsArray.length()) {
                    val recordingObj = recordingsArray.optJSONObject(i) ?: continue

                    val id = recordingObj.optString("id")
                    if (id.isEmpty()) continue
                    
                    val title = recordingObj.optString("title", "Unknown Title")
                    val length = recordingObj.optLong("length", 0L)
                    
                    val artistCreditArray = recordingObj.optJSONArray("artist-credit")
                    val artistName = if (artistCreditArray != null && artistCreditArray.length() > 0) {
                        artistCreditArray.optJSONObject(0)?.optString("name", "Unknown Artist") ?: "Unknown Artist"
                    } else {
                        "Unknown Artist"
                    }

                    val releasesArray = recordingObj.optJSONArray("releases")
                    val albumName = if (releasesArray != null && releasesArray.length() > 0) {
                        releasesArray.optJSONObject(0)?.optString("title")
                    } else {
                        null
                    }

                    val tagsArray = recordingObj.optJSONArray("tags")
                    val tags = mutableListOf<String>()
                    if (tagsArray != null) {
                        for (j in 0 until tagsArray.length()) {
                            val tagObj = tagsArray.optJSONObject(j)
                            val tagName = tagObj?.optString("name")
                            if (!tagName.isNullOrEmpty()) {
                                tags.add(tagName)
                            }
                        }
                    }

                    resultsList.add(
                        MusicBrainzResult(
                            mbid = id,
                            title = title,
                            artist = artistName,
                            album = albumName,
                            durationMs = length,
                            tags = tags
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception in MusicBrainz API fetch")
        }
        return resultsList
    }
}
