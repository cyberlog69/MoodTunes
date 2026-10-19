package com.moodtunes.app.data.remote.stream

import android.content.Context
import android.net.Uri
import com.moodtunes.app.data.remote.api.YouTubeMusicApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response as NpResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import timber.log.Timber
import java.io.IOException
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resilient, multi-tier YouTube stream resolver:
 * 1. Primary (Fast Path): NewPipeExtractor anonymous resolution for high-bitrate Opus (160 kbps) / AAC (130 kbps).
 * 2. Fallback (Protected Path): VISIONOS client query via YouTubeMusicApiService when NewPipe encounters bot blocks.
 * 3. Cipher Solving: PlayerJsSolver to decipher `signatureCipher` and solve the `n` throttling challenge.
 * 4. Thread-Safe In-Flight Deduplication & In-Memory TTL Caching.
 */
@Singleton
class StreamResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
    private val solver: PlayerJsSolver,
    private val youTubeMusicApiService: YouTubeMusicApiService
) {
    data class StreamResult(
        val url: String,
        val bitrateKbps: Int,
        val mimeType: String,
        val codec: String?,
        val sampleRate: Int,
        val contentLength: Long,
        val expiresAtMs: Long
    )

    private val cache = ConcurrentHashMap<String, StreamResult>()
    private val inflight = ConcurrentHashMap<String, CompletableFuture<StreamResult>>()

    init {
        try {
            val country = Locale.getDefault().country.ifBlank { "US" }
            val lang = Locale.getDefault().language.ifBlank { "en" }
            NewPipe.init(
                NewPipeDownloader(http),
                Localization(lang, country),
                ContentCountry(country)
            )
        } catch (e: Exception) {
            Timber.w(e, "NewPipe initialization warning")
        }
    }

    /**
     * Resolves the direct, unthrottled streaming URL for a given [videoIdOrUrl].
     */
    suspend fun resolveStreamUrl(videoIdOrUrl: String): String? {
        val videoId = extractVideoId(videoIdOrUrl)
        if (videoId.isBlank()) return null

        val now = System.currentTimeMillis()
        cache[videoId]?.let { cached ->
            if (now < cached.expiresAtMs) {
                return cached.url
            } else {
                cache.remove(videoId)
            }
        }

        return try {
            val result = resolveFormats(videoId)
            cache[videoId] = result
            result.url
        } catch (e: Exception) {
            Timber.e(e, "StreamResolver failed for $videoId, falling back to VISIONOS API")
            youTubeMusicApiService.resolveStreamUrl(videoId)
        }
    }

    /**
     * Synchronous/blocking resolver for background ExoPlayer loader threads.
     */
    fun resolveSync(videoIdOrUrl: String): StreamResult {
        val videoId = extractVideoId(videoIdOrUrl)
        if (videoId.isBlank()) throw IOException("Invalid videoId: $videoIdOrUrl")

        val now = System.currentTimeMillis()
        cache[videoId]?.let { cached ->
            if (now < cached.expiresAtMs) return cached
            cache.remove(videoId)
        }

        val result = resolveFormats(videoId)
        cache[videoId] = result
        return result
    }

    private fun resolveFormats(videoId: String): StreamResult {
        val mine = CompletableFuture<StreamResult>()
        val running = inflight.putIfAbsent(videoId, mine)
        if (running != null) {
            return try {
                running.get()
            } catch (e: ExecutionException) {
                throw (e.cause as? IOException) ?: IOException(e.cause?.message ?: "Stream resolution failed", e.cause)
            }
        }

        try {
            val result = loadStream(videoId)
            mine.complete(result)
            return result
        } catch (e: Throwable) {
            mine.completeExceptionally(e)
            throw e
        } finally {
            inflight.remove(videoId, mine)
        }
    }

    private fun loadStream(videoId: String): StreamResult {
        // Tier 1: Try anonymous NewPipeExtractor fast-path
        val newPipeResult = runCatching { resolveAnonymousNewPipe(videoId) }.getOrNull()
        if (newPipeResult != null && newPipeResult.url.isNotBlank()) {
            return newPipeResult
        }

        // Tier 2: Fallback to VISIONOS Client
        Timber.i("NewPipe resolution failed for $videoId; falling back to VISIONOS player")
        val visionOsUrl = runCatching {
            // Note: resolveStreamUrl in youTubeMusicApiService uses VISIONOS client context
            kotlinx.coroutines.runBlocking {
                youTubeMusicApiService.resolveStreamUrl(videoId)
            }
        }.getOrNull()

        if (!visionOsUrl.isNullOrBlank()) {
            val uri = Uri.parse(visionOsUrl)
            val expire = uri.getQueryParameter("expire")?.toLongOrNull()?.let { it * 1000 - 10 * 60_000 }
                ?: (System.currentTimeMillis() + 60 * 60_000)
            val clen = uri.getQueryParameter("clen")?.toLongOrNull() ?: -1L

            return StreamResult(
                url = visionOsUrl,
                bitrateKbps = 160,
                mimeType = "audio/webm",
                codec = "opus",
                sampleRate = 48000,
                contentLength = clen,
                expiresAtMs = expire
            )
        }

        throw IOException("All stream resolution tiers exhausted for videoId: $videoId")
    }

    private fun resolveAnonymousNewPipe(videoId: String): StreamResult {
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        val audioStreams = info.audioStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        if (audioStreams.isEmpty()) {
            throw IOException("No progressive audio streams found via NewPipe for $videoId")
        }

        // Pick highest quality audio stream (Opus preferred, otherwise AAC)
        val bestAudio: AudioStream = audioStreams
            .sortedWith(compareByDescending<AudioStream> { it.format?.mimeType?.contains("webm") == true }
                .thenByDescending { it.averageBitrate.takeIf { b -> b > 0 } ?: (it.bitrate / 1000) })
            .first()

        val rawUrl = bestAudio.content
        val finalUrl = decipherIfNeeded(rawUrl)

        val uri = Uri.parse(finalUrl)
        val expire = uri.getQueryParameter("expire")?.toLongOrNull()?.let { it * 1000 - 10 * 60_000 }
            ?: (System.currentTimeMillis() + 60 * 60_000)
        val clen = bestAudio.itagItem?.contentLength?.takeIf { it > 0 }
            ?: uri.getQueryParameter("clen")?.toLongOrNull()
            ?: -1L

        val nominalKbps = bestAudio.averageBitrate.takeIf { it > 0 } ?: (bestAudio.bitrate / 1000)

        return StreamResult(
            url = finalUrl,
            bitrateKbps = nominalKbps,
            mimeType = bestAudio.format?.mimeType ?: "audio/webm",
            codec = bestAudio.codec,
            sampleRate = bestAudio.itagItem?.sampleRate ?: 48000,
            contentLength = clen,
            expiresAtMs = expire
        )
    }

    private fun decipherIfNeeded(urlStr: String): String {
        val url = urlStr.toHttpUrlOrNull() ?: return urlStr
        val n = url.queryParameter("n")
        val s = url.queryParameter("s") ?: url.queryParameter("sig")

        if (n == null && s == null) return urlStr

        return try {
            val (playerId, _) = solver.currentPlayer()
            val (solvedSig, solvedN) = solver.solve(playerId, s, n)
            url.newBuilder().apply {
                if (solvedSig != null) {
                    val param = if (url.queryParameter("sig") != null) "sig" else "signature"
                    setQueryParameter(param, solvedSig)
                }
                if (solvedN != null) {
                    setQueryParameter("n", solvedN)
                }
            }.build().toString()
        } catch (e: Exception) {
            Timber.w(e, "Deciphering challenge failed, using original stream URL")
            urlStr
        }
    }

    private fun extractVideoId(target: String): String {
        val trimmed = target.trim()
        if (trimmed.length == 11 && !trimmed.contains("/") && !trimmed.contains("?")) {
            return trimmed
        }
        val videoIdRegex = Regex("""(?:v=|youtu\.be/|vi/|embed/|shorts/|music\.youtube\.com/watch\?v=)([a-zA-Z0-9_-]{11})""")
        return videoIdRegex.find(trimmed)?.groupValues?.get(1) ?: trimmed
    }

    /** Bridges NewPipeExtractor's HTTP needs to OkHttp. */
    private class NewPipeDownloader(private val client: OkHttpClient) : Downloader() {
        override fun execute(request: Request): NpResponse {
            val data = request.dataToSend()
            val body = when {
                data != null -> data.toRequestBody()
                request.httpMethod() == "POST" -> ByteArray(0).toRequestBody()
                else -> null
            }
            val builder = okhttp3.Request.Builder()
                .url(request.url())
                .method(request.httpMethod(), body)
                .header("User-Agent", DESKTOP_UA)

            request.headers().forEach { (name, values) ->
                builder.removeHeader(name)
                values.forEach { builder.addHeader(name, it) }
            }

            client.newCall(builder.build()).execute().use { r ->
                if (r.code == 429) throw ReCaptchaException("reCaptcha challenge requested", request.url())
                return NpResponse(
                    r.code,
                    r.message,
                    r.headers.toMultimap(),
                    r.body?.string().orEmpty(),
                    r.request.url.toString()
                )
            }
        }
    }
}
