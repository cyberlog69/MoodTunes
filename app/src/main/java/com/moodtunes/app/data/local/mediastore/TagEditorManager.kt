package com.moodtunes.app.data.local.mediastore

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.repository.IMusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TagEditorManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: IMusicRepository
) {
    /**
     * Saves user-edited tags (title, artist, album, genre) and custom cover art for a song.
     * Updates MediaStore where permitted, copies artwork to permanent internal storage,
     * and updates the local Room database cache so changes are immediately visible.
     */
    suspend fun saveSongTags(
        song: Song,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String?,
        newArtUri: Uri?
    ): Result<Song> = withContext(Dispatchers.IO) {
        runCatching {
            var finalArtUri: Uri? = song.albumArtUri

            // 1. If new artwork was picked from device gallery, save to permanent app storage
            if (newArtUri != null && newArtUri != song.albumArtUri) {
                finalArtUri = copyArtworkToInternalStorage(song.id, newArtUri)
            }

            // 2. Attempt MediaStore update for local audio tracks
            if (!song.isStream && song.uri.scheme == "content") {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Media.TITLE, newTitle.trim())
                        put(MediaStore.Audio.Media.ARTIST, newArtist.trim())
                        put(MediaStore.Audio.Media.ALBUM, newAlbum.trim())
                    }
                    context.contentResolver.update(song.uri, values, null, null)
                } catch (e: Exception) {
                    Timber.w(e, "MediaStore update skipped or requires RecoverableSecurityException")
                }
            }

            // 3. Update Room Database for instant UI persistence
            musicRepository.updateSongTags(
                songId = song.id,
                title = newTitle.trim(),
                artist = newArtist.trim(),
                album = newAlbum.trim(),
                genre = newGenre?.trim()?.ifBlank { null },
                albumArtUri = finalArtUri
            )

            song.copy(
                title = newTitle.trim(),
                artist = newArtist.trim(),
                album = newAlbum.trim(),
                genre = newGenre?.trim()?.ifBlank { null },
                albumArtUri = finalArtUri
            )
        }
    }

    private fun copyArtworkToInternalStorage(songId: Long, sourceUri: Uri): Uri {
        val artDir = File(context.filesDir, "custom_artwork").apply { if (!exists()) mkdirs() }
        val targetFile = File(artDir, "cover_${songId}_${System.currentTimeMillis()}.jpg")

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }
        return Uri.fromFile(targetFile)
    }
}
