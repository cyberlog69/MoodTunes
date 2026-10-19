package com.moodtunes.app.data.repository

import android.net.Uri
import com.moodtunes.app.data.local.db.dao.SongDao
import com.moodtunes.app.data.local.db.entity.SongEntity
import com.moodtunes.app.data.local.db.entity.toDomain
import com.moodtunes.app.data.local.db.entity.toEntity
import com.moodtunes.app.data.remote.OnlineStreamRepository
import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.repository.IMusicRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepositoryImpl @Inject constructor(
    private val onlineStreamRepository: OnlineStreamRepository,
    private val songDao: SongDao
) : IMusicRepository {

    override suspend fun getAllSongs(): List<Song> {
        val trending = runCatching { onlineStreamRepository.getGeneralTrendingSongs() }.getOrDefault(emptyList())
        if (trending.isNotEmpty()) {
            val merged = mergeDbStats(trending)
            songDao.upsertSongs(merged.map { it.toEntity() })
            return merged
        }
        val cached = songDao.getAllSongs().firstOrNull()?.map { it.toDomain() } ?: emptyList()
        return cached.sortedBy { it.title.lowercase() }
    }

    override suspend fun getSongsByMood(mood: MoodType): List<Song> {
        val onlineMoodSongs = runCatching { onlineStreamRepository.getSongsByMood(mood) }.getOrDefault(emptyList())
        if (onlineMoodSongs.isNotEmpty()) {
            val merged = mergeDbStats(onlineMoodSongs)
            songDao.upsertSongs(merged.map { it.toEntity() })
            return merged
        }
        val cached = songDao.getAllSongs().firstOrNull()?.map { it.toDomain() } ?: emptyList()
        return cached.filter { it.moodTags.contains(mood) }
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
        val onlineMatches = runCatching { onlineStreamRepository.searchSongs(query) }.getOrDefault(emptyList())
        if (onlineMatches.isNotEmpty()) {
            val merged = mergeDbStats(onlineMatches)
            songDao.upsertSongs(merged.map { it.toEntity() })
            return merged
        }
        return songDao.searchSongs(query).map { it.toDomain() }
    }

    override fun getRecentlyPlayed(limit: Int): Flow<List<Song>> =
        songDao.getRecentlyPlayed(limit).map { entities -> entities.map { it.toDomain() } }

    override fun getMostPlayed(limit: Int): Flow<List<Song>> =
        songDao.getMostPlayed(limit).map { entities -> entities.map { it.toDomain() } }

    override suspend fun updateSongTags(
        songId: Long,
        title: String,
        artist: String,
        album: String,
        genre: String?,
        albumArtUri: Uri?
    ) {
        songDao.updateSongTags(
            songId = songId,
            title = title,
            artist = artist,
            album = album,
            genre = genre,
            albumArtUri = albumArtUri?.toString()
        )
    }

    /** Loads persisted rows for the given songs and overlays DB-only state. */
    private suspend fun mergeDbStats(songs: List<Song>): List<Song> {
        if (songs.isEmpty()) return songs
        val dbRows = songDao.getSongsByIds(songs.map { it.id })
        return mergeStats(songs, dbRows)
    }
}

/**
 * Pure overlay of persisted (DB-only) state onto tracks, so updates
 * never clobber favorites, play counts, or listening recency.
 */
internal fun mergeStats(songs: List<Song>, dbRows: List<SongEntity>): List<Song> {
    if (songs.isEmpty()) return songs
    val rowsById = dbRows.associateBy { it.id }
    return songs.map { song ->
        val existing = rowsById[song.id]
        if (existing == null) {
            song
        } else {
            song.copy(
                isFavorite = existing.isFavorite,
                playCount = existing.playCount,
                lastPlayedAt = existing.lastPlayedAt
            )
        }
    }
}
