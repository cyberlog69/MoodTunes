package com.moodtunes.app.di

import com.moodtunes.app.data.repository.MoodRepositoryImpl
import com.moodtunes.app.data.repository.MusicRepositoryImpl
import com.moodtunes.app.data.repository.PlaylistRepositoryImpl
import com.moodtunes.app.domain.repository.IMoodRepository
import com.moodtunes.app.domain.repository.IMusicRepository
import com.moodtunes.app.domain.repository.IPlaylistRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMusicRepository(impl: MusicRepositoryImpl): IMusicRepository

    @Binds
    @Singleton
    abstract fun bindMoodRepository(impl: MoodRepositoryImpl): IMoodRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): IPlaylistRepository
}
