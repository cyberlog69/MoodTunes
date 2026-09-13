package com.moodtunes.app.data.local.lyrics

import com.moodtunes.app.domain.model.LyricsLine
import com.moodtunes.app.domain.model.WordTimestamp

/**
 * Parses standard LRC, enhanced LRC (with inline word timestamps), and TTML
 * (Timed Text Markup Language) into a sorted list of [LyricsLine] entries
 * with word-by-word timing support.
 */
object LrcParser {

    private val timestampRegex = Regex("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    private val offsetRegex = Regex("\\[offset:([+-]?\\d+)](.*)")
    private val wordTagRegex = Regex("(?:<|\\()(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?(?:>|\\))([^<(\n]*)")

    // TTML parsing regexes
    private val pTagRegex = Regex("<p\\s+[^>]*begin=\"([^\"]+)\"(?:\\s+[^>]*end=\"([^\"]+)\")?[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
    private val spanTagRegex = Regex("<span\\s+[^>]*begin=\"([^\"]+)\"(?:\\s+[^>]*end=\"([^\"]+)\")?[^>]*>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)

    fun parse(lrcText: String): List<LyricsLine> {
        val trimmed = lrcText.trim()
        if (trimmed.isEmpty()) return emptyList()

        return if (trimmed.contains("<tt") || trimmed.contains("<p ") || trimmed.contains("<p>")) {
            parseTtml(trimmed)
        } else {
            parseLrc(trimmed)
        }
    }

    private fun parseLrc(lrcText: String): List<LyricsLine> {
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
                    val millis = parseFraction(match.groupValues[3])
                    minutes * 60_000L + seconds * 1_000L + millis + offsetMs
                }
                .toList()

            if (times.isNotEmpty()) {
                val lineWithoutTimestamps = line.replace(timestampRegex, "").trim()

                // Check if the line has enhanced word timestamps: <mm:ss.xx>Word
                val wordMatches = wordTagRegex.findAll(lineWithoutTimestamps).toList()
                if (wordMatches.isNotEmpty()) {
                    val words = mutableListOf<WordTimestamp>()
                    for (i in wordMatches.indices) {
                        val match = wordMatches[i]
                        val min = match.groupValues[1].toLongOrNull() ?: 0L
                        val sec = match.groupValues[2].toLongOrNull() ?: 0L
                        val ms = parseFraction(match.groupValues[3])
                        val startMs = min * 60_000L + sec * 1_000L + ms + offsetMs
                        val wordText = match.groupValues[4]

                        // End time defaults to next word's start or start + 500ms
                        val nextStartMs = wordMatches.getOrNull(i + 1)?.let { nextMatch ->
                            val nMin = nextMatch.groupValues[1].toLongOrNull() ?: 0L
                            val nSec = nextMatch.groupValues[2].toLongOrNull() ?: 0L
                            val nMs = parseFraction(nextMatch.groupValues[3])
                            nMin * 60_000L + nSec * 1_000L + nMs + offsetMs
                        }
                        val endMs = nextStartMs ?: (startMs + 500L)

                        if (wordText.isNotEmpty()) {
                            words.add(WordTimestamp(startMs, endMs, wordText))
                        }
                    }

                    val cleanText = words.joinToString("") { it.text }.trim().ifEmpty {
                        lineWithoutTimestamps.replace(Regex("<[^>]*>|\\([^)]*\\)"), "").trim()
                    }

                    times.forEach { timeMs ->
                        result.add(LyricsLine(timeMs = timeMs, text = cleanText, words = words))
                    }
                } else {
                    val cleanText = lineWithoutTimestamps.trim()
                    times.forEach { timeMs ->
                        result.add(LyricsLine(timeMs = timeMs, text = cleanText))
                    }
                }
            }
        }

        val sorted = result.sortedBy { it.timeMs }
        return populateEndTimes(sorted)
    }

    private fun parseTtml(ttmlText: String): List<LyricsLine> {
        val result = mutableListOf<LyricsLine>()
        pTagRegex.findAll(ttmlText).forEach { pMatch ->
            val beginStr = pMatch.groupValues[1]
            val endStr = pMatch.groupValues[2]
            val content = pMatch.groupValues[3]

            val lineBeginMs = parseTtmlTimestamp(beginStr) ?: return@forEach
            val lineEndMs = parseTtmlTimestamp(endStr) ?: (lineBeginMs + 3500L)

            val spanMatches = spanTagRegex.findAll(content).toList()
            val words = mutableListOf<WordTimestamp>()

            if (spanMatches.isNotEmpty()) {
                spanMatches.forEach { sMatch ->
                    val sBeginStr = sMatch.groupValues[1]
                    val sEndStr = sMatch.groupValues[2]
                    val sText = sMatch.groupValues[3].replace(Regex("<[^>]*>"), "")

                    val wStart = parseTtmlTimestamp(sBeginStr) ?: lineBeginMs
                    val wEnd = parseTtmlTimestamp(sEndStr) ?: (wStart + 350L)
                    if (sText.isNotEmpty()) {
                        words.add(WordTimestamp(wStart, wEnd, sText))
                    }
                }
            }

            val cleanText = if (words.isNotEmpty()) {
                words.joinToString("") { it.text }.trim()
            } else {
                content.replace(Regex("<[^>]*>"), "").trim()
            }

            if (cleanText.isNotBlank()) {
                result.add(
                    LyricsLine(
                        timeMs = lineBeginMs,
                        text = cleanText,
                        endTimeMs = lineEndMs,
                        words = words
                    )
                )
            }
        }

        val sorted = result.sortedBy { it.timeMs }
        return populateEndTimes(sorted)
    }

    private fun populateEndTimes(lines: List<LyricsLine>): List<LyricsLine> {
        return lines.mapIndexed { index, line ->
            if (line.endTimeMs > line.timeMs) {
                line
            } else {
                val nextStart = lines.getOrNull(index + 1)?.timeMs
                val computedEnd = nextStart ?: (line.timeMs + 4000L)
                line.copy(endTimeMs = computedEnd)
            }
        }
    }

    private fun parseFraction(fractionStr: String): Long {
        return when (fractionStr.length) {
            3 -> fractionStr.toLongOrNull() ?: 0L
            2 -> (fractionStr.toLongOrNull() ?: 0L) * 10L
            1 -> (fractionStr.toLongOrNull() ?: 0L) * 100L
            else -> 0L
        }
    }

    private fun parseTtmlTimestamp(ts: String?): Long? {
        if (ts.isNullOrBlank()) return null
        val clean = ts.trim().removeSuffix("s")
        val parts = clean.split(":")
        return try {
            when (parts.size) {
                3 -> {
                    val h = parts[0].toLong()
                    val m = parts[1].toLong()
                    val s = parts[2].toDouble()
                    (h * 3600_000L + m * 60_000L + (s * 1000).toLong())
                }
                2 -> {
                    val m = parts[0].toLong()
                    val s = parts[1].toDouble()
                    (m * 60_000L + (s * 1000).toLong())
                }
                1 -> {
                    (parts[0].toDouble() * 1000).toLong()
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}

