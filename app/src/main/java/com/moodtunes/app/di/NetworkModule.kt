package com.moodtunes.app.di

import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.moodtunes.app.data.remote.stream.StreamResolver
import com.moodtunes.app.data.remote.stream.YouTubeStreamInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Provider
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StreamingClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideBaseOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @Provides
    @Singleton
    @StreamingClient
    fun provideStreamingOkHttpClient(
        streamResolverProvider: Provider<StreamResolver>
    ): OkHttpClient {
        // Underlying bare client for executing short ranged chunk requests
        val chunkClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        // Outer client wrapping YouTubeStreamInterceptor with 1MB chunked prefetch and transparent stream resolution
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(
                YouTubeStreamInterceptor(
                    chunkClient = chunkClient,
                    streamResolverProvider = streamResolverProvider
                )
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideOkHttpDataSourceFactory(
        @StreamingClient streamingClient: OkHttpClient
    ): OkHttpDataSource.Factory {
        return OkHttpDataSource.Factory(streamingClient)
    }
}
