package com.moodtunes.app.domain.usecase

import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.repository.IMusicRepository
import javax.inject.Inject

/**
 * Returns a shuffled list of songs matching the selected mood.
 * Falls back to a shuffled subset of all songs if no keyword matches are found.
 */
class GetSongsByMoodUseCase @Inject constructor(
    private val musicRepository: IMusicRepository
) {
    suspend operator fun invoke(mood: MoodType): List<Song> {
        val moodSongs = musicRepository.getSongsByMood(mood)
        return if (moodSongs.isNotEmpty()) {
            moodSongs.shuffled()
        } else {
            // Graceful fallback: return a shuffled random selection from all songs
            musicRepository.getAllSongs().shuffled().take(30)
        }
    }
}
