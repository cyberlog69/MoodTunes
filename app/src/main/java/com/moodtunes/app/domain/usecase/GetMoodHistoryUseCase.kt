package com.moodtunes.app.domain.usecase

import com.moodtunes.app.domain.model.MoodEntry
import com.moodtunes.app.domain.repository.IMoodRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMoodHistoryUseCase @Inject constructor(
    private val moodRepository: IMoodRepository
) {
    operator fun invoke(days: Int = 30): Flow<List<MoodEntry>> =
        moodRepository.getMoodHistoryForDays(days)

    fun allHistory(): Flow<List<MoodEntry>> = moodRepository.getMoodHistory()
}
