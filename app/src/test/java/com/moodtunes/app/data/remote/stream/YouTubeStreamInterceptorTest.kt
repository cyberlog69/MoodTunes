package com.moodtunes.app.data.remote.stream

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeStreamInterceptorTest {

    @Test
    fun `non-googlevideo request passes through unmodified`() {
        var interceptedMethod = ""
        var interceptedUrl = ""

        val client = OkHttpClient.Builder()
            .addInterceptor(YouTubeStreamInterceptor())
            .addInterceptor { chain ->
                interceptedMethod = chain.request().method
                interceptedUrl = chain.request().url.toString()
                Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody())
                    .build()
            }
            .build()

        val req = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/browse")
            .build()

        client.newCall(req).execute().use { resp ->
            assertEquals(200, resp.code)
            assertEquals("GET", interceptedMethod)
            assertEquals("https://music.youtube.com/youtubei/v1/browse", interceptedUrl)
        }
    }

    @Test
    fun `googlevideo stream request receives POST, rn counter and stream headers`() {
        var interceptedMethod = ""
        var interceptedRn: String? = null
        var interceptedOrigin: String? = null
        var interceptedUa: String? = null

        val client = OkHttpClient.Builder()
            .addInterceptor(YouTubeStreamInterceptor())
            .addInterceptor { chain ->
                val r = chain.request()
                interceptedMethod = r.method
                interceptedRn = r.url.queryParameter("rn")
                interceptedOrigin = r.header("Origin")
                interceptedUa = r.header("User-Agent")

                Response.Builder()
                    .request(r)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("audio_bytes".toResponseBody())
                    .build()
            }
            .build()

        val req = Request.Builder()
            .url("https://rr3---sn-4g5ednss.googlevideo.com/videoplayback?expire=12345678&id=test1234567")
            .build()

        client.newCall(req).execute().use { resp ->
            assertEquals(200, resp.code)
            assertEquals("POST", interceptedMethod)
            assertNotNull(interceptedRn)
            assertEquals("https://www.youtube.com", interceptedOrigin)
            assertEquals(DESKTOP_UA, interceptedUa)
        }
    }

    @Test
    fun `range request returns 206 Partial Content`() {
        val client = OkHttpClient.Builder()
            .addInterceptor(YouTubeStreamInterceptor())
            .addInterceptor { chain ->
                val r = chain.request()
                Response.Builder()
                    .request(r)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Length", "1000")
                    .body("x".repeat(1000).toResponseBody())
                    .build()
            }
            .build()

        val req = Request.Builder()
            .url("https://rr1---sn-oxun-4g5e.googlevideo.com/videoplayback?expire=12345678&clen=5000000")
            .header("Range", "bytes=1000-1999")
            .build()

        client.newCall(req).execute().use { resp ->
            assertEquals(206, resp.code)
            assertEquals("bytes 1000-1999/5000000", resp.header("Content-Range"))
        }
    }
}
