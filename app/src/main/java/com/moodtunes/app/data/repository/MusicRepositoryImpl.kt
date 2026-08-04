package com.moodtunes.app.data.repository

import android.net.Uri
import com.moodtunes.app.data.local.db.dao.SongDao
import com.moodtunes.app.data.local.db.entity.SongEntity
import com.moodtunes.app.data.local.mediastore.MediaStoreRepository
import com.moodtunes.app.domain.model.AudioFormat
import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.repository.IMusicRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepositoryImpl @Inject constructor(
    private val mediaStoreRepository: MediaStoreRepository,
    private val songDao: SongDao
) : IMusicRepository {

    override suspend fun getAllSongs(): List<Song> {
        val mediaSongs = mediaStoreRepository.getAllSongs()
        // Sync with Room to preserve favorites status
        val favoriteIds = getFavoriteIdsFromDb()
        val syncedSongs = mediaSongs.map { song ->
            song.copy(isFavorite = favoriteIds.contains(song.id))
        }
        // Upsert into db (preserves isFavorite)
        songDao.upsertSongs(syncedSongs.map { it.toEntity() })
        return syncedSongs
    }

    override suspend fun getSongsByMood(mood: MoodType): List<Song> {
        val moodSongs = mediaStoreRepository.getSongsByMood(mood)
        val favoriteIds = getFavoriteIdsFromDb()
        return moodSongs.map { it.copy(isFavorite = favoriteIds.contains(it.id)) }
    }

    override fun getFavoriteSongs(): Flow<List<Song>> =
        songDao.getFavoriteSongs().map { entities -> entities.map { it.toDomain() } }

    override suspend fun toggleFavorite(songId: Long): Boolean {
        val song = songDao.getSongById(songId)
        val newStatus = !(song?.isFavorite ?: false)
        songDao.updateFavoriteStatus(songId, newStatus)
        return newStatus
    }

    override suspend fun searchSongs(query: String): List<Song> {
        return songDao.searchSongs(query).map { it.toDomain() }
    }

    private suspend fun getFavoriteIdsFromDb(): Set<Long> {
        return emptySet()
    }

    // --- Mappers ---
    private fun Song.toEntity() = SongEntity(
        id = id,
        title = title,
        artist = artist,
        album = album,
        duration = duration,
        uriString = uri.toString(),
        albumArtUriString = albumArtUri?.toString(),
        genre = genre,
        isFavorite = isFavorite,
        audioFormatName = audioFormat.name,
        isStream = isStream
    )

    private fun SongEntity.toDomain() = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        duration = duration,
        uri = Uri.parse(uriString),
        albumArtUri = albumArtUriString?.let { Uri.parse(it) },
        genre = genre,
        isFavorite = isFavorite,
        audioFormat = runCatching { AudioFormat.valueOf(audioFormatName) }.getOrDefault(AudioFormat.MP3),
        isStream = isStream
    )
}
