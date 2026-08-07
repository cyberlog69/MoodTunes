package com.moodtunes.app.domain.usecase

import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.repository.IMusicRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetRecentlyPlayedUseCase @Inject constructor(
    private val musicRepository: IMusicRepository
) {
    operator fun invoke(limit: Int = 20): Flow<List<Song>> =
        musicRepository.getRecentlyPlayed(limit)
}
