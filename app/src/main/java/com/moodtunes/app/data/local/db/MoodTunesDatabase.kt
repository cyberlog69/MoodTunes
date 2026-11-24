package com.moodtunes.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.moodtunes.app.data.local.db.dao.PlaylistDao
import com.moodtunes.app.data.local.db.dao.SongDao
import com.moodtunes.app.data.local.db.entity.PlaylistEntity
import com.moodtunes.app.data.local.db.entity.PlaylistSongEntity
import com.moodtunes.app.data.local.db.entity.SongEntity

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SongEntity::class, PlaylistEntity::class, PlaylistSongEntity::class],
    version = 6,
    exportSchema = true
)
abstract class MoodTunesDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        const val DATABASE_NAME = "moodtunes_database"

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN coverUri TEXT DEFAULT NULL")
            }
        }

        /**
         * Moods are gone. Drop the mood history table and the mood tag column
         * while preserving songs, favorites, play counts and playlists.
         *
         * `ALTER TABLE ... DROP COLUMN` needs SQLite 3.35+, which old Android
         * releases do not ship, so the songs table is rebuilt instead.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS songs_new (
                        id INTEGER PRIMARY KEY NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        album TEXT NOT NULL,
                        duration INTEGER NOT NULL,
                        uriString TEXT NOT NULL,
                        albumArtUriString TEXT,
                        genre TEXT,
                        isFavorite INTEGER NOT NULL,
                        audioFormatName TEXT NOT NULL,
                        isStream INTEGER NOT NULL,
                        playCount INTEGER NOT NULL,
                        lastPlayedAt INTEGER NOT NULL,
                        videoId TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO songs_new (
                        id, title, artist, album, duration, uriString,
                        albumArtUriString, genre, isFavorite, audioFormatName,
                        isStream, playCount, lastPlayedAt, videoId
                    )
                    SELECT
                        id, title, artist, album, duration, uriString,
                        albumArtUriString, genre, isFavorite, audioFormatName,
                        isStream, playCount, lastPlayedAt, NULL
                    FROM songs
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE songs")
                db.execSQL("ALTER TABLE songs_new RENAME TO songs")
                db.execSQL("DROP TABLE IF EXISTS mood_history")
            }
        }
    }
}
