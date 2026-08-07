package com.moodtunes.app.data.local.lyrics

import com.moodtunes.app.domain.model.LyricsLine

/**
 * Parses standard LRC (lyrics with timestamps) text into a sorted list of
 * [LyricsLine] entries.
 *
 * Supported format: `[mm:ss.xx]Lyric text`, where the fraction can be 1–3 digits
 * (`.x`, `.xx` or `.xxx`). Multiple timestamps per line and metadata tags
 * (`[ti:]`, `[ar:]`, `[al:]`, `[by:]`, `[offset:]`) are handled.
 */
object LrcParser {

    private val timestampRegex = Regex("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    private val offsetRegex = Regex("\\[offset:([+-]?\\d+)](.*)")

    fun parse(lrcText: String): List<LyricsLine> {
        val result = mutableListOf<LyricsLine>()
        var offsetMs = 0L

        lrcText.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach

            val offsetMatch = offsetRegex.find(line)
            if (offsetMatch != null && !timestampRegex.containsMatchIn(line)) {
                offsetMs = offsetMatch.groupValues[1].toLongOrNull() ?: 0L
                return@forEach
            }

            val times = timestampRegex.findAll(line)
                .map { match ->
                    val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                    val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                    val millis = when (match.groupValues[3].length) {
                        3 -> match.groupValues[3].toLongOrNull() ?: 0L
                        2 -> (match.groupValues[3].toLongOrNull() ?: 0L) * 10L
                        1 -> (match.groupValues[3].toLongOrNull() ?: 0L) * 100L
                        else -> 0L
                    }
                    minutes * 60_000L + seconds * 1_000L + millis + offsetMs
                }
                .toList()

            if (times.isNotEmpty()) {
                val text = line.replace(timestampRegex, "").trim()
                times.forEach { timeMs -> result.add(LyricsLine(timeMs, text)) }
            }
        }

        return result.sortedBy { it.timeMs }
    }
}
