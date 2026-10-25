package com.moodtunes.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class SharedPlaylist(
    val id: Long = 0,
    val name: String,
    val moodType: SharedMoodType? = null,
    val songs: List<SharedSong> = emptyList(),
    val createdAt: Long = 0L
) {
    val songCount: Int get() = songs.size

    val totalDurationMs: Long get() = songs.sumOf { it.duration }

    val formattedDuration: String
        get() {
            val totalMinutes = totalDurationMs / 60_000
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        }
}
