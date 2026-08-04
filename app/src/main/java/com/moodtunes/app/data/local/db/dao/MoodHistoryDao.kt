package com.moodtunes.app.data.local.db.dao

import androidx.room.*
import com.moodtunes.app.data.local.db.entity.MoodHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMoodEntry(entry: MoodHistoryEntity)

    @Query("SELECT * FROM mood_history ORDER BY timestamp DESC")
    fun getAllMoodHistory(): Flow<List<MoodHistoryEntity>>

    @Query(
        "SELECT * FROM mood_history WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC"
    )
    fun getMoodHistoryFrom(sinceTimestamp: Long): Flow<List<MoodHistoryEntity>>

    @Query(
        """SELECT moodTypeName FROM mood_history 
        GROUP BY moodTypeName ORDER BY COUNT(*) DESC LIMIT 1"""
    )
    suspend fun getTopMoodName(): String?

    @Query("SELECT SUM(durationListenedMs) FROM mood_history")
    suspend fun getTotalListeningTimeMs(): Long?

    @Query("SELECT * FROM mood_history ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestEntry(): MoodHistoryEntity?
}
