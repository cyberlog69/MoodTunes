package com.moodtunes.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.moodtunes.app.data.local.db.dao.MoodHistoryDao
import com.moodtunes.app.data.local.db.dao.PlaylistDao
import com.moodtunes.app.data.local.db.dao.SongDao
import com.moodtunes.app.data.local.db.entity.MoodHistoryEntity
import com.moodtunes.app.data.local.db.entity.PlaylistEntity
import com.moodtunes.app.data.local.db.entity.PlaylistSongEntity
import com.moodtunes.app.data.local.db.entity.SongEntity

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SongEntity::class, MoodHistoryEntity::class, PlaylistEntity::class, PlaylistSongEntity::class],
    version = 5,
    exportSchema = false
)
abstract class MoodTunesDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun moodHistoryDao(): MoodHistoryDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        const val DATABASE_NAME = "moodtunes_database"

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN coverUri TEXT DEFAULT NULL")
            }
        }
    }
}
