package com.moodtunes.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Process-lifetime coroutine scope for application-scoped work.
 * Injected instead of being constructed inside feature classes so that the
 * lifetime is explicit and testable.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        // Deliberately not Dispatchers.Main.immediate: an immediate dispatcher
        // runs launch/collect bodies inline on the calling thread, which lets
        // an injected collector execute before the injecting class has finished
        // initializing its fields.
        CoroutineScope(SupervisorJob() + Dispatchers.Main)
}
