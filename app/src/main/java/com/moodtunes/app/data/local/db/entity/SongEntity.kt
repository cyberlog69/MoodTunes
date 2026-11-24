package com.moodtunes.app.data.local.db.entity

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moodtunes.app.domain.model.AudioFormat
import com.moodtunes.app.domain.model.Song

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uriString: String,
    val albumArtUriString: String?,
    val genre: String?,
    val isFavorite: Boolean = false,
    val audioFormatName: String = "MP3",
    val isStream: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0,
    val videoId: String? = null
)

fun SongEntity.toDomain(): Song = Song(
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
    lastPlayedAt = lastPlayedAt,
    videoId = videoId ?: Uri.parse(uriString).getQueryParameter("v")
)

fun Song.toEntity(): SongEntity = SongEntity(
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
    lastPlayedAt = lastPlayedAt,
    videoId = videoId ?: uri.getQueryParameter("v")
)
