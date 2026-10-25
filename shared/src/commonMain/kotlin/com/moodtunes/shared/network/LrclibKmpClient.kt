package com.moodtunes.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable

@Serializable
data class SharedLrclibResponse(
    val id: Long? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean? = null,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null
)

class LrclibKmpClient(private val client: HttpClient = createPlatformHttpClient()) {

    suspend fun getLyrics(track: String, artist: String, durationSec: Int? = null): SharedLrclibResponse? {
        return try {
            client.get("https://lrclib.net/api/get") {
                header("User-Agent", "MoodTunes-KMP/1.6.0")
                parameter("track_name", track)
                parameter("artist_name", artist)
                if (durationSec != null && durationSec > 0) {
                    parameter("duration", durationSec)
                }
            }.body()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun searchLyrics(query: String): List<SharedLrclibResponse> {
        return try {
            client.get("https://lrclib.net/api/search") {
                header("User-Agent", "MoodTunes-KMP/1.6.0")
                parameter("q", query)
            }.body()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
