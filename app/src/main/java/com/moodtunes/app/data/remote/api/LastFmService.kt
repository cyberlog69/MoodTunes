package com.moodtunes.app.data.remote.api

import com.moodtunes.app.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LastFmService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val BASE_URL = "https://ws.audioscrobbler.com/2.0/"
        // MoodTunes open client identification for scrobbling
        private const val DEFAULT_API_KEY = "4a9f5581a9cdf20a699f540ba52a4d2b"
        private const val DEFAULT_API_SECRET = "9b6e82ef620e122ea35aafb6ec00e85a"
    }

    /**
     * Submits a completed track scrobble to Last.fm.
     */
    suspend fun scrobbleTrack(
        sessionKey: String,
        song: Song,
        timestampSec: Long = System.currentTimeMillis() / 1000L,
        apiKey: String = DEFAULT_API_KEY,
        apiSecret: String = DEFAULT_API_SECRET
    ): Boolean = withContext(Dispatchers.IO) {
        if (sessionKey.isBlank() || song.title.isBlank() || song.artist.isBlank()) return@withContext false

        try {
            val params = mutableMapOf(
                "method" to "track.scrobble",
                "artist" to song.artist,
                "track" to song.title,
                "timestamp" to timestampSec.toString(),
                "api_key" to apiKey,
                "sk" to sessionKey
            )
            val sig = generateSignature(params, apiSecret)
            params["api_sig"] = sig
            params["format"] = "json"

            val formBody = FormBody.Builder().apply {
                params.forEach { (k, v) -> add(k, v) }
            }.build()

            val request = Request.Builder()
                .url(BASE_URL)
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Timber.d("Successfully scrobbled to Last.fm: ${song.title} - ${song.artist}")
                    true
                } else {
                    Timber.w("Last.fm scrobble failed HTTP ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error scrobbling to Last.fm")
            false
        }
    }

    /**
     * Updates real-time Now Playing status on Last.fm.
     */
    suspend fun updateNowPlaying(
        sessionKey: String,
        song: Song,
        apiKey: String = DEFAULT_API_KEY,
        apiSecret: String = DEFAULT_API_SECRET
    ): Boolean = withContext(Dispatchers.IO) {
        if (sessionKey.isBlank() || song.title.isBlank() || song.artist.isBlank()) return@withContext false

        try {
            val params = mutableMapOf(
                "method" to "track.updateNowPlaying",
                "artist" to song.artist,
                "track" to song.title,
                "api_key" to apiKey,
                "sk" to sessionKey
            )
            val sig = generateSignature(params, apiSecret)
            params["api_sig"] = sig
            params["format"] = "json"

            val formBody = FormBody.Builder().apply {
                params.forEach { (k, v) -> add(k, v) }
            }.build()

            val request = Request.Builder()
                .url(BASE_URL)
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Timber.e(e, "Error updating Last.fm now playing")
            false
        }
    }

    /**
     * Marks a track as loved/favorited on Last.fm.
     */
    suspend fun loveTrack(
        sessionKey: String,
        song: Song,
        apiKey: String = DEFAULT_API_KEY,
        apiSecret: String = DEFAULT_API_SECRET
    ): Boolean = withContext(Dispatchers.IO) {
        if (sessionKey.isBlank()) return@withContext false

        try {
            val params = mutableMapOf(
                "method" to "track.love",
                "artist" to song.artist,
                "track" to song.title,
                "api_key" to apiKey,
                "sk" to sessionKey
            )
            val sig = generateSignature(params, apiSecret)
            params["api_sig"] = sig
            params["format"] = "json"

            val formBody = FormBody.Builder().apply {
                params.forEach { (k, v) -> add(k, v) }
            }.build()

            client.newCall(Request.Builder().url(BASE_URL).post(formBody).build()).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun generateSignature(params: Map<String, String>, secret: String): String {
        // MD5 of alphabetical key=value pairs concatenated + secret
        val sortedString = params.toSortedMap().entries.joinToString("") { "${it.key}${it.value}" } + secret
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(sortedString.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

