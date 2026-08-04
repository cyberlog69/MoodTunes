package com.moodtunes.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mood_history")
data class MoodHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val moodTypeName: String,   // MoodType.name (enum name)
    val timestamp: Long,
    val songCount: Int,
    val durationListenedMs: Long
)
