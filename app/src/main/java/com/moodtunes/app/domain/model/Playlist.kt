package com.moodtunes.app.domain.model

/**
 * Represents a user-created or mood-generated playlist.
 */
data class Playlist(
    val id: Long = 0,
    val name: String,
    val moodType: MoodType?,      // null = user-created
    val songs: List<Song> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
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
