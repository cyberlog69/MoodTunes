package com.moodtunes.app.domain.util

import android.net.Uri
import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MoodScoringTest {

    private fun song(
        id: Long,
        title: String = "Title",
        artist: String = "Artist",
        album: String = "Album",
        genre: String? = null,
        moodTags: List<MoodType> = emptyList()
    ) = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        duration = 180_000,
        uri = Uri.parse("content://test/$id"),
        albumArtUri = null,
        genre = genre,
        moodTags = moodTags
    )

    @Test
    fun `tagged song scores higher than untagged`() {
        val tagged = song(1, moodTags = listOf(MoodType.HAPPY))
        val untagged = song(2)

        assertTrue(MoodScoring.score(tagged, MoodType.HAPPY) > MoodScoring.score(untagged, MoodType.HAPPY))
    }

    @Test
    fun `keyword in title increases score`() {
        val base = song(1, title = "Random Title")
        val keyword = song(2, title = "Sunny Day Parade")
        val happy = MoodType.HAPPY

        assertTrue(MoodScoring.score(keyword, happy) > MoodScoring.score(base, happy))
    }

    @Test
    fun `keyword in genre increases score`() {
        val noGenre = song(1, genre = null)
        val withGenre = song(2, genre = "chillhop lofi")
        val calm = MoodType.CALM

        assertTrue(MoodScoring.score(withGenre, calm) > MoodScoring.score(noGenre, calm))
    }

    @Test
    fun `mood tag gives largest single boost`() {
        val tagged = song(1, moodTags = listOf(MoodType.SLEEP))
        val keywordOnly = song(2, title = "Dreamland")

        assertTrue(MoodScoring.score(tagged, MoodType.SLEEP) > MoodScoring.score(keywordOnly, MoodType.SLEEP))
    }

    @Test
    fun `score is zero when nothing matches`() {
        val s = song(1, title = "Zebra", artist = "Goose", album = "Pebble", genre = "math")
        assertEquals(0f, MoodScoring.score(s, MoodType.EUPHORIC), 0.0001f)
    }

    @Test
    fun `smartOrder returns a permutation of the input`() {
        val songs = listOf(
            song(1, title = "A"),
            song(2, title = "B"),
            song(3, title = "C"),
            song(4, title = "D")
        )
        val ordered = MoodScoring.smartOrder(songs, MoodType.CALM, Random(42))

        assertEquals(songs.size, ordered.size)
        assertEquals(songs.map { it.id }.toSet(), ordered.map { it.id }.toSet())
    }

    @Test
    fun `smartOrder keeps a heavily matched song away from the back`() {
        val favorite = song(1, title = "Some Random Thing", moodTags = listOf(MoodType.HAPPY))
        val others = listOf(
            song(2, title = "X"),
            song(3, title = "Y")
        )
        val ordered = MoodScoring.smartOrder(listOf(favorite) + others, MoodType.HAPPY, Random(7))

        // The tagged song carries ~4.1 of ~4.3 total weight, so being picked last
        // is essentially impossible for any seed.
        assertTrue("tagged song should not be picked last", ordered.last().id != 1L)
    }

    @Test
    fun `smartOrder returns input unchanged for single song`() {
        val single = listOf(song(1))
        assertEquals(single, MoodScoring.smartOrder(single, MoodType.SAD))
    }
}
