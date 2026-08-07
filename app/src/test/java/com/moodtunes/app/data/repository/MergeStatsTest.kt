package com.moodtunes.app.data.repository

import android.net.Uri
import com.moodtunes.app.data.local.db.entity.SongEntity
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
class MergeStatsTest {

    private fun song(
        id: Long,
        isFavorite: Boolean = false,
        playCount: Int = 0,
        lastPlayedAt: Long = 0
    ) = Song(
        id = id,
        title = "Title $id",
        artist = "Artist",
        album = "Album",
        duration = 180_000,
        uri = Uri.parse("content://test/$id"),
        albumArtUri = null,
        isFavorite = isFavorite,
        playCount = playCount,
        lastPlayedAt = lastPlayedAt
    )

    private fun entity(
        id: Long,
        isFavorite: Boolean = false,
        playCount: Int = 0,
        lastPlayedAt: Long = 0
    ) = SongEntity(
        id = id,
        title = "Title $id",
        artist = "Artist",
        album = "Album",
        duration = 180_000,
        uriString = "content://test/$id",
        albumArtUriString = null,
        genre = null,
        isFavorite = isFavorite,
        playCount = playCount,
        lastPlayedAt = lastPlayedAt
    )

    @Test
    fun `overlays favorite status from db rows`() {
        val scanned = song(1)
        val merged = mergeStats(listOf(scanned), listOf(entity(1, isFavorite = true)))

        assertTrue(merged.single().isFavorite)
    }

    @Test
    fun `overlays play count and recency from db rows`() {
        val scanned = song(1)
        val merged = mergeStats(listOf(scanned), listOf(entity(1, playCount = 42, lastPlayedAt = 1234L)))

        assertEquals(42, merged.single().playCount)
        assertEquals(1234L, merged.single().lastPlayedAt)
    }

    @Test
    fun `leaves new songs untouched`() {
        val scanned = song(1)
        val merged = mergeStats(listOf(scanned), emptyList())

        assertEquals(scanned, merged.single())
    }

    @Test
    fun `preserves order of input`() {
        val scanned = listOf(song(3), song(1), song(2))
        val rows = listOf(entity(3, isFavorite = true), entity(2, isFavorite = true))

        val merged = mergeStats(scanned, rows)

        assertEquals(listOf(3L, 1L, 2L), merged.map { it.id })
        assertTrue(merged[0].isFavorite)
        assertFalse(merged[1].isFavorite)
        assertTrue(merged[2].isFavorite)
    }

    @Test
    fun `empty input returns empty`() {
        assertTrue(mergeStats(emptyList(), listOf(entity(1))).isEmpty())
    }

    @Test
    fun `unmatched db rows are ignored`() {
        val scanned = listOf(song(1))
        val merged = mergeStats(scanned, listOf(entity(999, isFavorite = true)))

        assertEquals(scanned, merged)
    }
}
