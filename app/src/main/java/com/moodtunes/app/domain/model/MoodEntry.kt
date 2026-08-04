package com.moodtunes.app.domain.model

/**
 * Represents a snapshot of the user's mood at a specific point in time.
 * Stored in the Room database for analytics.
 */
data class MoodEntry(
    val id: Long = 0,
    val moodType: MoodType,
    val timestamp: Long,          // epoch milliseconds
    val songCount: Int,
    val durationListenedMs: Long  // total playback time during this session
) {
    /** Formatted date string for display */
    val formattedDate: String
        get() {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
            return "%02d/%02d/%04d".format(
                cal.get(java.util.Calendar.DAY_OF_MONTH),
                cal.get(java.util.Calendar.MONTH) + 1,
                cal.get(java.util.Calendar.YEAR)
            )
        }

    /** Formatted duration for display */
    val formattedDuration: String
        get() {
            val totalMinutes = durationListenedMs / 60_000
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        }
}
