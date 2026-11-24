package com.moodtunes.app.data.local.db.dao

import androidx.room.*
import com.moodtunes.app.data.local.db.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavoriteSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :songId LIMIT 1")
    suspend fun getSongById(songId: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun getRecentlyPlayed(limit: Int): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE playCount > 0 ORDER BY playCount DESC, lastPlayedAt DESC LIMIT :limit")
    fun getMostPlayed(limit: Int): Flow<List<SongEntity>>

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayedAt = :now WHERE id = :songId")
    suspend fun incrementPlayCount(songId: Long, now: Long)

    @Upsert
    suspend fun upsertSong(song: SongEntity)

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :songId")
    suspend fun updateFavoriteStatus(songId: Long, isFavorite: Boolean)
}
