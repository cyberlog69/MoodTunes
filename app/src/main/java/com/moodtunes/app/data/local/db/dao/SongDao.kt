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

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun getSongsByIds(ids: List<Long>): List<SongEntity>

    @Query("SELECT * FROM songs WHERE lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun getRecentlyPlayed(limit: Int): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE playCount > 0 ORDER BY playCount DESC, lastPlayedAt DESC LIMIT :limit")
    fun getMostPlayed(limit: Int): Flow<List<SongEntity>>

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayedAt = :now WHERE id = :songId")
    suspend fun incrementPlayCount(songId: Long, now: Long)

    @Upsert
    suspend fun upsertSong(song: SongEntity)

    @Upsert
    suspend fun upsertSongs(songs: List<SongEntity>)

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :songId")
    suspend fun updateFavoriteStatus(songId: Long, isFavorite: Boolean)

    @Query("UPDATE songs SET title = :title, artist = :artist, album = :album, genre = :genre, albumArtUriString = :albumArtUri WHERE id = :songId")
    suspend fun updateSongTags(songId: Long, title: String, artist: String, album: String, genre: String?, albumArtUri: String?)

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
