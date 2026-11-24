package com.moodtunes.app.data.repository

import com.moodtunes.app.data.local.db.dao.SongDao
import com.moodtunes.app.data.local.db.entity.toDomain
import com.moodtunes.app.data.local.db.entity.toEntity
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.repository.IMusicRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepositoryImpl @Inject constructor(
    private val songDao: SongDao
) : IMusicRepository {

    override fun getFavoriteSongs(): Flow<List<Song>> =
        songDao.getFavoriteSongs().map { entities -> entities.map { it.toDomain() } }

    override suspend fun toggleFavorite(song: Song): Boolean {
        val existing = songDao.getSongById(song.id)
        val newStatus = !(existing?.isFavorite ?: song.isFavorite)
        val needsPersistedMetadata =
            existing == null || (existing.videoId.isNullOrBlank() && !song.videoId.isNullOrBlank())
        if (needsPersistedMetadata) {
            // First interaction with this YouTube track (or an older row that
            // predates video-id persistence): store the full snapshot so the
            // favorite survives and can still seed radio when replayed.
            songDao.upsertSong(song.toEntity().copy(isFavorite = newStatus))
        } else {
            songDao.updateFavoriteStatus(song.id, newStatus)
        }
        return newStatus
    }

    override fun getRecentlyPlayed(limit: Int): Flow<List<Song>> =
        songDao.getRecentlyPlayed(limit).map { entities -> entities.map { it.toDomain() } }

    override fun getMostPlayed(limit: Int): Flow<List<Song>> =
        songDao.getMostPlayed(limit).map { entities -> entities.map { it.toDomain() } }
}
