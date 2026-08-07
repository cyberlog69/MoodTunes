package com.moodtunes.app.domain.usecase

import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.repository.IMusicRepository
import com.moodtunes.app.domain.repository.IMoodRepository
import com.moodtunes.app.domain.util.RecommendationEngine
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Computes personalized song recommendations ("For You") from the user's
 * favorites, most-played tracks, and dominant mood.
 */
class GetForYouSongsUseCase @Inject constructor(
    private val musicRepository: IMusicRepository,
    private val moodRepository: IMoodRepository
) {
    suspend operator fun invoke(limit: Int = 20): List<Song> {
        val allSongs = musicRepository.getAllSongs()
        val favorites = musicRepository.getFavoriteSongs()
            .first()
            .filter { it.isFavorite }
        val mostPlayed = musicRepository.getMostPlayed(limit * 3)
            .first()
        val topMood = moodRepository.getTopMood()

        return RecommendationEngine.recommendForYou(
            allSongs = allSongs,
            favoriteSongs = favorites,
            mostPlayed = mostPlayed,
            topMood = topMood,
            limit = limit
        )
    }
}
