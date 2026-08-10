package com.moodtunes.app.data.remote.api

import android.net.Uri
import android.text.Html
import android.util.Base64
import com.moodtunes.app.domain.model.AudioFormat
import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Direct client-side JioSaavn API Service.
 * Provides access to full-length Indian regional (Tamil, Telugu, Hindi, Punjabi, Malayalam, Kannada, etc.),
 * traditional, classical (Carnatic/Hindustani), and folk music with on-device DES decryption.
 */
@Singleton
class JioSaavnApiService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val desKeySpec = SecretKeySpec(DES_KEY.toByteArray(Charsets.UTF_8), "DES")

    /**
     * Searches JioSaavn for full-length tracks matching a query.
     */
    suspend fun searchSongs(query: String, limit: Int = 20, page: Int = 1): List<Song> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return@withContext emptyList()

        val encodedQuery = Uri.encode(cleanQuery)
        val url = "$BASE_URL?__call=search.getResults&_format=json&_marker=0&api_version=4&ctx=web6dot0&n=$limit&p=$page&q=$encodedQuery"

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("JioSaavn search failed with HTTP ${response.code}")
                    return@withContext emptyList()
                }

                val body = response.body?.string() ?: return@withContext emptyList()
                parseSearchResponse(body, cleanQuery)
            }
        } catch (e: Exception) {
            Timber.w(e, "JioSaavn search query failed: $query")
            emptyList()
        }
    }

    /**
     * Fetches regional songs for a specific language or genre (e.g. "Carnatic", "Tamil Folk", "Punjabi Pop").
     */
    suspend fun getRegionalTracks(languageOrGenre: String, category: String = "Top Hits", limit: Int = 16): List<Song> {
        val query = if (languageOrGenre.isNotBlank() && languageOrGenre != "All") {
            "$languageOrGenre $category"
        } else {
            category
        }
        return searchSongs(query, limit = limit)
    }

    /**
     * Fetches JioSaavn songs tailored to a mood and optional language prefix.
     */
    suspend fun getSongsByMood(
        mood: MoodType,
        languagePrefix: String = "",
        limit: Int = 12
    ): List<Song> {
        val keyword = mood.keywords.firstOrNull() ?: mood.displayName
        val query = if (languagePrefix.isNotBlank()) {
            "$languagePrefix $keyword songs"
        } else {
            "${mood.displayName} songs"
        }
        val results = searchSongs(query, limit = limit)
        return results.map { it.copy(moodTags = listOf(mood)) }
    }

    private fun parseSearchResponse(jsonString: String, genreTag: String): List<Song> {
        val songs = mutableListOf<Song>()
        try {
            val root = JSONObject(jsonString)
            val results = root.optJSONArray("results") ?: JSONArray()

            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val song = parseSongObject(item, genreTag)
                if (song != null) {
                    songs.add(song)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse JioSaavn response")
        }
        return songs
    }

    private fun parseSongObject(item: JSONObject, genreTag: String): Song? {
        val idStr = item.optString("id", "")
        if (idStr.isEmpty()) return null

        val rawTitle = item.optString("song", item.optString("title", "Unknown Track"))
        val title = unescapeHtml(rawTitle)

        val rawArtist = item.optString("primary_artists", item.optString("singers", item.optString("artist", "JioSaavn Artist")))
        val artist = unescapeHtml(rawArtist).ifBlank { "Various Artists" }

        val rawAlbum = item.optString("album", "JioSaavn")
        val album = unescapeHtml(rawAlbum)

        val durationSec = item.optLong("duration", 0L)
        val durationMs = if (durationSec > 0) durationSec * 1000L else 0L

        // Album art: replace 150x150 with 500x500 for crisp high-res display
        val rawImage = item.optString("image", "")
        val highResImage = rawImage
            .replace("150x150", "500x500")
            .replace("50x50", "500x500")
            .takeIf { it.startsWith("http") }

        // Decrypt media URL
        val encryptedMediaUrl = item.optString("encrypted_media_url", "")
        val directStreamUrl = if (encryptedMediaUrl.isNotEmpty()) {
            decryptMediaUrl(encryptedMediaUrl)
        } else {
            item.optString("media_preview_url", "")
        }

        if (directStreamUrl.isNullOrEmpty() || !directStreamUrl.startsWith("http")) {
            return null
        }

        // Upgrade audio quality to 320kbps AAC if available
        val highQualityStreamUrl = directStreamUrl
            .replace("_96.mp4", "_320.mp4")
            .replace("_160.mp4", "_320.mp4")
            .replace("_48.mp4", "_320.mp4")
            .replace("_96.mp3", "_320.mp3")
            .replace("_160.mp3", "_320.mp3")

        val songId = idStr.hashCode().toLong() and 0x7FFFFFFF

        return Song(
            id = songId,
            title = title,
            artist = artist,
            album = album,
            duration = durationMs,
            uri = Uri.parse(highQualityStreamUrl),
            albumArtUri = highResImage?.let { Uri.parse(it) },
            genre = genreTag,
            audioFormat = AudioFormat.AAC_HQ,
            isStream = true,
            isPreview = false
        )
    }

    /**
     * Decrypts the JioSaavn DES-ECB encrypted media URL natively on the device.
     */
    fun decryptMediaUrl(encryptedUrl: String): String? {
        return try {
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, desKeySpec)
            val decodedBytes = Base64.decode(encryptedUrl, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            String(decryptedBytes, Charsets.UTF_8).trim()
        } catch (e: Exception) {
            Timber.w(e, "DES decryption failed for media URL")
            null
        }
    }

    private fun unescapeHtml(text: String): String {
        return try {
            Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString().trim()
        } catch (e: Exception) {
            text.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#039;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim()
        }
    }

    companion object {
        private const val BASE_URL = "https://www.jiosaavn.com/api.php"
        private const val DES_KEY = "38346591"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
