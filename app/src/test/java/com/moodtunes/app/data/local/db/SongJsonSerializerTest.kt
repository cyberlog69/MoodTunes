package com.moodtunes.app.data.local.db

import android.net.Uri
import com.moodtunes.app.domain.model.AudioFormat
import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SongJsonSerializerTest {

    private fun song(
        id: Long,
        genre: String? = "Indie",
        moodTags: List<MoodType> = listOf(MoodType.HAPPY),
        audioFormat: AudioFormat = AudioFormat.FLAC
    ) = Song(
        id = id,
        title = "Title $id",
        artist = "Artist",
        album = "Album",
        duration = 240_000,
        uri = Uri.parse("content://test/$id"),
        albumArtUri = Uri.parse("content://art/$id"),
        genre = genre,
        isFavorite = true,
        moodTags = moodTags,
        audioFormat = audioFormat,
        isStream = true,
        playCount = 7,
        lastPlayedAt = 1234L
    )

    @Test
    fun `round trip preserves all fields`() {
        val original = song(1)
        val json = SongJsonSerializer.serialize(original)
        val restored = SongJsonSerializer.deserialize(json)

        assertNotNull(restored)
        restored!!.run {
            assertEquals(original.id, id)
            assertEquals(original.title, title)
            assertEquals(original.artist, artist)
            assertEquals(original.album, album)
            assertEquals(original.duration, duration)
            assertEquals(original.genre, genre)
            assertEquals(original.isFavorite, isFavorite)
            assertEquals(original.moodTags, moodTags)
            assertEquals(original.audioFormat, audioFormat)
            assertEquals(original.isStream, isStream)
            assertEquals(original.playCount, playCount)
            assertEquals(original.lastPlayedAt, lastPlayedAt)
        }
    }

    @Test
    fun `deserialize handles missing optional fields`() {
        val json = """{"id":9,"title":"T","artist":"A","album":"B","duration":0,"uri":"content://x"}"""
        val restored = SongJsonSerializer.deserialize(json)

        assertNotNull(restored)
        restored!!.run {
            assertEquals(9L, id)
            assertNull(albumArtUri)
            assertNull(genre)
            assertEquals(false, isFavorite)
            assertEquals(AudioFormat.MP3, audioFormat)
            assertTrue(moodTags.isEmpty())
            assertEquals(false, isStream)
        }
    }

    @Test
    fun `deserialize returns null for invalid json`() {
        assertNull(SongJsonSerializer.deserialize("not json at all"))
    }

    @Test
    fun `deserialize returns null for empty json`() {
        assertNull(SongJsonSerializer.deserialize(""))
    }

    @Test
    fun `round trip with null genre and empty mood tags`() {
        val original = song(2, genre = null, moodTags = emptyList(), audioFormat = AudioFormat.STREAM)
        val restored = SongJsonSerializer.deserialize(SongJsonSerializer.serialize(original))

        assertNotNull(restored)
        assertEquals(original.genre, restored!!.genre)
        assertTrue(restored.moodTags.isEmpty())
        assertEquals(AudioFormat.STREAM, restored.audioFormat)
    }
}
