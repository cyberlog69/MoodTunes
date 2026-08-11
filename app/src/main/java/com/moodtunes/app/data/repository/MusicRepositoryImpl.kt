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
        // Merge persisted state (favorites, play counts, recency) so a rescan
        // never clobbers listening history.
        val merged = mergeDbStats(mediaSongs)
        songDao.upsertSongs(merged.map { it.toEntity() })
        return merged
    }

    override suspend fun getSongsByMood(mood: MoodType): List<Song> {
        val moodSongs = mediaStoreRepository.getSongsByMood(mood)
        return mergeDbStats(moodSongs)
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
        return mergeStats(songs, songDao.getSongsByIds(songs.map { it.id }))
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
        isStream = isStream,
        playCount = playCount,
        lastPlayedAt = lastPlayedAt
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
        isStream = isStream,
        playCount = playCount,
        lastPlayedAt = lastPlayedAt
    )
}

/**
 * Pure overlay of persisted (DB-only) state onto fresh MediaStore scans, so a
 * rescan never clobbers favorites, play counts, or listening recency.
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
