package com.moodtunes.app.data.local.lyrics

import android.content.Context
import android.provider.MediaStore
import com.moodtunes.app.domain.model.LyricsLine
import com.moodtunes.app.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves synced lyrics for a local [Song].
 *
 * Tries, in order:
 * 1. The `LYRICS` column exposed by MediaStore (embedded in the audio tag).
 * 2. A sibling `.lrc` file next to the audio file on disk.
 *
 * Results are cached per song id for the process lifetime.
 */
@Singleton
class LyricsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cache = mutableMapOf<Long, List<LyricsLine>>()

    suspend fun getLyrics(song: Song): List<LyricsLine> = withContext(Dispatchers.IO) {
        if (song.isStream) return@withContext emptyList()
        cache[song.id]?.let { return@withContext it }

        val lyrics = readFromMediaStore(song) ?: readFromSiblingFile(song) ?: emptyList()
        if (lyrics.isNotEmpty()) cache[song.id] = lyrics
        lyrics
    }

    private fun readFromMediaStore(song: Song): List<LyricsLine>? = runCatching {
        context.contentResolver.query(
            song.uri,
            arrayOf("lyrics"),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val text = cursor.getString(0)
                if (!text.isNullOrBlank()) LrcParser.parse(text) else null
            } else {
                null
            }
        }
    }.getOrNull()

    private fun readFromSiblingFile(song: Song): List<LyricsLine>? = runCatching {
        val path = context.contentResolver.query(
            song.uri,
            arrayOf(MediaStore.Audio.Media.DATA),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: return null

        val audioFile = File(path)
        val baseName = audioFile.absolutePath.substringBeforeLast('.')
        val lrcFile = listOf(File("$baseName.lrc"), File("$baseName.LRC"))
            .firstOrNull { it.exists() && it.isFile } ?: return null

        val parsed = LrcParser.parse(lrcFile.readText())
        if (parsed.isNotEmpty()) parsed else null
    }.getOrNull()
}
