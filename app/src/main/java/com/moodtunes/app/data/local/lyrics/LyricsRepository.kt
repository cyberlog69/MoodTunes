package com.moodtunes.app.data.local.lyrics

import android.content.Context
import android.provider.MediaStore
import com.moodtunes.app.data.remote.api.LrclibService
import com.moodtunes.app.data.remote.api.LyricsTranslationService
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
    private val lrclibService: LrclibService,
    private val translationService: LyricsTranslationService
) {
    private val cache = mutableMapOf<Long, List<LyricsLine>>()
    private val lyricsCacheDir = File(context.filesDir, "lyrics_cache").apply {
        if (!exists()) mkdirs()
    }

    suspend fun getLyrics(song: Song): List<LyricsLine> = withContext(Dispatchers.IO) {
        cache[song.id]?.let { return@withContext it }

        // 1. Check persistent offline disk cache first
        readFromDiskCache(song.id)?.let { diskLyrics ->
            cache[song.id] = diskLyrics
            return@withContext diskLyrics
        }

        // 2. Resolve via local files or remote LRCLIB
        val lyrics = if (song.isStream) {
            fetchFromLrclib(song)
        } else {
            readFromMediaStore(song) ?: readFromSiblingFile(song) ?: fetchFromLrclib(song)
        } ?: emptyList()

        if (lyrics.isNotEmpty()) cache[song.id] = lyrics
        lyrics
    }

    /**
     * Proactively fetches and caches lyrics to disk (e.g. when downloading a track offline).
     */
    suspend fun cacheLyricsForSong(song: Song): List<LyricsLine> = withContext(Dispatchers.IO) {
        val existing = readFromDiskCache(song.id)
        if (existing != null && existing.isNotEmpty()) {
            cache[song.id] = existing
            return@withContext existing
        }
        val fetched = fetchFromLrclib(song) ?: emptyList()
        if (fetched.isNotEmpty()) {
            cache[song.id] = fetched
        }
        fetched
    }

    /**
     * Saves user-supplied or edited LRC content directly to disk cache.
     */
    suspend fun saveCustomLyrics(songId: Long, rawLrc: String): List<LyricsLine> = withContext(Dispatchers.IO) {
        val parsed = LrcParser.parse(rawLrc)
        if (parsed.isNotEmpty()) {
            saveToDiskCache(songId, rawLrc)
            cache[songId] = parsed
        }
        parsed
    }

    /**
     * Manually searches LRCLIB using custom user-provided title/artist queries
     * and persists the matched lyrics to disk cache.
     */
    suspend fun searchAndSetLyrics(song: Song, queryTitle: String, queryArtist: String): List<LyricsLine>? = withContext(Dispatchers.IO) {
        return@withContext try {
            val durationSec = if (song.duration > 0) (song.duration / 1000).toInt() else null
            val result = lrclibService.getLyrics(queryTitle, queryArtist, durationSec)
            if (result != null) {
                val raw = result.syncedLyrics ?: result.plainLyrics ?: ""
                val parsed = when {
                    !result.syncedLyrics.isNullOrBlank() -> LrcParser.parse(result.syncedLyrics)
                    !result.plainLyrics.isNullOrBlank() -> {
                        result.plainLyrics.lines()
                            .filter { it.isNotBlank() }
                            .mapIndexed { index, line ->
                                LyricsLine(timeMs = index * 4000L, text = line.trim())
                            }
                    }
                    else -> null
                }
                if (parsed != null && parsed.isNotEmpty()) {
                    saveToDiskCache(song.id, raw)
                    cache[song.id] = parsed
                    parsed
                } else null
            } else null
        } catch (e: Exception) {
            Timber.w(e, "Manual lyrics search failed for query: $queryTitle - $queryArtist")
            null
        }
    }

    suspend fun translateLyrics(songId: Long, lyrics: List<LyricsLine>): List<LyricsLine> = withContext(Dispatchers.IO) {
        if (lyrics.isEmpty()) return@withContext lyrics
        // If already translated, return cached version
        if (lyrics.any { it.translation != null }) return@withContext lyrics

        val texts = lyrics.map { it.text }
        val translations = translationService.translateLines(texts)
        val translatedLyrics = lyrics.mapIndexed { index, line ->
            line.copy(translation = translations[index])
        }
        cache[songId] = translatedLyrics
        translatedLyrics
    }

    private fun readFromDiskCache(songId: Long): List<LyricsLine>? = runCatching {
        val file = File(lyricsCacheDir, "$songId.lrc")
        if (file.exists() && file.isFile) {
            val text = file.readText()
            if (text.isNotBlank()) LrcParser.parse(text) else null
        } else null
    }.getOrNull()

    private fun saveToDiskCache(songId: Long, rawLrc: String) = runCatching {
        if (rawLrc.isNotBlank()) {
            val file = File(lyricsCacheDir, "$songId.lrc")
            file.writeText(rawLrc)
        }
    }

    private suspend fun fetchFromLrclib(song: Song): List<LyricsLine>? {
        return try {
            val durationSec = if (song.duration > 0) (song.duration / 1000).toInt() else null
            val result = lrclibService.getLyrics(song.title, song.artist, durationSec)
            if (result != null) {
                // Prefer synced lyrics, fall back to plain
                val syncedText = result.syncedLyrics
                val plainText = result.plainLyrics
                val raw = syncedText ?: plainText ?: ""
                val parsed = when {
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
                if (parsed != null && parsed.isNotEmpty()) {
                    saveToDiskCache(song.id, raw)
                }
                parsed
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
                if (!text.isNullOrBlank()) {
                    saveToDiskCache(song.id, text)
                    LrcParser.parse(text)
                } else null
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

        val raw = lrcFile.readText()
        val parsed = LrcParser.parse(raw)
        if (parsed.isNotEmpty()) {
            saveToDiskCache(song.id, raw)
            parsed
        } else null
    }.getOrNull()
}
