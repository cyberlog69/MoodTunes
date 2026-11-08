package com.moodtunes.app.data.remote.stream

import com.moodtunes.app.data.local.preferences.StreamQuality
import com.moodtunes.app.data.remote.api.YouTubeSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * InnerTube `/player` client with several fallbacks.
 *
 * YouTube blocks each client family differently: ANDROID_MUSIC usually avoids
 * web bot checks, TVHTML5 survives signature changes, IOS is resilient for
 * age-gated tracks and VISIONOS is a good anonymous last resort. Trying them in
 * order is what makes a stream resolve on the first or second attempt instead
 * of failing the whole playback session.
 *
 * Every signed URL is normalised through [PlayerJsSolver] so both the `sig`
 * cipher and the `n` throttling parameter are solved before ExoPlayer sees it.
 */
@Singleton
class YouTubePlayerClient @Inject constructor(
    private val http: OkHttpClient,
    private val solver: PlayerJsSolver,
    private val sessionStore: YouTubeSessionStore
) {
    data class PlayerStream(
        val url: String,
        val bitrateKbps: Int,
        val mimeType: String,
        val codec: String?,
        val sampleRate: Int,
        val contentLength: Long,
        val expiresAtMs: Long,
        val client: String
    )

    private data class ClientProfile(
        val name: String,
        val clientName: String,
        val version: String,
        val numericId: String,
        val userAgent: String,
        val extra: Map<String, Any> = emptyMap(),
        val contextExtra: Map<String, Any> = emptyMap()
    )

    private val clients = listOf(
        ClientProfile(
            name = "ANDROID_MUSIC",
            clientName = "ANDROID_MUSIC",
            version = "8.39.42",
            numericId = "21",
            userAgent = "com.google.android.apps.youtube.music/8.39.42 (Linux; U; Android 14; en_US; Pixel 8 Build/UQ1A.240205.004) gzip",
            extra = mapOf(
                "androidSdkVersion" to 34,
                "osName" to "Android",
                "osVersion" to "14",
                "deviceMake" to "Google",
                "deviceModel" to "Pixel 8"
            )
        ),
        ClientProfile(
            name = "VISIONOS",
            clientName = "VISIONOS",
            version = "1.02",
            numericId = "101",
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
            extra = mapOf(
                "deviceMake" to "Apple",
                "deviceModel" to "RealityDevice17,1",
                "osName" to "visionOS",
                "osVersion" to "26.5.23O471"
            )
        ),
        ClientProfile(
            name = "TVHTML5",
            clientName = "TVHTML5",
            version = "7.20260707.07.00",
            numericId = "7",
            userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.0-qa (unlike Gecko) v8/8.8.278.8-jit gles Starboard/17, SystemIntegratorName/YouTube",
            extra = mapOf(
                "clientScreen" to "EMBED",
                "osName" to "Tizen",
                "osVersion" to "6.0"
            )
        ),
        ClientProfile(
            name = "IOS",
            clientName = "IOS",
            version = "21.26.4",
            numericId = "5",
            userAgent = "com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 18_5_0 like Mac OS X)",
            extra = mapOf(
                "deviceMake" to "Apple",
                "deviceModel" to "iPhone16,2",
                "osName" to "iPhone OS",
                "osVersion" to "18.5.0.15F79"
            )
        ),
        ClientProfile(
            name = "WEB_REMIX",
            clientName = "WEB_REMIX",
            version = "1.20260928.13.00",
            numericId = "67",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            extra = mapOf(
                "originalUrl" to "https://music.youtube.com/",
                "platform" to "DESKTOP"
            ),
            contextExtra = mapOf("clientScreen" to "WATCH")
        )
    )

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var cachedSignatureTimestamp: Int? = null

    suspend fun resolveStream(videoId: String, quality: StreamQuality = StreamQuality.AUTO): PlayerStream? =
        withContext(Dispatchers.IO) {
            if (videoId.isBlank()) return@withContext null

            for (client in clients) {
                val response = requestPlayer(client, videoId) ?: continue
                val stream = runCatching { extractStream(response, videoId, quality, client.name) }
                    .getOrNull()
                if (stream != null) {
                    Timber.i("Resolved %s via %s (%d kbps)", videoId, client.name, stream.bitrateKbps)
                    return@withContext stream
                }
            }
            null
        }

    private fun requestPlayer(client: ClientProfile, videoId: String): JSONObject? {
        val visitor = sessionStore.visitorData
        val clientNode = JSONObject().apply {
            put("clientName", client.clientName)
            put("clientVersion", client.version)
            put("hl", "en")
            put("gl", "US")
            client.extra.forEach { (key, value) -> put(key, value) }
            if (!visitor.isNullOrBlank()) put("visitorData", visitor)
        }
        client.contextExtra.forEach { (key, value) -> clientNode.put(key, value) }

        val playbackContext = JSONObject().apply {
            put("contentPlaybackContext", JSONObject().apply {
                put("html5Preference", "HTML5_PREF_WANTS")
                signatureTimestamp()?.let { put("signatureTimestamp", it) }
            })
        }

        val body = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", clientNode)
                put("user", JSONObject().put("lockedSafetyMode", false))
                put("request", JSONObject().put("useSsl", true))
            })
            put("videoId", videoId)
            put("playbackContext", playbackContext)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }

        val builder = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .post(body.toString().toRequestBody(jsonMediaType))
            .addHeader("User-Agent", client.userAgent)
            .addHeader("X-YouTube-Client-Name", client.numericId)
            .addHeader("X-YouTube-Client-Version", client.version)
            .addHeader("Origin", "https://www.youtube.com")
            .addHeader("Referer", "https://www.youtube.com/")
            .addHeader("Accept-Language", "en-US,en;q=0.9")
        if (!visitor.isNullOrBlank()) builder.addHeader("X-Goog-Visitor-Id", visitor)
        sessionStore.cookieHeader()?.let { builder.addHeader("Cookie", it) }
        sessionStore.sapisidHash()?.let { builder.addHeader("Authorization", it) }

        return runCatching {
            http.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("%s player HTTP %d", client.name, response.code)
                    return@use null
                }
                val payload = response.body?.string()?.takeIf { it.isNotBlank() } ?: return@use null
                val root = JSONObject(payload)
                val status = root.obj("playabilityStatus")
                val state = status?.optString("status")
                if (state != "OK") {
                    Timber.w(
                        "%s player status=%s reason=%s",
                        client.name,
                        state,
                        status?.optString("reason").orEmpty()
                    )
                    return@use null
                }
                root
            }
        }.getOrElse { error ->
            Timber.w(error, "%s player request failed", client.name)
            null
        }
    }

    private fun extractStream(
        root: JSONObject,
        videoId: String,
        quality: StreamQuality,
        client: String
    ): PlayerStream? {
        val streamingData = root.optJSONObject("streamingData") ?: return null
        val formats = mutableListOf<JSONObject>()
        streamingData.optJSONArray("adaptiveFormats")?.let { formats.addAll(it.objects()) }
        streamingData.optJSONArray("formats")?.let { formats.addAll(it.objects()) }

        val audio = formats.filter {
            it.optString("mimeType").startsWith("audio/") &&
                (it.has("url") || it.has("signatureCipher") || it.has("cipher"))
        }
        if (audio.isEmpty()) return null

        val ranked = when (quality) {
            StreamQuality.DATA_SAVER -> audio.sortedBy { it.optInt("bitrate", 0) }
            StreamQuality.STANDARD -> audio.sortedBy { Math.abs(it.optInt("bitrate", 0) - 128_000) }
            StreamQuality.HIGH -> audio.sortedByDescending { it.optInt("bitrate", 0) }
            StreamQuality.LOSSLESS -> audio.sortedByDescending { it.optInt("bitrate", 0) }
            StreamQuality.AUTO -> audio.sortedWith(
                compareByDescending<JSONObject> {
                    it.optString("mimeType").contains("opus", ignoreCase = true) ||
                        it.optString("mimeType").contains("webm", ignoreCase = true)
                }.thenByDescending { it.optInt("bitrate", 0) }
            )
        }

        for (format in ranked) {
            val url = runCatching { normaliseUrl(format) }.getOrNull() ?: continue
            val mimeType = format.optString("mimeType", "audio/webm").substringBefore(';').trim()
            val codec = Regex("""codecs="([^"]+)"""").find(format.optString("mimeType"))
                ?.groupValues?.get(1)
            val bitrateBps = format.optInt("bitrate", 0).takeIf { it > 0 } ?: 160_000
            val expiresAt = url.toHttpUrlOrNull()
                ?.queryParameter("expire")?.toLongOrNull()
                ?.let { it * 1000 - 10 * 60_000 }
                ?: (System.currentTimeMillis() + 60 * 60_000)
            val contentLength = format.optLong("contentLength", -1L)
                .takeIf { it > 0 }
                ?: url.toHttpUrlOrNull()?.queryParameter("clen")?.toLongOrNull()
                ?: -1L

            return PlayerStream(
                url = url,
                bitrateKbps = bitrateBps / 1000,
                mimeType = mimeType,
                codec = codec,
                sampleRate = format.optInt("sampleRate", 48_000),
                contentLength = contentLength,
                expiresAtMs = expiresAt,
                client = client
            )
        }
        return null
    }

    /**
     * Turns `url` / `signatureCipher` / `cipher` into a directly playable URL,
     * solving the signature and `n` challenge through the player JS sandbox.
     */
    private fun normaliseUrl(format: JSONObject): String? {
        var rawUrl = format.optString("url").takeIf { it.isNotBlank() }
        var signature: String? = null
        var signatureParam = "signature"

        if (rawUrl == null) {
            val cipher = format.optString("signatureCipher")
                .takeIf { it.isNotBlank() }
                ?: format.optString("cipher").takeIf { it.isNotBlank() }
                ?: return null
            val params = cipher.split('&').mapNotNull { pair ->
                val key = pair.substringBefore('=', "").takeIf { it.isNotBlank() }
                val value = pair.substringAfter('=', "")
                key?.let { it to android.net.Uri.decode(value) }
            }.toMap()
            rawUrl = params["url"] ?: return null
            signature = params["s"]
            signatureParam = params["sp"].orEmpty().ifBlank { "signature" }
        }

        val parsed = rawUrl.toHttpUrlOrNull() ?: return null
        val n = parsed.queryParameter("n")

        if (signature == null && n == null) return rawUrl

        return runCatching {
            val (playerId, _) = solver.currentPlayer()
            val (solvedSig, solvedN) = solver.solve(playerId, signature, n)
            parsed.newBuilder().apply {
                if (solvedSig != null) setQueryParameter(signatureParam, solvedSig)
                if (solvedN != null) setQueryParameter("n", solvedN)
            }.build().toString()
        }.getOrElse { error ->
            Timber.w(error, "Could not solve stream challenge, using the signed URL as-is")
            rawUrl
        }
    }

    private fun signatureTimestamp(): Int? {
        cachedSignatureTimestamp?.let { return it }
        return runCatching {
            solver.currentPlayer().second
        }.getOrNull()?.also { cachedSignatureTimestamp = it }
    }

    private fun JSONArray.objects(): List<JSONObject> {
        val items = mutableListOf<JSONObject>()
        for (index in 0 until length()) optJSONObject(index)?.let(items::add)
        return items
    }

    private fun JSONObject?.obj(vararg keys: String): JSONObject? {
        var cursor: JSONObject = this ?: return null
        for (key in keys) {
            cursor = cursor.optJSONObject(key) ?: return null
        }
        return cursor
    }
}
