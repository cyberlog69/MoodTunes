package com.moodtunes.app.data.remote.recommendation

import com.moodtunes.app.data.remote.api.YouTubeMusicApiService
import com.moodtunes.app.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

enum class DayTimeSlot(
    val title: String,
    val subtitle: String,
    val searchQueries: List<String>
) {
    MORNING(
        title = "Morning Focus & Awakening",
        subtitle = "Gentle acoustic melodies, fresh coffeehouse vibes & uplifting focus",
        searchQueries = listOf("Acoustic Morning Coffee Chill", "Gentle Pop Morning Wake Up")
    ),
    AFTERNOON(
        title = "Afternoon Flow & Energy",
        subtitle = "Lo-Fi productivity beats, rhythmic pop & deep work motivation",
        searchQueries = listOf("Lo-Fi Beats Deep Focus Flow", "Upbeat Energy Work Pop")
    ),
    EVENING_NIGHT(
        title = "Late Night Unwind & Chill",
        subtitle = "Mellow grooves, warm atmospheric melodies & peaceful relaxation",
        searchQueries = listOf("Late Night Drive Mellow Grooves", "Ambient Chill Relaxation")
    );

    companion object {
        fun currentSlot(): DayTimeSlot {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return when (hour) {
                in 5..11 -> MORNING
                in 12..17 -> AFTERNOON
                else -> EVENING_NIGHT
            }
        }
    }
}

data class TimeOfDayRecommendation(
    val slot: DayTimeSlot,
    val songs: List<Song>
)

@Singleton
class TimeOfDayRecommender @Inject constructor(
    private val youTubeMusicApiService: YouTubeMusicApiService
) {
    suspend fun getRecommendations(limit: Int = 10): TimeOfDayRecommendation = withContext(Dispatchers.IO) {
        val slot = DayTimeSlot.currentSlot()
        val query = slot.searchQueries.first()
        val tracks = runCatching {
            youTubeMusicApiService.searchSongs(query, limit)
        }.getOrDefault(emptyList())

        TimeOfDayRecommendation(
            slot = slot,
            songs = tracks
        )
    }
}

