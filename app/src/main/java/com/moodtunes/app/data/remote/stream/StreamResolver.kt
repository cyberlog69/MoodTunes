package com.moodtunes.app.data.remote.stream

import android.content.Context
import android.net.Uri
import com.moodtunes.app.data.local.preferences.StreamQuality
import com.moodtunes.app.data.local.preferences.UserPreferencesRepository
import com.moodtunes.app.data.remote.api.YouTubeMusicApiService
import com.moodtunes.app.data.remote.stream.YouTubePlayerClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
    private val youTubeMusicApiService: YouTubeMusicApiService,
    private val youTubePlayerClient: YouTubePlayerClient,
    private val userPreferencesRepository: UserPreferencesRepository
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

    // NewPipeExtractor is a best-effort fast path. When it starts failing
    // (YouTube changed a client again), stop paying its timeout on every track
    // and let the InnerTube clients take over for a while.
    @Volatile
    private var newPipeConsecutiveFailures = 0

    @Volatile
    private var newPipeDisabledUntil = 0L

    init {
        try {
            val country = Locale.getDefault().country.ifBlank { "US" }
            val lang = Locale.getDefault().language.ifBlank { "en" }
            val newPipeHttp = http.newBuilder()
                .callTimeout(NEWPIPE_CALL_TIMEOUT_S, TimeUnit.SECONDS)
                .build()
            NewPipe.init(
                NewPipeDownloader(newPipeHttp),
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
            val result = resolveFormatsSuspending(videoId)
            cache[videoId] = result
            result.url
        } catch (e: Exception) {
            Timber.e(e, "StreamResolver failed for $videoId, falling back to VISIONOS API")
            youTubeMusicApiService.resolveStreamUrl(videoId)
        }
    }

    /**
     * Coroutine-friendly variant of [resolveFormats]: it awaits the shared
     * in-flight resolution instead of blocking the calling thread.
     */
    private suspend fun resolveFormatsSuspending(videoId: String): StreamResult {
        val mine = CompletableFuture<StreamResult>()
        val running = inflight.putIfAbsent(videoId, mine)
        if (running != null) return awaitStream(running)

        return try {
            val result = loadStreamSuspending(videoId)
            mine.complete(result)
            result
        } catch (e: Throwable) {
            mine.completeExceptionally(e)
            throw e
        } finally {
            inflight.remove(videoId, mine)
        }
    }

    private suspend fun awaitStream(future: CompletableFuture<StreamResult>): StreamResult =
        suspendCancellableCoroutine { continuation ->
            future.whenComplete { result, error ->
                when {
                    error != null -> continuation.resumeWithException(error.cause ?: error)
                    result != null -> continuation.resume(result)
                    else -> continuation.resumeWithException(
                        IOException("Stream resolution completed without a result")
                    )
                }
            }
        }

    private suspend fun loadStreamSuspending(videoId: String): StreamResult {
        // Tier 1: anonymous NewPipe resolution (blocking extractor work on IO)
        val newPipeResult = if (shouldAttemptNewPipe()) {
            withContext(Dispatchers.IO) {
                runCatching { resolveAnonymousNewPipe(videoId) }
                    .onSuccess { onNewPipeSuccess() }
                    .onFailure { onNewPipeFailure(it) }
                    .getOrNull()
            }
        } else {
            Timber.i("Skipping NewPipe for %s (temporarily disabled)", videoId)
            null
        }
        if (newPipeResult != null && newPipeResult.url.isNotBlank()) {
            return newPipeResult
        }

        // Tier 2: multi-client InnerTube player (suspend, non-blocking)
        Timber.i("NewPipe resolution failed for $videoId; trying InnerTube player clients")
        val quality = userPreferencesRepository.getActiveStreamQuality()
        val playerStream = runCatching {
            youTubePlayerClient.resolveStream(videoId, quality)
        }.getOrNull()
        if (playerStream != null) {
            return playerStream.toStreamResult()
        }

        // Tier 3: single-client VISIONOS fallback
        Timber.i("InnerTube player clients failed for $videoId; falling back to VISIONOS")
        val visionOsUrl = runCatching {
            youTubeMusicApiService.resolveStreamUrl(videoId)
        }.getOrNull()

        return visionOsUrl?.let(::toStreamResult)
            ?: throw IOException("All stream resolution tiers exhausted for videoId: $videoId")
    }

    private fun YouTubePlayerClient.PlayerStream.toStreamResult(): StreamResult = StreamResult(
        url = url,
        bitrateKbps = bitrateKbps,
        mimeType = mimeType,
        codec = codec,
        sampleRate = sampleRate,
        contentLength = contentLength,
        expiresAtMs = expiresAtMs
    )

    private fun toStreamResult(visionOsUrl: String): StreamResult {
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

    /**
     * Blocking resolution used by ExoPlayer's loader thread. The coroutine
     * bridge here is intentional and confined to this non-suspend entry point.
     */
    private fun loadStream(videoId: String): StreamResult {
        // Tier 1: Try anonymous NewPipeExtractor fast-path
        val newPipeResult = if (shouldAttemptNewPipe()) {
            runCatching { resolveAnonymousNewPipe(videoId) }
                .onSuccess { onNewPipeSuccess() }
                .onFailure { onNewPipeFailure(it) }
                .getOrNull()
        } else {
            Timber.i("Skipping NewPipe for %s (temporarily disabled)", videoId)
            null
        }
        if (newPipeResult != null && newPipeResult.url.isNotBlank()) {
            return newPipeResult
        }

        // Tier 2: multi-client InnerTube player fallback
        Timber.i("NewPipe resolution failed for $videoId; trying InnerTube player clients")
        val quality = userPreferencesRepository.getActiveStreamQuality()
        val playerStream = runCatching {
            kotlinx.coroutines.runBlocking {
                youTubePlayerClient.resolveStream(videoId, quality)
            }
        }.getOrNull()
        if (playerStream != null) {
            return playerStream.toStreamResult()
        }

        // Tier 3: VISIONOS Client
        Timber.i("InnerTube player clients failed for $videoId; falling back to VISIONOS player")
        val visionOsUrl = runCatching {
            // Note: resolveStreamUrl in youTubeMusicApiService uses VISIONOS client context
            kotlinx.coroutines.runBlocking {
                youTubeMusicApiService.resolveStreamUrl(videoId)
            }
        }.getOrNull()

        if (!visionOsUrl.isNullOrBlank()) {
            return toStreamResult(visionOsUrl)
        }

        throw IOException("All stream resolution tiers exhausted for videoId: $videoId")
    }

    private fun shouldAttemptNewPipe(): Boolean =
        System.currentTimeMillis() >= newPipeDisabledUntil

    private fun onNewPipeSuccess() {
        newPipeConsecutiveFailures = 0
        newPipeDisabledUntil = 0L
    }

    private fun onNewPipeFailure(error: Throwable) {
        newPipeConsecutiveFailures++
        if (newPipeConsecutiveFailures >= NEWPIPE_FAILURE_THRESHOLD) {
            newPipeDisabledUntil = System.currentTimeMillis() + NEWPIPE_COOLDOWN_MS
            Timber.w(
                error,
                "NewPipe disabled for %d min after %d consecutive failure(s)",
                NEWPIPE_COOLDOWN_MS / 60_000,
                newPipeConsecutiveFailures
            )
        }
    }

    private fun resolveAnonymousNewPipe(videoId: String): StreamResult {
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        val audioStreams = info.audioStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        if (audioStreams.isEmpty()) {
            throw IOException("No progressive audio streams found via NewPipe for $videoId")
        }

        // Pick audio stream based on active network quality ceiling (Wi-Fi vs Mobile)
        val quality = userPreferencesRepository.getActiveStreamQuality()
        val bestAudio: AudioStream = when (quality) {
            StreamQuality.DATA_SAVER -> {
                audioStreams.minByOrNull { it.averageBitrate.takeIf { b -> b > 0 } ?: (it.bitrate / 1000) }
                    ?: audioStreams.first()
            }
            StreamQuality.STANDARD -> {
                audioStreams.minByOrNull {
                    val kbps = it.averageBitrate.takeIf { b -> b > 0 } ?: (it.bitrate / 1000)
                    Math.abs(kbps - 128)
                } ?: audioStreams.first()
            }
            else -> {
                audioStreams
                    .sortedWith(compareByDescending<AudioStream> { it.format?.mimeType?.contains("webm") == true }
                        .thenByDescending { it.averageBitrate.takeIf { b -> b > 0 } ?: (it.bitrate / 1000) })
                    .first()
            }
        }

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

    /**
     * Evicts a cached stream result for [videoIdOrUrl], allowing a fresh stream to be fetched.
     */
    fun evict(videoIdOrUrl: String) {
        val videoId = extractVideoId(videoIdOrUrl)
        if (videoId.isNotBlank()) {
            cache.remove(videoId)
        }
    }

    /**
     * Returns a valid cached stream result for [videoIdOrUrl], or null if absent or expired.
     */
    fun getCached(videoIdOrUrl: String): StreamResult? {
        val videoId = extractVideoId(videoIdOrUrl)
        if (videoId.isBlank()) return null
        val now = System.currentTimeMillis()
        val cached = cache[videoId] ?: return null
        return if (now < cached.expiresAtMs) {
            cached
        } else {
            cache.remove(videoId)
            null
        }
    }

    fun extractVideoId(target: String): String {
        val trimmed = target.trim()
        if (trimmed.length == 11 && !trimmed.contains("/") && !trimmed.contains("?") && !trimmed.contains("&")) {
            return trimmed
        }
        runCatching {
            val uri = Uri.parse(trimmed)
            val v = uri.getQueryParameter("v")
            if (!v.isNullOrBlank() && v.length == 11) return v
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

    private companion object {
        const val NEWPIPE_CALL_TIMEOUT_S = 8L
        const val NEWPIPE_FAILURE_THRESHOLD = 1
        const val NEWPIPE_COOLDOWN_MS = 10 * 60_000L
    }
}
