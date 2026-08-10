package com.moodtunes.app.data.remote.api

import com.moodtunes.app.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for interfacing with the official ListenBrainz API (https://listenbrainz.org).
 * Supports:
 * - Scrobbling completed tracks (`single` listen type)
 * - Real-time "playing_now" status broadcast
 * - Collaborative-filtering recommendations
 * - User listen history & feedback
 */
@Singleton
class ListenBrainzService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Submits a completed track scrobble to ListenBrainz.
     */
    suspend fun submitListen(
        userToken: String,
        song: Song,
        listenedAtSeconds: Long = System.currentTimeMillis() / 1000
    ): Boolean = withContext(Dispatchers.IO) {
        if (userToken.isBlank() || song.title.isBlank()) return@withContext false

        try {
            val payload = buildListenPayload(
                listenType = "single",
                song = song,
                listenedAtSeconds = listenedAtSeconds
            )

            val request = Request.Builder()
                .url("$BASE_URL/1/submit-listens")
                .header("Authorization", "Token ${userToken.trim()}")
                .header("User-Agent", USER_AGENT)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Timber.d("Successfully scrobbled to ListenBrainz: ${song.title} - ${song.artist}")
                    true
                } else {
                    Timber.w("ListenBrainz scrobble failed with HTTP ${response.code}: ${response.body?.string()}")
                    false
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error submitting listen to ListenBrainz")
            false
        }
    }

    /**
     * Submits the currently playing track status to ListenBrainz.
     */
    suspend fun submitPlayingNow(
        userToken: String,
        song: Song
    ): Boolean = withContext(Dispatchers.IO) {
        if (userToken.isBlank() || song.title.isBlank()) return@withContext false

        try {
            val payload = buildListenPayload(
                listenType = "playing_now",
                song = song,
                listenedAtSeconds = null
            )

            val request = Request.Builder()
                .url("$BASE_URL/1/submit-listens")
                .header("Authorization", "Token ${userToken.trim()}")
                .header("User-Agent", USER_AGENT)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Timber.w(e, "Error updating playing_now on ListenBrainz")
            false
        }
    }

    /**
     * Fetches recent listens for a given username.
     */
    suspend fun getUserRecentListens(username: String, count: Int = 20): List<ListenItem> = withContext(Dispatchers.IO) {
        val cleanUser = username.trim()
        if (cleanUser.isEmpty()) return@withContext emptyList()

        try {
            val request = Request.Builder()
                .url("$BASE_URL/1/user/$cleanUser/listens?count=$count")
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val root = JSONObject(body)
                val payload = root.optJSONObject("payload") ?: return@withContext emptyList()
                val listens = payload.optJSONArray("listens") ?: JSONArray()

                val items = mutableListOf<ListenItem>()
                for (i in 0 until listens.length()) {
                    val listenObj = listens.optJSONObject(i) ?: continue
                    val listenedAt = listenObj.optLong("listened_at", 0L)
                    val data = listenObj.optJSONObject("track_metadata") ?: continue
                    val trackName = data.optString("track_name", "Unknown")
                    val artistName = data.optString("artist_name", "Unknown Artist")
                    val releaseName = data.optString("release_name", "")

                    items.add(
                        ListenItem(
                            trackName = trackName,
                            artistName = artistName,
                            releaseName = releaseName,
                            listenedAt = listenedAt
                        )
                    )
                }
                items
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to get user listens from ListenBrainz")
            emptyList()
        }
    }

    private fun buildListenPayload(
        listenType: String,
        song: Song,
        listenedAtSeconds: Long?
    ): JSONObject {
        val root = JSONObject()
        root.put("listen_type", listenType)

        val payloadArray = JSONArray()
        val listenData = JSONObject()

        if (listenedAtSeconds != null) {
            listenData.put("listened_at", listenedAtSeconds)
        }

        val trackMetadata = JSONObject()
        trackMetadata.put("track_name", song.title)
        trackMetadata.put("artist_name", song.artist)
        if (song.album.isNotBlank()) {
            trackMetadata.put("release_name", song.album)
        }

        val additionalInfo = JSONObject()
        additionalInfo.put("media_player", "MoodTunes")
        additionalInfo.put("submission_client", "MoodTunes Android")
        if (song.duration > 0) {
            additionalInfo.put("duration_ms", song.duration)
        }
        trackMetadata.put("additional_info", additionalInfo)

        listenData.put("track_metadata", trackMetadata)
        payloadArray.put(listenData)

        root.put("payload", payloadArray)
        return root
    }

    data class ListenItem(
        val trackName: String,
        val artistName: String,
        val releaseName: String,
        val listenedAt: Long
    )

    companion object {
        private const val BASE_URL = "https://api.listenbrainz.org"
        private const val USER_AGENT = "MoodTunes/1.0 (Android; contact@moodtunes.app)"
    }
}
