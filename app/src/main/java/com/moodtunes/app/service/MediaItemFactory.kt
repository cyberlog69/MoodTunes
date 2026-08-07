package com.moodtunes.app.service

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.moodtunes.app.domain.model.Song

/**
 * Builds Media3 [MediaItem]s from domain [Song]s so the same representation is
 * shared by the local player, Android Auto browsing, and Chromecast.
 */
object MediaItemFactory {

    fun songToMediaItem(song: Song, resolvedUri: Uri? = null): MediaItem =
        MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(resolvedUri ?: song.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(song.albumArtUri)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
}
