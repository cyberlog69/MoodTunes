package com.moodtunes.app.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateCheckerVersionTest {

    private val checker = UpdateChecker(RuntimeEnvironment.getApplication())

    @Test
    fun `same version is not newer`() {
        assertFalse(checker.isVersionNewer("3.0.1", "3.0.1"))
        assertFalse(checker.isVersionNewer("3.0.1", "v3.0.1"))
        assertFalse(checker.isVersionNewer("v3.0.1", "3.0.1"))
    }

    @Test
    fun `higher version is newer`() {
        assertTrue(checker.isVersionNewer("3.0.0", "3.0.1"))
        assertTrue(checker.isVersionNewer("3.0.1", "3.1.0"))
        assertTrue(checker.isVersionNewer("3.9.9", "4.0.0"))
    }

    @Test
    fun `lower version is not newer`() {
        assertFalse(checker.isVersionNewer("3.0.1", "3.0.0"))
        assertFalse(checker.isVersionNewer("4.0.0", "3.9.9"))
    }

    @Test
    fun `numeric segments compare as numbers`() {
        assertTrue(checker.isVersionNewer("3.0.9", "3.0.10"))
        assertFalse(checker.isVersionNewer("3.0.10", "3.0.9"))
    }

    @Test
    fun `missing segments count as zero`() {
        assertFalse(checker.isVersionNewer("3.1", "3.1.0"))
        assertTrue(checker.isVersionNewer("3.1", "3.1.1"))
    }

    @Test
    fun `build suffixes do not create false updates`() {
        assertFalse(checker.isVersionNewer("3.0.1-beta", "3.0.1"))
        assertFalse(checker.isVersionNewer("3.0.1", "3.0.1+42"))
    }
}
