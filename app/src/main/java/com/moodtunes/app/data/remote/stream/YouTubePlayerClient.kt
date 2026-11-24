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
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * InnerTube `/player` client with several fallbacks.
 *
 * YouTube enforces PO tokens and SABR per client at different times, so the
 * working client changes. As of late 2026 VISIONOS is the reliable anonymous
 * client, ANDROID_VR is a fast backup, and the cookie-only clients are tried
 * after sign-in. [PlayerClientHealthStore] remembers the last winner and
 * demotes failures for a while so dead clients stay off the hot path.
 *
 * Every signed URL is normalised through [PlayerJsSolver] when it actually
 * contains a `sig` cipher or `n` challenge; clients that return already-signed
 * URLs skip the JS player entirely.
 */
@Singleton
class YouTubePlayerClient @Inject constructor(
    private val http: OkHttpClient,
    private val solver: PlayerJsSolver,
    private val sessionStore: YouTubeSessionStore,
    private val healthStore: PlayerClientHealthStore
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

    internal data class ClientProfile(
        val name: String,
        val clientName: String,
        val version: String,
        val numericId: String,
        val userAgent: String,
        val extra: Map<String, Any> = emptyMap(),
        val contextExtra: Map<String, Any> = emptyMap(),
        val requiresAuth: Boolean = false,
        val needsJsPlayer: Boolean = true
    )

    private val clients = listOf(
        // Anonymous clients. VISIONOS is the only remaining client that
        // consistently returns direct audio URLs without a PO token or a JS
        // player; ANDROID_VR is the fastest backup on current builds.
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
            ),
            needsJsPlayer = false
        ),
        ClientProfile(
            name = "ANDROID_VR",
            clientName = "ANDROID_VR",
            version = "1.65.10",
            numericId = "28",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            extra = mapOf(
                "deviceMake" to "Oculus",
                "deviceModel" to "Quest 3",
                "androidSdkVersion" to 32,
                "osName" to "Android",
                "osVersion" to "12L"
            ),
            needsJsPlayer = false
        ),
        // Authenticated clients. Web cookies do not work with ANDROID_MUSIC,
        // but keep it available for future native-token support; WEB_REMIX is
        // the client that accepts the web session captured by the account UI.
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
            ),
            requiresAuth = true,
            needsJsPlayer = false
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
            contextExtra = mapOf("clientScreen" to "WATCH"),
            requiresAuth = true,
            needsJsPlayer = true
        ),
        // TV accepts cookies but returns "page needs to be reloaded" without
        // them, so it is authenticated-only. IOS currently returns SABR-only
        // responses (no direct URLs) and stays last until SABR is supported.
        ClientProfile(
            name = "TVHTML5",
            clientName = "TVHTML5",
            version = "5.20260707",
            numericId = "7",
            userAgent = "Mozilla/5.0 (SMART-TV; Linux; Tizen 2.4.0) AppleWebKit/538.1 (KHTML, like Gecko) Version/2.4.0 TV Safari/538.1",
            extra = mapOf(
                "deviceMake" to "Samsung",
                "deviceModel" to "UKS9800",
                "osName" to "Tizen",
                "osVersion" to "2.4.0"
            ),
            requiresAuth = true,
            needsJsPlayer = true
        ),
        ClientProfile(
            name = "IOS",
            clientName = "IOS",
            version = "21.26.4",
            numericId = "5",
            userAgent = "com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X)",
            extra = mapOf(
                "deviceMake" to "Apple",
                "deviceModel" to "iPhone16,2",
                "osName" to "iPhone OS",
                "osVersion" to "18.3.2.22D82"
            ),
            needsJsPlayer = false
        )
    )

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var cachedSignatureTimestamp: Int? = null

    suspend fun resolveStream(videoId: String, quality: StreamQuality = StreamQuality.AUTO): PlayerStream? =
        withContext(Dispatchers.IO) {
            if (videoId.isBlank()) return@withContext null

            val ordered = orderClientProfiles(
                profiles = clients,
                signedIn = sessionStore.isSignedIn.value,
                lastGood = healthStore.lastGoodClient(),
                cooling = healthStore.coolingClients()
            ).ifEmpty { clients }

            for (client in ordered) {
                val response = requestPlayer(client, videoId)
                if (response == null) {
                    healthStore.recordFailure(client.name)
                    continue
                }
                val stream = runCatching { extractStream(response, videoId, quality, client.name) }
                    .getOrNull()
                if (stream != null) {
                    healthStore.recordSuccess(client.name)
                    Timber.i("Resolved %s via %s (%d kbps)", videoId, client.name, stream.bitrateKbps)
                    return@withContext stream
                }
                healthStore.recordFailure(client.name)
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
                if (client.needsJsPlayer) {
                    signatureTimestamp()?.let { put("signatureTimestamp", it) }
                }
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
            .url(playerEndpoint(client))
            .post(body.toString().toRequestBody(jsonMediaType))
            .addHeader("User-Agent", client.userAgent)
            .addHeader("X-YouTube-Client-Name", client.numericId)
            .addHeader("X-YouTube-Client-Version", client.version)
            .addHeader("Origin", playerOrigin(client))
            .addHeader("Referer", "${playerOrigin(client)}/")
            .addHeader("Accept-Language", "en-US,en;q=0.9")
        if (!visitor.isNullOrBlank()) builder.addHeader("X-Goog-Visitor-Id", visitor)
        sessionStore.cookieHeader()?.let { builder.addHeader("Cookie", it) }
        sessionStore.sapisidHash()?.let { builder.addHeader("Authorization", it) }

        return runCatching {
            val call = http.newCall(builder.build())
            // Bound each client attempt; five clients at the shared 20s read
            // timeout would otherwise mean up to 100s before falling back.
            call.timeout().timeout(CLIENT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            call.execute().use { response ->
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
        if (audio.isEmpty()) {
            Timber.w("%s returned no direct audio formats (SABR-only or blocked)", client)
            return null
        }

        val ranked = rankAudioFormats(audio, quality)

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

    private fun playerOrigin(client: ClientProfile): String =
        if (client.clientName == "WEB_REMIX") "https://music.youtube.com" else "https://www.youtube.com"

    private fun playerEndpoint(client: ClientProfile): String =
        "${playerOrigin(client)}/youtubei/v1/player?prettyPrint=false"

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

    private companion object {
        const val CLIENT_TIMEOUT_SECONDS = 8L
    }
}

/**
 * Builds the client attempt order:
 * 1. clients allowed for the current session (cookie-only clients are hidden
 *    when signed out),
 * 2. clients not in their failure cooldown,
 * 3. the last client that produced a stream first, then the static priority.
 *
 * If every client is cooling down, the full allowed set is used so a stale
 * health entry can never make playback impossible.
 */
internal fun orderClientProfiles(
    profiles: List<YouTubePlayerClient.ClientProfile>,
    signedIn: Boolean,
    lastGood: String?,
    cooling: Set<String>
): List<YouTubePlayerClient.ClientProfile> {
    val available = profiles.filter { !it.requiresAuth || signedIn }
    if (available.isEmpty()) return emptyList()

    val healthy = available.filterNot { it.name in cooling }
    val pool = healthy.ifEmpty { available }

    // With a connected account, WEB_REMIX is the only client that can return
    // YouTube Music's 256 kbps AAC/Opus formats, so it gets first refusal.
    // If it fails, the health store removes it and the last-good anonymous
    // client takes over.
    val authPreferred = if (signedIn) setOf("WEB_REMIX") else emptySet()

    return pool.sortedWith(
        compareByDescending<YouTubePlayerClient.ClientProfile> { it.name in authPreferred }
            .thenByDescending { it.name == lastGood }
            .thenBy { profiles.indexOf(it) }
    )
}

/**
 * Ranks audio formats for a quality preference.
 *
 * Auto is bitrate-first on purpose: a 256 kbps AAC track (Premium, itag 141)
 * is better than a 160 kbps Opus track (itag 251). Opus only wins on a tie.
 * High caps at ~192 kbps so it stays data-efficient; Maximum always takes the
 * highest available format.
 */
internal fun rankAudioFormats(
    formats: List<JSONObject>,
    quality: StreamQuality
): List<JSONObject> {
    fun bitrate(format: JSONObject) = format.optInt("bitrate", 0)

    return when (quality) {
        StreamQuality.DATA_SAVER -> formats.sortedBy(::bitrate)
        StreamQuality.STANDARD -> formats.sortedBy { Math.abs(bitrate(it) - 128_000) }
        StreamQuality.HIGH -> {
            val capped = formats.filter { bitrate(it) in 1..192_000 }
            (capped.ifEmpty { formats }).sortedByDescending(::bitrate)
        }
        StreamQuality.LOSSLESS -> formats.sortedByDescending(::bitrate)
        StreamQuality.AUTO -> formats.sortedWith(
            compareByDescending<JSONObject>(::bitrate)
                .thenByDescending {
                    it.optString("mimeType").contains("opus", ignoreCase = true) ||
                        it.optString("mimeType").contains("webm", ignoreCase = true)
                }
        )
    }
}
