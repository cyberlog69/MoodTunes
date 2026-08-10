package com.moodtunes.app.data.remote.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

data class LrclibResult(
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val syncedLyrics: String?,
    val plainLyrics: String?
)

@Singleton
class LrclibService @Inject constructor() {

    private val client = OkHttpClient()

    suspend fun getLyrics(trackName: String, artistName: String, durationSeconds: Int? = null): LrclibResult? = withContext(Dispatchers.IO) {
        val t = URLEncoder.encode(trackName, "UTF-8")
        val a = URLEncoder.encode(artistName, "UTF-8")
        var url = "https://lrclib.net/api/get?track_name=$t&artist_name=$a"
        if (durationSeconds != null) {
            url += "&duration=$durationSeconds"
        }
        
        if (!url.startsWith("https://")) {
            Timber.e("URL must start with https://")
            return@withContext null
        }
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MoodTunes/1.0 (Android; Music Player App)")
            .build()
            
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code != 404) {
                        Timber.e("LRCLIB API error: ${response.code}")
                    }
                    return@withContext null
                }
                
                val responseBody = response.body?.string()
                if (responseBody.isNullOrEmpty()) return@withContext null
                
                val jsonObject = JSONObject(responseBody)
                parseLrclibResult(jsonObject)
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception in LRCLIB API fetch")
            null
        }
    }

    suspend fun searchLyrics(query: String): List<LrclibResult> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://lrclib.net/api/search?q=$encodedQuery"
        
        if (!url.startsWith("https://")) {
            Timber.e("URL must start with https://")
            return@withContext emptyList()
        }
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MoodTunes/1.0 (Android; Music Player App)")
            .build()
            
        val results = mutableListOf<LrclibResult>()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.e("LRCLIB API error: ${response.code}")
                    return@withContext emptyList()
                }
                
                val responseBody = response.body?.string()
                if (responseBody.isNullOrEmpty()) return@withContext emptyList()
                
                val jsonArray = JSONArray(responseBody)
                for (i in 0 until jsonArray.length()) {
                    val jsonObj = jsonArray.optJSONObject(i) ?: continue
                    val result = parseLrclibResult(jsonObj)
                    if (result != null) {
                        results.add(result)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception in LRCLIB API search")
        }
        return@withContext results
    }
    
    private fun parseLrclibResult(jsonObject: JSONObject): LrclibResult? {
        val trackName = jsonObject.optString("trackName")
        val artistName = jsonObject.optString("artistName")
        if (trackName.isEmpty() || artistName.isEmpty()) return null
        
        val albumName = jsonObject.optString("albumName").takeIf { it.isNotEmpty() }
        val syncedLyrics = jsonObject.optString("syncedLyrics").takeIf { it.isNotEmpty() }
        val plainLyrics = jsonObject.optString("plainLyrics").takeIf { it.isNotEmpty() }
        
        return LrclibResult(
            trackName = trackName,
            artistName = artistName,
            albumName = albumName,
            syncedLyrics = syncedLyrics,
            plainLyrics = plainLyrics
        )
    }
}
