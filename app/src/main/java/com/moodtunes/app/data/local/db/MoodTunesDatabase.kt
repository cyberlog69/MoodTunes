package com.moodtunes.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.moodtunes.app.data.local.db.dao.MoodHistoryDao
import com.moodtunes.app.data.local.db.dao.SongDao
import com.moodtunes.app.data.local.db.entity.MoodHistoryEntity
import com.moodtunes.app.data.local.db.entity.SongEntity

@Database(
    entities = [SongEntity::class, MoodHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MoodTunesDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun moodHistoryDao(): MoodHistoryDao

    companion object {
        const val DATABASE_NAME = "moodtunes_database"
    }
}
