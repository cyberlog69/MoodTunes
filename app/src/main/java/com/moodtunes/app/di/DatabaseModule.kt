package com.moodtunes.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moodtunes.app.data.local.db.MoodTunesDatabase
import com.moodtunes.app.data.local.db.MoodTunesDbGuardian
import com.moodtunes.app.data.local.db.dao.MoodHistoryDao
import com.moodtunes.app.data.local.db.dao.PlaylistDao
import com.moodtunes.app.data.local.db.dao.SongDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMoodTunesDatabase(@ApplicationContext context: Context): MoodTunesDatabase {
        // Restore from backup if the previous session ended in a corrupt state.
        MoodTunesDbGuardian.ensureHealthy(context)

        return Room.databaseBuilder(
            context,
            MoodTunesDatabase::class.java,
            MoodTunesDatabase.DATABASE_NAME
        )
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .fallbackToDestructiveMigration(true)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    val integrityOk = runCatching {
                        db.query("PRAGMA quick_check(1)").use { cursor ->
                            cursor.moveToFirst() && cursor.getString(0) == "ok"
                        }
                    }.getOrDefault(true)

                    if (integrityOk) {
                        MoodTunesDbGuardian.clearCorruptMarker(context)
                        MoodTunesDbGuardian.backupGoodDatabase(context)
                    } else {
                        Timber.e("Room integrity check failed — will restore from backup on next launch")
                        MoodTunesDbGuardian.markCorrupt(context)
                    }
                }
            })
            .build()
    }

    @Provides
    fun provideSongDao(db: MoodTunesDatabase): SongDao = db.songDao()

    @Provides
    fun provideMoodHistoryDao(db: MoodTunesDatabase): MoodHistoryDao = db.moodHistoryDao()

    @Provides
    fun providePlaylistDao(db: MoodTunesDatabase): PlaylistDao = db.playlistDao()
}
