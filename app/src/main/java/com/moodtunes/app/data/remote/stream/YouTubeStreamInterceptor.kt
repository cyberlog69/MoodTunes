package com.moodtunes.app.data.remote.stream

import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Timeout
import okio.buffer
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Provider

const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
const val VISIONOS_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15"

/**
 * Intercepts audio playback requests on top of OkHttp:
 * - Transparently intercepts YouTube watch URLs (`music.youtube.com/watch`, `youtube.com/watch`, `youtu.be`)
 *   and resolves them synchronously into direct googlevideo audio streams using [StreamResolver],
 *   preventing ExoPlayer container parsing errors on queue, shuffle, and DJ mix transitions.
 * - Sends POST with request counter (`rn`) and client-appropriate headers for googlevideo streams.
 * - Converts open-ended long reads into 1 MB chunks with immediate pre-fetching of the next chunk,
 *   completely bypassing YouTube/GoogleVideo streaming speed throttling (which limits open-ended reads to 1x playback speed).
 * - Reports range responses as 206 so ExoPlayer smoothly streams without byte skips.
 * - Retries transient 403/410 errors with a brief delay, updated request counter, and automatic stream token refresh.
 */
class YouTubeStreamInterceptor(
    private val chunkClient: OkHttpClient? = null,
    private val streamResolverProvider: Provider<StreamResolver>? = null,
    private val chunkSize: Long = 1L shl 20 // 1 MB
) : Interceptor {

    private val requestNumber = AtomicInteger(0)

    private fun isYouTubeWatchUrl(url: HttpUrl): Boolean {
        val host = url.host
        val path = url.encodedPath
        return (host.contains("youtube.com") && (path.startsWith("/watch") || path.startsWith("/shorts/"))) ||
                host == "youtu.be"
    }

    private fun isStream(url: HttpUrl): Boolean =
        url.host.endsWith("googlevideo.com") && url.encodedPath.startsWith("/videoplayback")

    private fun streamRequest(base: Request, from: Long, to: Long?, ranged: Boolean): Request {
        val url = base.url
        val newUrl = url.newBuilder().setQueryParameter("rn", requestNumber.incrementAndGet().toString())
        if (ranged) {
            newUrl.setQueryParameter("range", "$from-${to ?: ""}")
        }
        val full = url.toString()
        val isVisionOs = full.contains("c=VISIONOS") || full.contains("client=VISIONOS")
        val builder = base.newBuilder()
            .url(newUrl.build())
            .removeHeader("Range")
            .post(byteArrayOf(0x78, 0).toRequestBody(null))
            .header("Accept-Encoding", "identity")
            .header("User-Agent", if (isVisionOs) VISIONOS_UA else DESKTOP_UA)

        if (!isVisionOs) {
            builder.header("Origin", "https://www.youtube.com")
                .header("Referer", "https://www.youtube.com/")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "cross-site")
        }
        return builder.build()
    }

    private fun withRetry(
        request: Request,
        resolvedFromWatch: String?,
        send: (Request) -> Response
    ): Response {
        val resp = send(request)
        if (resp.code != 403 && resp.code != 410) return resp
        resp.close()
        try {
            Thread.sleep(350)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        val retryUrl = request.url.newBuilder()
            .setQueryParameter("rn", requestNumber.incrementAndGet().toString())
            .build()
        val retryResp = send(request.newBuilder().url(retryUrl).build())
        if (retryResp.code != 403 && retryResp.code != 410) return retryResp

        // If retry still returned 403/410, and stream resolver is available, refresh the stream URL
        val resolver = streamResolverProvider?.get()
        if (resolver != null) {
            val videoId = request.url.queryParameter("docid")
                ?: resolvedFromWatch?.let { resolver.extractVideoId(it) }
            if (!videoId.isNullOrBlank()) {
                retryResp.close()
                Timber.w("Stream token expired for videoId=%s (HTTP 403/410), refreshing stream...", videoId)
                resolver.evict(videoId)
                return runCatching {
                    val freshResult = resolver.resolveSync(videoId)
                    val freshHttpUrl = freshResult.url.toHttpUrlOrNull()
                    if (freshHttpUrl != null) {
                        val freshReq = streamRequest(
                            request.newBuilder().url(freshHttpUrl).build(),
                            0,
                            null,
                            request.url.queryParameter("range") != null
                        )
                        send(freshReq)
                    } else {
                        retryResp
                    }
                }.getOrElse {
                    send(request)
                }
            }
        }
        return retryResp
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        var req = chain.request()
        var url = req.url
        var resolvedFromWatch: String? = null

        // Transparently resolve YouTube watch URLs if requested directly by ExoPlayer
        if (isYouTubeWatchUrl(url)) {
            val watchUrl = url.toString()
            val resolver = streamResolverProvider?.get()
            if (resolver != null) {
                try {
                    val streamResult = resolver.resolveSync(watchUrl)
                    val targetHttpUrl = streamResult.url.toHttpUrlOrNull()
                    if (targetHttpUrl != null) {
                        resolvedFromWatch = watchUrl
                        req = req.newBuilder().url(targetHttpUrl).build()
                        url = req.url
                        Timber.i("Transparently resolved YouTube watch URL to direct audio stream: %s", watchUrl)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "YouTubeStreamInterceptor: Failed to resolve watch URL: %s", url)
                    throw IOException("Failed to resolve stream for YouTube URL: $url", e)
                }
            }
        }

        if (!isStream(url)) return chain.proceed(req)

        if (url.queryParameter("range") != null) {
            return withRetry(streamRequest(req, 0, null, false), resolvedFromWatch, chain::proceed)
        }

        val rangeHeader = req.header("Range")
        val m = rangeHeader?.let { Regex("bytes=(\\d+)-(\\d*)").find(it) }
        val start = m?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val clen = url.queryParameter("clen")?.toLongOrNull()
        val end = m?.groupValues?.get(2)?.toLongOrNull()?.let { e ->
            clen?.let { minOf(e, it - 1) } ?: e
        } ?: clen?.minus(1)

        // If chunkClient is present and request is larger than chunkSize, serve via ChunkedBody with prefetch
        if (chunkClient != null && clen != null && end != null && (end - start + 1) > chunkSize) {
            val firstEnd = minOf(end, start + chunkSize - 1)
            val first = withRetry(streamRequest(req, start, firstEnd, true), resolvedFromWatch, chain::proceed)
            if (!first.isSuccessful) return first
            val len = end - start + 1
            val body = ChunkedBody(first, start, end, req)
            val rb = first.newBuilder()
                .body(body)
                .removeHeader("Content-Length")
                .removeHeader("Content-Range")
                .header("Content-Length", len.toString())

            return if (m != null) {
                rb.code(206)
                    .message("Partial Content")
                    .header("Content-Range", "bytes $start-$end/$clen")
                    .build()
            } else {
                rb.code(200)
                    .message("OK")
                    .build()
            }
        }

        val translated = m != null && !(start == 0L && m.groupValues[2].isEmpty())
        val resp = withRetry(
            streamRequest(req, start, m?.groupValues?.get(2)?.toLongOrNull(), translated),
            resolvedFromWatch,
            chain::proceed
        )
        if (translated && resp.code == 200) {
            val len = resp.header("Content-Length")?.toLongOrNull()
            val rb = resp.newBuilder().code(206).message("Partial Content")
            if (len != null) {
                rb.header("Content-Range", "bytes $start-${start + len - 1}/${clen ?: "*"}")
            }
            return rb.build()
        }
        return resp
    }

    private inner class ChunkedBody(
        first: Response,
        start: Long,
        private val end: Long,
        private val base: Request
    ) : ResponseBody() {
        private val type = first.body?.contentType()
        private var pos = start
        private var current: Response? = first
        private var currentEnd = minOf(end, start + chunkSize - 1)
        private var next: Pair<Long, Future<Response>>? = null

        private fun fetch(from: Long): Future<Response> {
            val to = minOf(end, from + chunkSize - 1)
            val f = CompletableFuture<Response>()
            val call = chunkClient!!.newCall(streamRequest(base, from, to, true))
            f.whenComplete { _, _ -> if (f.isCancelled) call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    f.completeExceptionally(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.code == 403) {
                        response.close()
                        runCatching {
                            chunkClient.newCall(streamRequest(base, from, to, true)).execute()
                        }.onSuccess { retryResp ->
                            if (!f.complete(retryResp)) retryResp.close()
                        }.onFailure { err ->
                            f.completeExceptionally(err)
                        }
                    } else if (!f.complete(response)) {
                        response.close()
                    }
                }
            })
            return f
        }

        private fun prefetch() {
            val from = currentEnd + 1
            if (from <= end && next == null) {
                next = from to fetch(from)
            }
        }

        private fun open() {
            current?.close()
            current = null
            val pending = next?.takeIf { it.first == pos }
            if (pending == null) {
                next?.second?.cancel(true)
            }
            next = null
            val resp = try {
                (pending?.second ?: fetch(pos)).get()
            } catch (e: ExecutionException) {
                throw (e.cause as? IOException) ?: IOException(e.cause)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw java.io.InterruptedIOException()
            }
            if (!resp.isSuccessful) {
                resp.close()
                throw IOException("Stream chunk at $pos failed: HTTP ${resp.code}")
            }
            current = resp
            currentEnd = minOf(end, pos + chunkSize - 1)
        }

        private val source = object : okio.Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (pos > end) return -1
                while (true) {
                    val resp = current ?: run { open(); current!! }
                    if (next == null) prefetch()
                    val bodySource = resp.body?.source() ?: return -1
                    val n = bodySource.read(sink, minOf(byteCount, end - pos + 1))
                    if (n > 0) {
                        pos += n
                        return n
                    }
                    if (pos > end) return -1
                    current?.close()
                    current = null
                }
            }

            override fun timeout() = Timeout.NONE

            override fun close() {
                current?.close()
                next?.second?.let { f ->
                    f.cancel(true)
                    if (f.isDone && !f.isCancelled) {
                        runCatching { f.get().close() }
                    }
                }
                next = null
            }
        }.buffer()

        override fun contentType() = type
        private val length = end - start + 1
        override fun contentLength() = length
        override fun source(): BufferedSource = source
    }
}
