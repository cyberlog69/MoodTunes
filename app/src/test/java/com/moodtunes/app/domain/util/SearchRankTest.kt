package com.moodtunes.app.domain.util

import android.net.Uri
import com.moodtunes.app.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchRankTest {

    private fun song(
        id: Long,
        title: String,
        artist: String = "Artist",
        duration: Long = 200_000,
        plays: Long? = null,
        videoType: String? = null
    ) = Song(
        id = id,
        title = title,
        artist = artist,
        album = "Album",
        duration = duration,
        uri = Uri.parse("https://music.youtube.com/watch?v=video$id"),
        albumArtUri = null,
        isStream = true,
        playCountLong = plays,
        videoType = videoType
    )

    @Test
    fun `official studio track outranks karaoke cover`() {
        val karaoke = song(
            id = 1,
            title = "Midnight City (Karaoke Version)",
            artist = "Karaoke Band",
            duration = 15 * 60_000,
            plays = 100
        )
        val studio = song(
            id = 2,
            title = "Midnight City",
            artist = "M83",
            duration = 243_000,
            plays = 250_000_000,
            videoType = "MUSIC_VIDEO_TYPE_ATV"
        )

        val ranked = SearchRank.rankSongs(listOf(karaoke, studio), "Midnight City")
        assertEquals(listOf(studio, karaoke), ranked)
    }

    @Test
    fun `blank query keeps provider order`() {
        val first = song(1, "A")
        val second = song(2, "B")
        assertEquals(listOf(first, second), SearchRank.rankSongs(listOf(first, second), ""))
    }

    @Test
    fun `exact artist match scores higher`() {
        val wrong = song(1, "Nightcall", artist = "Somebody Else")
        val right = song(2, "Nightcall", artist = "Kavinsky")

        val ranked = SearchRank.rankSongs(listOf(wrong, right), "Kavinsky Nightcall")
        assertEquals(right, ranked.first())
    }

    @Test
    fun `title ranking prefers close matches over long compilations`() {
        val ranked = SearchRank.rankTitles(
            listOf("Best of Bollywood 500 songs", "Bollywood", "Bollywood Night"),
            "Bollywood"
        )
        assertEquals("Bollywood", ranked.first())
    }
}
