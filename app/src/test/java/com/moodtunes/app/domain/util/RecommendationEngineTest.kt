package com.moodtunes.app.domain.util

import android.net.Uri
import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecommendationEngineTest {

    private fun song(
        id: Long,
        artist: String = "Artist",
        album: String = "Album",
        genre: String? = null,
        moodTags: List<MoodType> = emptyList(),
        playCount: Int = 0,
        isFavorite: Boolean = false
    ) = Song(
        id = id,
        title = "Title $id",
        artist = artist,
        album = album,
        duration = 180_000,
        uri = Uri.parse("content://test/$id"),
        albumArtUri = null,
        genre = genre,
        isFavorite = isFavorite,
        moodTags = moodTags,
        playCount = playCount
    )

    @Test
    fun `cold start with no user history returns empty`() {
        val all = listOf(song(1), song(2), song(3))

        val result = RecommendationEngine.recommendForYou(
            allSongs = all,
            favoriteSongs = emptyList(),
            mostPlayed = emptyList(),
            topMood = null,
            limit = 20
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `empty library returns empty list`() {
        val result = RecommendationEngine.recommendForYou(
            allSongs = emptyList(),
            favoriteSongs = emptyList(),
            mostPlayed = emptyList(),
            topMood = null
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `seed songs are excluded from results`() {
        val seed = song(1, artist = "Liked Artist", isFavorite = true, playCount = 10)
        val candidate = song(2, artist = "Liked Artist")
        val other = song(3, artist = "Someone Else")

        val result = RecommendationEngine.recommendForYou(
            allSongs = listOf(seed, candidate, other),
            favoriteSongs = listOf(seed),
            mostPlayed = emptyList(),
            topMood = null
        )

        assertFalse(result.map { it.id }.contains(seed.id))
        assertTrue(result.contains(candidate))
    }

    @Test
    fun `artist match ranks ahead of unrelated song`() {
        val seed = song(1, artist = "Radiohead", isFavorite = true, playCount = 20)
        val sameArtist = song(2, artist = "Radiohead")
        val unrelated = song(3, artist = "Adele")

        val result = RecommendationEngine.recommendForYou(
            allSongs = listOf(seed, sameArtist, unrelated),
            favoriteSongs = listOf(seed),
            mostPlayed = emptyList(),
            topMood = null
        )

        assertEquals(2, result.size)
        assertEquals(sameArtist.id, result[0].id)
    }

    @Test
    fun `top mood match boosts candidate`() {
        val seed = song(1, artist = "Seeded", isFavorite = true)
        val moody = song(2, artist = "Other", moodTags = listOf(MoodType.CALM))
        val plain = song(3, artist = "Other")

        val result = RecommendationEngine.recommendForYou(
            allSongs = listOf(seed, moody, plain),
            favoriteSongs = listOf(seed),
            mostPlayed = emptyList(),
            topMood = MoodType.CALM
        )

        assertEquals(moody.id, result[0].id)
    }

    @Test
    fun `respects limit`() {
        val seed = song(1, artist = "Seeded", isFavorite = true, playCount = 50)
        val candidates = (2..10).map { song(it.toLong(), artist = "Seeded") }

        val result = RecommendationEngine.recommendForYou(
            allSongs = listOf(seed) + candidates,
            favoriteSongs = listOf(seed),
            mostPlayed = emptyList(),
            topMood = null,
            limit = 3
        )

        assertEquals(3, result.size)
    }
}
