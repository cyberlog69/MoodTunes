package com.moodtunes.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class SharedSong(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: String,
    val albumArtUri: String? = null,
    val genre: String? = null,
    val isFavorite: Boolean = false,
    val moodTags: List<SharedMoodType> = emptyList(),
    val audioFormat: SharedAudioFormat = SharedAudioFormat.MP3,
    val isStream: Boolean = false,
    val isPreview: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0
) {
    val formattedDuration: String
        get() {
            if (isStream && duration <= 0) return "Live Stream"
            val totalSeconds = duration / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val secStr = if (seconds < 10) "0$seconds" else "$seconds"
            return "$minutes:$secStr"
        }
}
