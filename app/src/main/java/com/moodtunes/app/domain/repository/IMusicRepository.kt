package com.moodtunes.app.domain.repository

import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.model.Song
import kotlinx.coroutines.flow.Flow

/**
 * Contract for all music-related data operations.
 */
interface IMusicRepository {
    /** Returns all audio files from the device's MediaStore. */
    suspend fun getAllSongs(): List<Song>

    /** Returns songs that match the given mood based on keyword matching. */
    suspend fun getSongsByMood(mood: MoodType): List<Song>

    /** Returns all songs marked as favorites. */
    fun getFavoriteSongs(): Flow<List<Song>>

    /** Toggles the favorite status of a song. Returns the new status. */
    suspend fun toggleFavorite(songId: Long): Boolean

    /** Searches songs by title or artist. */
    suspend fun searchSongs(query: String): List<Song>
}
