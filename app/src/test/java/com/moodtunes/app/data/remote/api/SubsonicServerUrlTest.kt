package com.moodtunes.app.data.remote.api

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SubsonicServerUrlTest {

    private val service = SubsonicApiService()

    @Test
    fun `accepts https server urls`() {
        assertTrue(service.validateServerUrl("https://music.example.com").isSuccess)
        assertTrue(service.validateServerUrl("https://music.example.com:4533/navidrome/").isSuccess)
    }

    @Test
    fun `accepts loopback http for development`() {
        assertTrue(service.validateServerUrl("http://localhost:4533").isSuccess)
        assertTrue(service.validateServerUrl("http://10.0.2.2:4533").isSuccess)
    }

    @Test
    fun `rejects plaintext lan server urls`() {
        val result = service.validateServerUrl("http://192.168.1.10:4533")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Plain HTTP is blocked"))
    }

    @Test
    fun `rejects empty or schemeless urls`() {
        assertTrue(service.validateServerUrl("").isFailure)
        assertTrue(service.validateServerUrl("music.example.com").isFailure)
    }
}
