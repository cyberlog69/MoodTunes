package com.moodtunes.app.data.remote.stream

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
class PlayerClientHealthStoreTest {

    private lateinit var store: PlayerClientHealthStore

    @Before
    fun setUp() {
        store = PlayerClientHealthStore(RuntimeEnvironment.getApplication())
        store.clear()
    }

    @Test
    fun `failed client cools down for ten minutes`() {
        val now = 1_000_000L
        store.recordFailure("IOS", failedAt = now)

        assertTrue("IOS" in store.coolingClients(now))
        assertTrue("IOS" in store.coolingClients(now + 9 * 60_000L))
        assertFalse("IOS" in store.coolingClients(now + 11 * 60_000L))
    }

    @Test
    fun `success remembers the last good client and clears its failure`() {
        val now = 1_000_000L
        store.recordFailure("VISIONOS", failedAt = now)
        store.recordSuccess("VISIONOS")

        assertEquals("VISIONOS", store.lastGoodClient())
        assertFalse("VISIONOS" in store.coolingClients(now))
    }

    @Test
    fun `clear removes all health data`() {
        store.recordFailure("ANDROID_VR")
        store.recordSuccess("VISIONOS")

        store.clear()

        assertEquals(null, store.lastGoodClient())
        assertTrue(store.coolingClients().isEmpty())
    }
}
