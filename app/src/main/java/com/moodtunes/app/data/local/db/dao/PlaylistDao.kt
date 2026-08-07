package com.moodtunes.app.data.local.db.dao

import androidx.room.*
import com.moodtunes.app.data.local.db.entity.PlaylistEntity
import com.moodtunes.app.data.local.db.entity.PlaylistSongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    // ── Playlists ────────────────────────────────────────────────────────────
    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylist(playlistId: Long): PlaylistEntity?

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    // ── Playlist songs ───────────────────────────────────────────────────────
    @Insert
    suspend fun insertSong(playlistSong: PlaylistSongEntity): Long

    @Insert
    suspend fun insertSongs(playlistSongs: List<PlaylistSongEntity>)

    @Update
    suspend fun updateSong(playlistSong: PlaylistSongEntity)

    @Query("SELECT * FROM playlist_songs ORDER BY playlistId ASC, position ASC")
    fun getAllPlaylistSongs(): Flow<List<PlaylistSongEntity>>

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    fun getSongsForPlaylist(playlistId: Long): Flow<List<PlaylistSongEntity>>

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getSongsForPlaylistSync(playlistId: Long): List<PlaylistSongEntity>

    @Query("SELECT * FROM playlist_songs WHERE id = :entryId LIMIT 1")
    suspend fun getSongEntry(entryId: Long): PlaylistSongEntity?

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId LIMIT 1")
    suspend fun getSongEntryBySongId(playlistId: Long, songId: Long): PlaylistSongEntity?

    @Query("DELETE FROM playlist_songs WHERE id = :entryId")
    suspend fun deleteSong(entryId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearSongs(playlistId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun deleteSongBySongId(playlistId: Long, songId: Long)

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun countSongs(playlistId: Long): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int
}
