package com.moodtunes.app.data.remote.stream

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerClientOrderingTest {

    private fun profile(name: String, requiresAuth: Boolean = false) =
        YouTubePlayerClient.ClientProfile(
            name = name,
            clientName = name,
            version = "1.0",
            numericId = "0",
            userAgent = "test",
            requiresAuth = requiresAuth
        )

    @Test
    fun `signed out hides cookie only clients`() {
        val profiles = listOf(
            profile("VISIONOS"),
            profile("WEB_REMIX", requiresAuth = true)
        )

        val ordered = orderClientProfiles(profiles, signedIn = false, lastGood = null, cooling = emptySet())

        assertEquals(listOf("VISIONOS"), ordered.map { it.name })
    }

    @Test
    fun `signed in includes cookie only clients`() {
        val profiles = listOf(
            profile("VISIONOS"),
            profile("WEB_REMIX", requiresAuth = true)
        )

        val ordered = orderClientProfiles(profiles, signedIn = true, lastGood = null, cooling = emptySet())

        assertEquals(listOf("WEB_REMIX", "VISIONOS"), ordered.map { it.name })
    }

    @Test
    fun `signed in prefers web remix over a last good anonymous client`() {
        val profiles = listOf(
            profile("VISIONOS"),
            profile("WEB_REMIX", requiresAuth = true)
        )

        val ordered = orderClientProfiles(
            profiles,
            signedIn = true,
            lastGood = "VISIONOS",
            cooling = emptySet()
        )

        assertEquals(listOf("WEB_REMIX", "VISIONOS"), ordered.map { it.name })
    }

    @Test
    fun `last good client is tried first`() {
        val profiles = listOf(profile("VISIONOS"), profile("ANDROID_VR"), profile("IOS"))

        val ordered = orderClientProfiles(
            profiles,
            signedIn = false,
            lastGood = "ANDROID_VR",
            cooling = emptySet()
        )

        assertEquals(listOf("ANDROID_VR", "VISIONOS", "IOS"), ordered.map { it.name })
    }

    @Test
    fun `cooling clients are skipped`() {
        val profiles = listOf(profile("VISIONOS"), profile("ANDROID_VR"), profile("IOS"))

        val ordered = orderClientProfiles(
            profiles,
            signedIn = false,
            lastGood = null,
            cooling = setOf("VISIONOS")
        )

        assertEquals(listOf("ANDROID_VR", "IOS"), ordered.map { it.name })
    }

    @Test
    fun `if every client is cooling the full list is used`() {
        val profiles = listOf(profile("VISIONOS"), profile("ANDROID_VR"))

        val ordered = orderClientProfiles(
            profiles,
            signedIn = false,
            lastGood = null,
            cooling = setOf("VISIONOS", "ANDROID_VR")
        )

        assertEquals(listOf("VISIONOS", "ANDROID_VR"), ordered.map { it.name })
    }
}
