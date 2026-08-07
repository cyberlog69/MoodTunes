package com.moodtunes.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

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
