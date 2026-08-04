package com.moodtunes.app.domain.usecase

import com.moodtunes.app.domain.repository.IMusicRepository
import javax.inject.Inject

class ToggleFavoriteUseCase @Inject constructor(
    private val musicRepository: IMusicRepository
) {
    suspend operator fun invoke(songId: Long): Boolean =
        musicRepository.toggleFavorite(songId)
}
