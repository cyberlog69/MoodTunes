package com.moodtunes.app.domain.repository

import com.moodtunes.app.domain.model.Playlist
import com.moodtunes.app.domain.model.Song
import kotlinx.coroutines.flow.Flow

/**
 * Contract for user-created playlist storage.
 */
interface IPlaylistRepository {

    /** All playlists (with their songs resolved) ordered by newest first. */
    fun getPlaylists(): Flow<List<Playlist>>

    /** A single playlist (with songs) by id. */
    fun getPlaylist(playlistId: Long): Flow<Playlist?>

    /** Creates a new empty playlist. Returns the new playlist id. */
    suspend fun createPlaylist(name: String): Long

    suspend fun renamePlaylist(playlistId: Long, name: String)

    suspend fun deletePlaylist(playlistId: Long)

    /** Appends a song to the end of a playlist. */
    suspend fun addSongToPlaylist(playlistId: Long, song: Song)

    /** Removes a song (first occurrence) from a playlist. */
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)

    /** Moves a song at [fromPosition] to [toPosition] within a playlist. */
    suspend fun moveSong(playlistId: Long, fromPosition: Int, toPosition: Int)

    /** Replaces all songs of a playlist (used by import/restore). */
    suspend fun replacePlaylistSongs(playlistId: Long, songs: List<Song>)
}
