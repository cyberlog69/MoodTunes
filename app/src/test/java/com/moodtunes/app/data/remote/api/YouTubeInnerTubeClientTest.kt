package com.moodtunes.app.data.remote.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YouTubeInnerTubeClientTest {

    private lateinit var server: MockWebServer
    private lateinit var sessionStore: YouTubeSessionStore
    private lateinit var client: YouTubeInnerTubeClient

    private val homeJson = """
        {
          "header": {
            "musicHeaderRenderer": {
              "title": { "runs": [{ "text": "Home" }] }
            }
          },
          "contents": {
            "singleColumnBrowseResultsRenderer": {
              "tabs": [{
                "tabRenderer": {
                  "content": { "sectionListRenderer": { "contents": [] } }
                }
              }]
            }
          }
        }
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        sessionStore = YouTubeSessionStore(RuntimeEnvironment.getApplication())
        sessionStore.visitorData = "visitor-1"

        client = YouTubeInnerTubeClient(OkHttpClient(), sessionStore).apply {
            baseUrlOverride = server.url("/youtubei/v1").toString().trimEnd('/')
            webOriginOverride = server.url("/").toString().trimEnd('/')
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `browse posts to the correct endpoint with client headers`() = runBlocking {
        server.enqueue(MockResponse().setBody(homeJson))

        val page = client.home()

        assertEquals("Home", page.title)
        assertEquals(1, server.requestCount)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/youtubei/v1/browse?prettyPrint=false", request.path)
        assertEquals("67", request.getHeader("X-YouTube-Client-Name"))
        assertEquals("1.20260928.13.00", request.getHeader("X-YouTube-Client-Version"))
        assertEquals("visitor-1", request.getHeader("X-Goog-Visitor-Id"))

        val body = JSONObject(request.body.readUtf8())
        assertEquals("FEmusic_home", body.getString("browseId"))
        val clientNode = body.getJSONObject("context").getJSONObject("client")
        assertEquals("WEB_REMIX", clientNode.getString("clientName"))
        assertEquals("visitor-1", clientNode.getString("visitorData"))
    }

    @Test
    fun `refreshes stale visitorData and retries once on rejection`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("{}"))
        server.enqueue(MockResponse().setBody("""{"visitorData":"fresh-visitor"}"""))
        server.enqueue(MockResponse().setBody(homeJson))

        val page = client.home()

        assertEquals("Home", page.title)
        assertEquals(3, server.requestCount)

        val rejected = server.takeRequest()
        assertEquals("/youtubei/v1/browse?prettyPrint=false", rejected.path)
        assertEquals("visitor-1", rejected.getHeader("X-Goog-Visitor-Id"))

        val refresh = server.takeRequest()
        assertEquals("/", refresh.path)

        val retried = server.takeRequest()
        assertEquals("/youtubei/v1/browse?prettyPrint=false", retried.path)
        assertEquals("fresh-visitor", retried.getHeader("X-Goog-Visitor-Id"))
        assertEquals("fresh-visitor", sessionStore.visitorData)

        // The retry must not send the stale visitor id inside the JSON body.
        val body = JSONObject(retried.body.readUtf8())
        assertFalse(body.getJSONObject("context").getJSONObject("client").has("visitorData"))
    }

    @Test
    fun `failed requests return an empty page`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("{}"))

        val page = client.home()

        assertTrue(page.isEmpty)
        assertEquals("", page.title)
    }
}
