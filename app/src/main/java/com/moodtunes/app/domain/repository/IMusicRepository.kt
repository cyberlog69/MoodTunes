package com.moodtunes.app.domain.repository

import com.moodtunes.app.domain.model.Song
import kotlinx.coroutines.flow.Flow

/**
 * Contract for app-managed YouTube Music state (favorites and play history).
 */
interface IMusicRepository {
    /** Returns all songs marked as favorites. */
    fun getFavoriteSongs(): Flow<List<Song>>

    /** Toggles the favorite status of [song]. Returns the new status. */
    suspend fun toggleFavorite(song: Song): Boolean

    /** Returns recently played songs, most recent first. */
    fun getRecentlyPlayed(limit: Int = 20): Flow<List<Song>>

    /** Returns most-played songs, highest play count first. */
    fun getMostPlayed(limit: Int = 50): Flow<List<Song>>
}
