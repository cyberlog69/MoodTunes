package com.moodtunes.app.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateEndpointPolicyTest {

    @Test
    fun `accepts github release urls`() {
        assertTrue(
            UpdateEndpointPolicy.isTrustedReleaseUrl(
                "https://github.com/cyberlog69/MoodTunes/releases/download/v1.9.2/MoodTunes-v1.9.2.apk"
            )
        )
        assertTrue(
            UpdateEndpointPolicy.isTrustedReleaseUrl(
                "https://objects.githubusercontent.com/github-production-release-asset/123/MoodTunes.apk"
            )
        )
    }

    @Test
    fun `rejects non-https urls`() {
        assertFalse(
            UpdateEndpointPolicy.isTrustedReleaseUrl(
                "http://github.com/cyberlog69/MoodTunes/releases/download/v1.9.2/app.apk"
            )
        )
    }

    @Test
    fun `rejects lookalike and foreign hosts`() {
        assertFalse(UpdateEndpointPolicy.isTrustedReleaseUrl("https://evil.example.com/app.apk"))
        assertFalse(UpdateEndpointPolicy.isTrustedReleaseUrl("https://github.com.evil.example/app.apk"))
        assertFalse(UpdateEndpointPolicy.isTrustedReleaseUrl("https://notgithub.com/app.apk"))
        assertFalse(UpdateEndpointPolicy.isTrustedReleaseUrl(null))
        assertFalse(UpdateEndpointPolicy.isTrustedReleaseUrl(""))
        assertFalse(UpdateEndpointPolicy.isTrustedReleaseUrl("not a url"))
    }

    @Test
    fun `rejects file and intent schemes`() {
        assertFalse(UpdateEndpointPolicy.isTrustedReleaseUrl("file:///sdcard/app.apk"))
        assertFalse(UpdateEndpointPolicy.isTrustedReleaseUrl("intent://github.com/#Intent;scheme=https;end"))
    }
}
