package com.moodtunes.app.data.remote

import com.moodtunes.app.data.local.preferences.MusicLanguage
import com.moodtunes.app.data.remote.api.YouTubeInnerTubeClient
import com.moodtunes.app.data.remote.api.YouTubeMusicApiService
import com.moodtunes.app.data.remote.stream.StreamResolver
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.model.YouTubePage
import com.moodtunes.app.domain.model.YouTubeRadioPage
import com.moodtunes.app.domain.model.YouTubeSearchFilter
import com.moodtunes.app.domain.model.YouTubeSearchPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Modern online streaming repository powered purely by YouTube Music (InnerTube & NewPipe).
 * Provides free, full-length streaming tracks, trending charts, mood discovery,
 * and high-fidelity Opus/AAC stream resolution with 1MB chunked prefetching.
 */
@Singleton
class OnlineStreamRepository @Inject constructor(
    private val youTubeMusicApi: YouTubeMusicApiService,
    private val innerTube: YouTubeInnerTubeClient,
    private val streamResolver: StreamResolver
) {

    /**
     * Resolves the playable direct HTTPS audio streaming URL for a given YouTube Music song or URL.
     * Uses multi-tier NewPipe fast-path, VISIONOS fallback, and V8 cipher solver.
     */
    suspend fun resolveDirectStreamUrl(targetUrlOrVideoId: String): String = withContext(Dispatchers.IO) {
        val resolved = streamResolver.resolveStreamUrl(targetUrlOrVideoId)
            ?: youTubeMusicApi.resolveStreamUrl(targetUrlOrVideoId)

        if (!resolved.isNullOrBlank()) {
            resolved
        } else {
            Timber.w("Failed to resolve YouTube Music direct stream URL for: $targetUrlOrVideoId")
            targetUrlOrVideoId
        }
    }

    /**
     * Evicts the cached stream URL for [targetUrlOrVideoId] so that subsequent resolution fetches a fresh URL.
     */
    fun evictStreamCache(targetUrlOrVideoId: String) {
        streamResolver.evict(targetUrlOrVideoId)
    }

    /**
     * Returns an active cached direct stream URL if available, or null if absent or expired.
     */
    fun getCachedStreamUrl(targetUrlOrVideoId: String): String? {
        return streamResolver.getCached(targetUrlOrVideoId)?.url
    }

    /**
     * Searches YouTube Music for full-length tracks matching [query].
     */
    suspend fun searchSongs(query: String, limit: Int = 20): List<Song> = withContext(Dispatchers.IO) {
        youTubeMusicApi.searchSongs(query, limit)
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
     * The typed InnerTube client is preferred because it returns playlist ids
     * and continuation tokens; the legacy parser is kept as a fallback.
     */
    suspend fun getRadioQueue(videoId: String, limit: Int = 20): List<Song> = withContext(Dispatchers.IO) {
        val page = runCatching { innerTube.radio(videoId) }.getOrNull()
        if (page != null && page.songs.isNotEmpty()) page.songs.take(limit)
        else youTubeMusicApi.getRadioQueue(videoId, limit)
    }

    suspend fun getRadioPage(videoId: String): YouTubeRadioPage = withContext(Dispatchers.IO) {
        innerTube.radio(videoId)
    }

    suspend fun getRadioContinuation(token: String): YouTubeRadioPage = withContext(Dispatchers.IO) {
        innerTube.radioContinuation(token)
    }

    // ── Browse graph ─────────────────────────────────────────────────────────

    suspend fun getHome(): YouTubePage = withContext(Dispatchers.IO) { innerTube.home() }

    suspend fun getMoodsAndGenres(): YouTubePage = withContext(Dispatchers.IO) { innerTube.moodsAndGenres() }

    suspend fun getCharts(): YouTubePage = withContext(Dispatchers.IO) { innerTube.charts() }

    suspend fun getExplore(): YouTubePage = withContext(Dispatchers.IO) { innerTube.explore() }

    suspend fun getNewReleases(): YouTubePage = withContext(Dispatchers.IO) { innerTube.newReleases() }

    suspend fun getBrowsePage(browseId: String, params: String? = null): YouTubePage =
        withContext(Dispatchers.IO) { innerTube.browse(browseId, params) }

    suspend fun getBrowseContinuation(continuation: String): YouTubePage =
        withContext(Dispatchers.IO) { innerTube.browseContinuation(continuation) }

    // ── Typed search ─────────────────────────────────────────────────────────

    suspend fun searchPage(
        query: String,
        filter: YouTubeSearchFilter = YouTubeSearchFilter.ALL
    ): YouTubeSearchPage = withContext(Dispatchers.IO) { innerTube.search(query, filter) }

    suspend fun searchPageContinuation(
        query: String,
        filter: YouTubeSearchFilter,
        continuation: String
    ): YouTubeSearchPage = withContext(Dispatchers.IO) {
        innerTube.searchContinuation(query, filter, continuation)
    }

    suspend fun getSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        innerTube.suggestions(query)
    }

    // ── Signed-in collection ─────────────────────────────────────────────────

    suspend fun getLikedPlaylists(): YouTubePage = withContext(Dispatchers.IO) { innerTube.likedPlaylists() }

    suspend fun getLikedAlbums(): YouTubePage = withContext(Dispatchers.IO) { innerTube.likedAlbums() }

    suspend fun getLibraryArtists(): YouTubePage = withContext(Dispatchers.IO) { innerTube.libraryArtists() }

    suspend fun getLibrarySongs(): YouTubePage = withContext(Dispatchers.IO) { innerTube.librarySongs() }

    suspend fun getLibraryLanding(): YouTubePage = withContext(Dispatchers.IO) { innerTube.libraryLanding() }

    suspend fun getLibraryAlbums(): YouTubePage = withContext(Dispatchers.IO) { innerTube.libraryAlbums() }
}
