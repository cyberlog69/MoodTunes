package com.moodtunes.app.domain.repository

import com.moodtunes.app.domain.model.MoodEntry
import com.moodtunes.app.domain.model.MoodType
import kotlinx.coroutines.flow.Flow

/**
 * Contract for mood history and analytics data operations.
 */
interface IMoodRepository {
    /** Saves a mood session entry to the local database. */
    suspend fun saveMoodEntry(entry: MoodEntry)

    /** Returns all saved mood entries as a reactive stream. */
    fun getMoodHistory(): Flow<List<MoodEntry>>

    /** Returns mood entries for the past [days] days. */
    fun getMoodHistoryForDays(days: Int): Flow<List<MoodEntry>>

    /** Returns the most-used mood across all history. */
    suspend fun getTopMood(): MoodType?

    /** Returns total listening time across all sessions in milliseconds. */
    suspend fun getTotalListeningTimeMs(): Long
}
