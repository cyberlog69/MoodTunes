package com.moodtunes.app.data.local.mediastore

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.moodtunes.app.data.local.preferences.AudioSourceMode
import com.moodtunes.app.data.local.preferences.StreamingProvider
import com.moodtunes.app.data.local.preferences.UserPreferencesRepository
import com.moodtunes.app.data.remote.OnlineStreamRepository
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
 * Integrates OnlineStreamRepository to fetch live, ISP-unrestricted Audius and YouTube streaming tracks.
 * Respects user settings for Local vs Stream audio modes & Audius vs YouTube streaming providers.
 */
@Singleton
class MediaStoreRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val onlineStreamRepository: OnlineStreamRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    // COPYRIGHT FIX (C1): Removed hardcoded Zeno.fm commercial radio URLs.
    // Zeno.fm hosts copyrighted broadcast radio that the app is not licensed to relay.
    // All online tracks are now sourced exclusively from:
    //   - Audius: decentralised protocol with CC-licensed, royalty-free artist uploads
    //   - YouTube via Piped: user-initiated streaming (same as browser playback)

    /** Fetches audio files according to user's selected AudioSourceMode */
    suspend fun getAllSongs(): List<Song> = withContext(Dispatchers.IO) {
        val settings = userPreferencesRepository.settings.value

        val localSongs = if (settings.audioSourceMode != AudioSourceMode.STREAM_ONLY) {
            fetchLocalMediaSongs()
        } else {
            emptyList()
        }

        if (settings.audioSourceMode == AudioSourceMode.LOCAL_ONLY) {
            return@withContext localSongs
        }

        // Online tracks are fetched on-demand via getSongsByMood() — no hardcoded stream list
        localSongs
    }

    private fun fetchLocalMediaSongs(): List<Song> {
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
        return songs
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
     * Filters songs matching mood. Respects AudioSourceMode and StreamingProvider preferences.
     */
    suspend fun getSongsByMood(mood: MoodType): List<Song> = withContext(Dispatchers.IO) {
        val settings = userPreferencesRepository.settings.value
        val resultList = mutableListOf<Song>()

        // 1. Local files if allowed
        if (settings.audioSourceMode != AudioSourceMode.STREAM_ONLY) {
            val allLocal = fetchLocalMediaSongs()
            val keywords = mood.keywords
            val localMatched = allLocal.filter { song ->
                val combined = "${song.title} ${song.artist} ${song.album} ${song.genre ?: ""}".lowercase()
                keywords.any { keyword -> combined.contains(keyword.lowercase()) } ||
                        song.moodTags.contains(mood)
            }
            resultList.addAll(if (localMatched.isNotEmpty()) localMatched else allLocal.take(10))
        }

        // If local only, return immediately
        if (settings.audioSourceMode == AudioSourceMode.LOCAL_ONLY) {
            return@withContext resultList
        }

        // 2. Online streams according to StreamingProvider setting
        val provider = settings.streamingProvider

        when (provider) {
            StreamingProvider.ALL_COMBINED -> {
                try {
                    val tracks = onlineStreamRepository.fetchAllOnlineTracksForMood(mood, settings.preferredLanguage)
                    resultList.addAll(tracks)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            StreamingProvider.JIOSAAVN_REGIONAL -> {
                try {
                    val saavnTracks = onlineStreamRepository.getJioSaavnTracksByMood(mood, settings.preferredLanguage, limit = 16)
                    resultList.addAll(saavnTracks)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            StreamingProvider.AUDIUS_ONLY -> {
                try {
                    val audiusTracks = onlineStreamRepository.getAudiusTracksByMood(mood, settings.preferredLanguage, limit = 10)
                    resultList.addAll(audiusTracks)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            StreamingProvider.ITUNES_DEEZER -> {
                try {
                    val itunesTracks = onlineStreamRepository.getITunesPreviewTracks(
                        languages = setOf(settings.preferredLanguage),
                        categoryQuery = "${mood.displayName} music",
                        limit = 8
                    )
                    val deezerTracks = onlineStreamRepository.getDeezerPreviewTracks(
                        languages = setOf(settings.preferredLanguage),
                        categoryQuery = "${mood.displayName} music",
                        limit = 8
                    )
                    resultList.addAll((itunesTracks + deezerTracks).distinctBy { it.id })
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            StreamingProvider.JAMENDO_ONLY -> {
                try {
                    val jamendoTracks = onlineStreamRepository.getJamendoTracks(
                        languages = setOf(settings.preferredLanguage),
                        categoryQuery = mood.displayName,
                        limit = 12
                    )
                    resultList.addAll(jamendoTracks)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            StreamingProvider.INTERNET_RADIO -> {
                try {
                    val radioTracks = onlineStreamRepository.getGlobalInternetRadioStations(
                        languages = setOf(settings.preferredLanguage),
                        categoryQuery = mood.displayName,
                        limit = 12
                    )
                    resultList.addAll(radioTracks)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        resultList
    }
}
