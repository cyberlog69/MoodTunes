package com.moodtunes.app.data.local.mediastore

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.moodtunes.app.domain.model.AudioFormat
import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Queries the device's MediaStore to retrieve local FLAC, ALAC, WAV, AAC, and MP3 audio files.
 * Performs keyword-based mood matching using song title, artist, and genre metadata.
 * Also provides high-resolution streaming streams for online listening.
 */
@Singleton
class MediaStoreRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** High-Quality Lossless & HQ Audio Online Demo Streams */
    private val sampleStreamingTracks = listOf(
        Song(
            id = 999001,
            title = "Chill Lofi Beats (Lossless FLAC Stream)",
            artist = "Lofi Girl Radio",
            album = "HQ Chill Streams",
            duration = 0,
            uri = Uri.parse("https://stream.zeno.fm/f3wvbbqmdg8uv"),
            albumArtUri = null,
            genre = "Chill",
            audioFormat = AudioFormat.FLAC,
            isStream = true,
            moodTags = listOf(MoodType.CALM, MoodType.SLEEP)
        ),
        Song(
            id = 999002,
            title = "Electro Euphoria Raves (Hi-Res Streaming)",
            artist = "EDM Live HQ",
            album = "Festival Lossless",
            duration = 0,
            uri = Uri.parse("https://stream.zeno.fm/87vcfd89z18uv"),
            albumArtUri = null,
            genre = "EDM",
            audioFormat = AudioFormat.ALAC,
            isStream = true,
            moodTags = listOf(MoodType.EUPHORIC, MoodType.ENERGETIC, MoodType.HAPPY)
        ),
        Song(
            id = 999003,
            title = "Deep Focus Piano (Lossless FLAC Audio)",
            artist = "Ambient Sounds HQ",
            album = "Piano Dreams",
            duration = 0,
            uri = Uri.parse("https://stream.zeno.fm/0r0xa792kwzuv"),
            albumArtUri = null,
            genre = "Classical",
            audioFormat = AudioFormat.FLAC,
            isStream = true,
            moodTags = listOf(MoodType.CALM, MoodType.SAD, MoodType.SLEEP)
        )
    )

    /** Fetches all audio files (FLAC, ALAC, WAV, AAC, MP3) from the device plus streams. */
    suspend fun getAllSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DISPLAY_NAME
        ).toTypedArray()

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 3000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val nameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val albumId = cursor.getLong(albumIdCol)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    )
                    val albumArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"), albumId
                    )
                    val title = cursor.getString(titleCol) ?: "Unknown"
                    val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                    val album = cursor.getString(albumCol) ?: "Unknown Album"
                    val duration = cursor.getLong(durationCol)
                    val mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) else ""
                    val displayName = if (nameCol >= 0) cursor.getString(nameCol) else ""

                    val format = detectAudioFormat(mimeType, displayName)

                    songs.add(
                        Song(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            duration = duration,
                            uri = contentUri,
                            albumArtUri = albumArtUri,
                            audioFormat = format,
                            isStream = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Combine local files with high quality online streaming tracks
        songs.addAll(sampleStreamingTracks)
        songs
    }

    /** Detects FLAC, ALAC, WAV, AAC, and MP3 format from MIME type or file extension */
    private fun detectAudioFormat(mimeType: String?, displayName: String?): AudioFormat {
        val mime = mimeType?.lowercase() ?: ""
        val name = displayName?.lowercase() ?: ""

        return when {
            mime.contains("flac") || name.endsWith(".flac") -> AudioFormat.FLAC
            mime.contains("alac") || name.contains("alac") || (mime.contains("m4a") && name.contains("alac")) -> AudioFormat.ALAC
            mime.contains("wav") || name.endsWith(".wav") -> AudioFormat.WAV
            mime.contains("aac") || name.endsWith(".aac") -> AudioFormat.AAC_HQ
            else -> AudioFormat.MP3
        }
    }

    /**
     * Filters songs from MediaStore/Streams that match the given mood's keywords.
     * Matching is done on title + artist + album + genre metadata.
     */
    suspend fun getSongsByMood(mood: MoodType): List<Song> = withContext(Dispatchers.IO) {
        val allSongs = getAllSongs()
        val keywords = mood.keywords

        val matched = allSongs.filter { song ->
            val combined = "${song.title} ${song.artist} ${song.album} ${song.genre ?: ""}".lowercase()
            keywords.any { keyword -> combined.contains(keyword.lowercase()) } ||
                    song.moodTags.contains(mood)
        }

        if (matched.isEmpty()) allSongs else matched
    }
}
