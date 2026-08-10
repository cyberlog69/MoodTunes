package com.moodtunes.app.data.local.lyrics

import android.content.Context
import android.provider.MediaStore
import com.moodtunes.app.data.remote.api.LrclibService
import com.moodtunes.app.domain.model.LyricsLine
import com.moodtunes.app.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lrclibService: LrclibService
) {
    private val cache = mutableMapOf<Long, List<LyricsLine>>()

    suspend fun getLyrics(song: Song): List<LyricsLine> = withContext(Dispatchers.IO) {
        cache[song.id]?.let { return@withContext it }

        val lyrics = if (song.isStream) {
            fetchFromLrclib(song)
        } else {
            readFromMediaStore(song) ?: readFromSiblingFile(song) ?: fetchFromLrclib(song)
        } ?: emptyList()

        if (lyrics.isNotEmpty()) cache[song.id] = lyrics
        lyrics
    }

    private suspend fun fetchFromLrclib(song: Song): List<LyricsLine>? {
        return try {
            val durationSec = if (song.duration > 0) (song.duration / 1000).toInt() else null
            val result = lrclibService.getLyrics(song.title, song.artist, durationSec)
            if (result != null) {
                // Prefer synced lyrics, fall back to plain
                val syncedText = result.syncedLyrics
                val plainText = result.plainLyrics
                when {
                    !syncedText.isNullOrBlank() -> LrcParser.parse(syncedText)
                    !plainText.isNullOrBlank() -> {
                        // Convert plain text to LyricsLine with 0ms timestamps
                        plainText.lines()
                            .filter { it.isNotBlank() }
                            .mapIndexed { index, line ->
                                LyricsLine(timeMs = index * 4000L, text = line.trim())
                            }
                    }
                    else -> null
                }
            } else null
        } catch (e: Exception) {
            Timber.w(e, "LRCLIB lyrics fetch failed for: ${song.title}")
            null
        }
    }

    // Keep existing readFromMediaStore and readFromSiblingFile methods unchanged
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
