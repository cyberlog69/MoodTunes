package com.moodtunes.app.domain.usecase

import com.moodtunes.app.domain.model.MoodEntry
import com.moodtunes.app.domain.repository.IMoodRepository
import javax.inject.Inject

class SaveMoodHistoryUseCase @Inject constructor(
    private val moodRepository: IMoodRepository
) {
    suspend operator fun invoke(entry: MoodEntry) = moodRepository.saveMoodEntry(entry)
}
