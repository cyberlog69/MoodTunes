package com.moodtunes.app.data.local.db.dao

import androidx.room.*
import com.moodtunes.app.data.local.db.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavoriteSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :songId LIMIT 1")
    suspend fun getSongById(songId: Long): SongEntity?

    @Upsert
    suspend fun upsertSong(song: SongEntity)

    @Upsert
    suspend fun upsertSongs(songs: List<SongEntity>)

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :songId")
    suspend fun updateFavoriteStatus(songId: Long, isFavorite: Boolean)

    @Query(
        """SELECT * FROM songs WHERE 
        LOWER(title) LIKE '%' || LOWER(:query) || '%' OR 
        LOWER(artist) LIKE '%' || LOWER(:query) || '%' OR 
        LOWER(album) LIKE '%' || LOWER(:query) || '%'
        ORDER BY title ASC"""
    )
    suspend fun searchSongs(query: String): List<SongEntity>

    @Query("DELETE FROM songs WHERE id NOT IN (:activeIds)")
    suspend fun deleteRemovedSongs(activeIds: List<Long>)
}
