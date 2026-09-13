package com.moodtunes.app.data.remote

import com.moodtunes.app.data.local.preferences.MusicLanguage
import com.moodtunes.app.data.remote.api.YouTubeMusicApiService
import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Modern online streaming repository powered purely by YouTube Music (InnerTube).
 * Provides free, full-length streaming tracks, trending charts, mood discovery,
 * and high-fidelity Opus/AAC stream resolution.
 */
@Singleton
class OnlineStreamRepository @Inject constructor(
    private val youTubeMusicApi: YouTubeMusicApiService
) {

    /**
     * Resolves the playable direct HTTPS audio streaming URL for a given YouTube Music song or URL.
     */
    suspend fun resolveDirectStreamUrl(targetUrlOrVideoId: String): String = withContext(Dispatchers.IO) {
        val resolved = youTubeMusicApi.resolveStreamUrl(targetUrlOrVideoId)
        if (!resolved.isNullOrBlank()) {
            resolved
        } else {
            Timber.w("Failed to resolve YouTube Music direct stream URL for: $targetUrlOrVideoId")
            targetUrlOrVideoId
        }
    }

    /**
     * Searches YouTube Music for full-length tracks matching [query].
     */
    suspend fun searchSongs(query: String, limit: Int = 20): List<Song> = withContext(Dispatchers.IO) {
        youTubeMusicApi.searchSongs(query, limit)
    }

    /**
     * Fetches curated songs matching the given [mood].
     */
    suspend fun getSongsByMood(mood: MoodType, limit: Int = 20): List<Song> = withContext(Dispatchers.IO) {
        youTubeMusicApi.getSongsByMood(mood, limit)
    }

    /**
     * Backward-compatible alias for fetching all online tracks matching a mood.
     */
    suspend fun fetchAllOnlineTracksForMood(
        mood: MoodType,
        language: MusicLanguage = MusicLanguage.ALL,
        limit: Int = 20
    ): List<Song> = withContext(Dispatchers.IO) {
        getSongsByMood(mood, limit)
    }

    /**
     * Fetches trending category and chart songs.
     */
    suspend fun getGeneralTrendingSongs(
        languages: Set<MusicLanguage> = emptySet(),
        category: String = "Top Hits",
        limit: Int = 20
    ): List<Song> = withContext(Dispatchers.IO) {
        youTubeMusicApi.getTrendingSongs(category, limit)
    }

    /**
     * Fetches continuous radio queue recommendations for a given [videoId].
     */
    suspend fun getRadioQueue(videoId: String, limit: Int = 20): List<Song> = withContext(Dispatchers.IO) {
        youTubeMusicApi.getRadioQueue(videoId, limit)
    }
}
