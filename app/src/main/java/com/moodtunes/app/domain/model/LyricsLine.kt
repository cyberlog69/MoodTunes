package com.moodtunes.app.domain.model

/**
 * A single synced lyric line parsed from an .lrc file.
 * @param timeMs time (in milliseconds) from track start when the line is sung
 * @param text   the lyric text (may be empty for instrumental time markers)
 */
data class LyricsLine(
    val timeMs: Long,
    val text: String
)
