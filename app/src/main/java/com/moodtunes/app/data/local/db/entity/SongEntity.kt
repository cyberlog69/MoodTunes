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
    val moodTagsJson: String = "[]"  // JSON array of MoodType names
)
