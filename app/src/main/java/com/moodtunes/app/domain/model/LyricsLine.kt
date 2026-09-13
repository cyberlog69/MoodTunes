package com.moodtunes.app.domain.model

/**
 * A single word or syllable with precise start and end timestamp in milliseconds.
 */
data class WordTimestamp(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

/**
 * A single synced lyric line parsed from an .lrc, enhanced LRC, or TTML payload.
 * @param timeMs time (in milliseconds) from track start when the line is sung
 * @param text   the lyric text (may be empty for instrumental time markers)
 * @param endTimeMs optional line ending timestamp
 * @param words  individual word-level timestamps for karaoke-style word-by-word highlighting
 * @param translation optional translated text in user's target language
 */
data class LyricsLine(
    val timeMs: Long,
    val text: String,
    val endTimeMs: Long = 0L,
    val words: List<WordTimestamp> = emptyList(),
    val translation: String? = null
)

