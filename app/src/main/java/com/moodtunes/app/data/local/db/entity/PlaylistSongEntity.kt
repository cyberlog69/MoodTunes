package com.moodtunes.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Join row between a [PlaylistEntity] and a [SongEntity].
 *
 * A full JSON snapshot of the [com.moodtunes.app.domain.model.Song] is stored in
 * [songJson] so that online streams and local tracks can be restored even if the
 * source (MediaStore / remote API) is no longer reachable.
 */
@Entity(
    tableName = "playlist_songs",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId")]
)
data class PlaylistSongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val songId: Long,
    val position: Int,
    val songJson: String
)
