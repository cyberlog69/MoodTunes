package com.moodtunes.app.domain.model

import android.net.Uri

/**
 * Domain model representing a single audio track.
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,           // milliseconds
    val uri: Uri,
    val albumArtUri: Uri?,
    val genre: String? = null,
    val isFavorite: Boolean = false,
    val moodTags: List<MoodType> = emptyList()
) {
    /** Formatted duration as mm:ss */
    val formattedDuration: String
        get() {
            val totalSeconds = duration / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}
