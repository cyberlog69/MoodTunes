package com.moodtunes.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class SharedWordTimestamp(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

@Serializable
data class SharedLyricsLine(
    val timeMs: Long,
    val text: String,
    val endTimeMs: Long = 0L,
    val words: List<SharedWordTimestamp> = emptyList(),
    val translation: String? = null
)
