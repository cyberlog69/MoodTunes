package com.moodtunes.app.di

import android.content.Context
import androidx.room.Room
import com.moodtunes.app.data.local.db.MoodTunesDatabase
import com.moodtunes.app.data.local.db.dao.MoodHistoryDao
import com.moodtunes.app.data.local.db.dao.SongDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMoodTunesDatabase(@ApplicationContext context: Context): MoodTunesDatabase {
        return Room.databaseBuilder(
            context,
            MoodTunesDatabase::class.java,
            MoodTunesDatabase.DATABASE_NAME
        ).fallbackToDestructiveMigration(true).build()
    }

    @Provides
    fun provideSongDao(db: MoodTunesDatabase): SongDao = db.songDao()

    @Provides
    fun provideMoodHistoryDao(db: MoodTunesDatabase): MoodHistoryDao = db.moodHistoryDao()
}
