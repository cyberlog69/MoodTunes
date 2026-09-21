package com.moodtunes.app.data.local.db.entity

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moodtunes.app.domain.model.AudioFormat
import com.moodtunes.app.domain.model.Song
import org.json.JSONArray

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
    val moodTagsJson: String = "[]",
    val audioFormatName: String = "MP3",
    val isStream: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0
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
    lastPlayedAt = lastPlayedAt
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
    moodTagsJson = JSONArray(moodTags.map { it.name }).toString(),
    audioFormatName = audioFormat.name,
    isStream = isStream,
    playCount = playCount,
    lastPlayedAt = lastPlayedAt
)
