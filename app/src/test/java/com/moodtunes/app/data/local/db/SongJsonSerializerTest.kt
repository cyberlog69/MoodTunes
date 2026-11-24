package com.moodtunes.app.data.local.db

import android.net.Uri
import com.moodtunes.app.domain.model.AudioFormat
import com.moodtunes.app.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        audioFormat = audioFormat,
        isStream = true,
        playCount = 7,
        lastPlayedAt = 1234L,
        videoId = "vid$id"
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
            assertEquals(original.audioFormat, audioFormat)
            assertEquals(original.isStream, isStream)
            assertEquals(original.playCount, playCount)
            assertEquals(original.lastPlayedAt, lastPlayedAt)
            assertEquals(original.videoId, videoId)
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
            assertEquals(false, isStream)
            assertNull(videoId)
        }
    }

    @Test
    fun `video id is inferred from a YouTube watch uri`() {
        val original = Song(
            id = 3,
            title = "T",
            artist = "A",
            album = "B",
            duration = 1000,
            uri = Uri.parse("https://music.youtube.com/watch?v=abc123"),
            albumArtUri = null,
            isStream = true
        )

        val restored = SongJsonSerializer.deserialize(SongJsonSerializer.serialize(original))

        assertNotNull(restored)
        assertEquals("abc123", restored!!.videoId)
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
    fun `round trip with null genre and stream format`() {
        val original = song(2, genre = null, audioFormat = AudioFormat.STREAM)
        val restored = SongJsonSerializer.deserialize(SongJsonSerializer.serialize(original))

        assertNotNull(restored)
        assertEquals(original.genre, restored!!.genre)
        assertEquals(AudioFormat.STREAM, restored.audioFormat)
    }
}
