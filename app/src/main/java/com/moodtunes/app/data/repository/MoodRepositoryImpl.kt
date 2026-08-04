package com.moodtunes.app.data.repository

import com.moodtunes.app.data.local.db.dao.MoodHistoryDao
import com.moodtunes.app.data.local.db.entity.MoodHistoryEntity
import com.moodtunes.app.domain.model.MoodEntry
import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.repository.IMoodRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MoodRepositoryImpl @Inject constructor(
    private val moodHistoryDao: MoodHistoryDao
) : IMoodRepository {

    override suspend fun saveMoodEntry(entry: MoodEntry) {
        moodHistoryDao.insertMoodEntry(entry.toEntity())
    }

    override fun getMoodHistory(): Flow<List<MoodEntry>> =
        moodHistoryDao.getAllMoodHistory().map { it.map { e -> e.toDomain() } }

    override fun getMoodHistoryForDays(days: Int): Flow<List<MoodEntry>> {
        val sinceTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        return moodHistoryDao.getMoodHistoryFrom(sinceTimestamp)
            .map { it.map { e -> e.toDomain() } }
    }

    override suspend fun getTopMood(): MoodType? {
        return moodHistoryDao.getTopMoodName()?.let { name ->
            MoodType.entries.firstOrNull { it.name == name }
        }
    }

    override suspend fun getTotalListeningTimeMs(): Long {
        return moodHistoryDao.getTotalListeningTimeMs() ?: 0L
    }

    // --- Mappers ---
    private fun MoodEntry.toEntity() = MoodHistoryEntity(
        id = id,
        moodTypeName = moodType.name,
        timestamp = timestamp,
        songCount = songCount,
        durationListenedMs = durationListenedMs
    )

    private fun MoodHistoryEntity.toDomain() = MoodEntry(
        id = id,
        moodType = MoodType.valueOf(moodTypeName),
        timestamp = timestamp,
        songCount = songCount,
        durationListenedMs = durationListenedMs
    )
}
