package com.moodtunes.app.data.local.download

import android.app.NotificationManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.moodtunes.app.MoodTunesApp
import com.moodtunes.app.data.local.db.dao.SongDao
import com.moodtunes.app.data.local.db.entity.SongEntity
import com.moodtunes.app.data.local.preferences.UserPreferencesRepository
import com.moodtunes.app.data.remote.OnlineStreamRepository
import com.moodtunes.app.domain.model.AudioFormat
import com.moodtunes.app.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val progress: Float) : DownloadState
    data class Completed(val fileUri: Uri) : DownloadState
    data class Failed(val reason: String) : DownloadState
}

@Singleton
class SongDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val onlineStreamRepository: OnlineStreamRepository,
    private val songDao: SongDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val prefs = context.getSharedPreferences("moodtunes_downloads", Context.MODE_PRIVATE)

    // In-memory per-song download state
    private val _downloadStates = MutableStateFlow<Map<Long, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<Long, DownloadState>> = _downloadStates.asStateFlow()

    // Set of downloaded song IDs persisted across launches
    private val _downloadedSongIds = MutableStateFlow<Set<Long>>(loadDownloadedSongIds())
    val downloadedSongIds: StateFlow<Set<Long>> = _downloadedSongIds.asStateFlow()

    // Single-event notification stream (e.g. for Toasts)
    private val _userFeedback = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val userFeedback: SharedFlow<String> = _userFeedback.asSharedFlow()

    private fun loadDownloadedSongIds(): Set<Long> {
        val raw = prefs.getStringSet("saved_ids", emptySet()) ?: emptySet()
        return raw.mapNotNull { it.toLongOrNull() }.toSet()
    }

    private fun saveDownloadedSongId(songId: Long) {
        val current = _downloadedSongIds.value + songId
        _downloadedSongIds.value = current
        prefs.edit().putStringSet("saved_ids", current.map { it.toString() }.toSet()).apply()
    }

    private fun removeDownloadedSongId(songId: Long) {
        val current = _downloadedSongIds.value - songId
        _downloadedSongIds.value = current
        prefs.edit().putStringSet("saved_ids", current.map { it.toString() }.toSet()).apply()
    }

    fun isDownloaded(songId: Long): Boolean {
        return _downloadedSongIds.value.contains(songId)
    }

    fun getDownloadState(songId: Long): DownloadState {
        return _downloadStates.value[songId]
            ?: if (isDownloaded(songId)) DownloadState.Completed(Uri.EMPTY) else DownloadState.Idle
    }

    /**
     * Initiates asynchronous background download of a streamed track.
     */
    fun downloadSong(song: Song) {
        if (isDownloaded(song.id)) {
            _userFeedback.tryEmit("\"${song.title}\" is already downloaded")
            return
        }

        val currentState = _downloadStates.value[song.id]
        if (currentState is DownloadState.Downloading) {
            _userFeedback.tryEmit("Download already in progress for \"${song.title}\"")
            return
        }

        // Check Wi-Fi constraint
        val settings = userPreferencesRepository.settings.value
        if (settings.wifiOnlyDownloads && !isWifiConnected()) {
            _userFeedback.tryEmit("Wi-Fi only downloads enabled. Connect to Wi-Fi to download.")
            return
        }

        _userFeedback.tryEmit("Downloading \"${song.title}\"...")
        _downloadStates.update { it + (song.id to DownloadState.Downloading(0f)) }

        scope.launch {
            executeDownload(song)
        }
    }

    private suspend fun executeDownload(song: Song) = withContext(Dispatchers.IO) {
        val notificationId = (song.id.hashCode() and 0x7FFFFFFF)

        // Show starting notification
        showProgressNotification(notificationId, song.title, song.artist, 0)

        try {
            // 1. Resolve playable direct audio stream URL
            val directStreamUrl = onlineStreamRepository.resolveDirectStreamUrl(song.uri.toString())
            if (directStreamUrl.isBlank() || !directStreamUrl.startsWith("http") || directStreamUrl.contains("youtube.com/watch")) {
                throw IllegalStateException("Failed to resolve playable audio stream")
            }

            // 2. Request audio stream
            val request = Request.Builder()
                .url(directStreamUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("Server returned HTTP ${response.code}")
            }

            val body = response.body ?: throw IllegalStateException("Empty response body")
            val totalBytes = body.contentLength()
            val contentType = body.contentType()?.toString()?.lowercase() ?: "audio/mp4"

            val (extension, mimeType, audioFormat) = when {
                contentType.contains("webm") || contentType.contains("opus") -> Triple("opus", "audio/webm", AudioFormat.AAC_HQ)
                contentType.contains("mp4") || contentType.contains("m4a") -> Triple("m4a", "audio/mp4", AudioFormat.AAC_HQ)
                contentType.contains("mpeg") || contentType.contains("mp3") -> Triple("mp3", "audio/mpeg", AudioFormat.MP3)
                else -> Triple("m4a", "audio/mp4", AudioFormat.AAC_HQ)
            }

            val safeArtist = song.artist.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(40)
            val safeTitle = song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(50)
            val fileName = "${safeArtist} - ${safeTitle}_${song.id}.$extension"

            // 3. Write audio stream to destination (MediaStore on Android 10+, fallback to app files)
            var lastProgressUpdate = 0
            val fileUri = writeToStorage(
                fileName = fileName,
                song = song,
                mimeType = mimeType,
                inputStream = body.byteStream(),
                totalBytes = totalBytes,
                onProgress = { percent ->
                    if (percent - lastProgressUpdate >= 5 || percent == 100) {
                        lastProgressUpdate = percent
                        val frac = percent / 100f
                        _downloadStates.update { it + (song.id to DownloadState.Downloading(frac)) }
                        showProgressNotification(notificationId, song.title, song.artist, percent)
                    }
                }
            )

            // 4. Download and cache album art locally for offline viewing
            val localArtUri = cacheAlbumArt(song)

            // 5. Index into Room database for instant local library visibility
            val songEntity = SongEntity(
                id = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album.ifBlank { "MoodTunes Downloads" },
                duration = song.duration,
                uriString = fileUri.toString(),
                albumArtUriString = localArtUri?.toString() ?: song.albumArtUri?.toString(),
                genre = song.genre ?: "YouTube Music",
                isFavorite = song.isFavorite,
                moodTagsJson = JSONArray(song.moodTags.map { it.name }).toString(),
                audioFormatName = audioFormat.name,
                isStream = false,
                playCount = song.playCount,
                lastPlayedAt = song.lastPlayedAt
            )
            songDao.upsertSong(songEntity)

            // 6. Record completed state
            saveDownloadedSongId(song.id)
            _downloadStates.update { it + (song.id to DownloadState.Completed(fileUri)) }
            showCompletedNotification(notificationId, song.title)
            _userFeedback.tryEmit("Downloaded \"${song.title}\"")

        } catch (e: Exception) {
            Timber.e(e, "Download failed for song ${song.id} (${song.title})")
            _downloadStates.update { it + (song.id to DownloadState.Failed(e.message ?: "Download failed")) }
            showFailedNotification(notificationId, song.title, e.message ?: "Download error")
            _userFeedback.tryEmit("Download failed: ${e.message}")
        }
    }

    private fun writeToStorage(
        fileName: String,
        song: Song,
        mimeType: String,
        inputStream: InputStream,
        totalBytes: Long,
        onProgress: (Int) -> Unit
    ): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.TITLE, song.title)
                put(MediaStore.Audio.Media.ARTIST, song.artist)
                put(MediaStore.Audio.Media.ALBUM, song.album.ifBlank { "MoodTunes Downloads" })
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/MoodTunes")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
                if (song.duration > 0) {
                    put(MediaStore.Audio.Media.DURATION, song.duration)
                }
            }

            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val itemUri = context.contentResolver.insert(collection, contentValues)
                ?: throw IllegalStateException("Failed to insert MediaStore record")

            try {
                context.contentResolver.openOutputStream(itemUri)?.use { output ->
                    copyStreamWithProgress(inputStream, output, totalBytes, onProgress)
                } ?: throw IllegalStateException("Failed to open output stream for $itemUri")

                contentValues.clear()
                contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                context.contentResolver.update(itemUri, contentValues, null, null)
                itemUri
            } catch (e: Exception) {
                context.contentResolver.delete(itemUri, null, null)
                throw e
            }
        } else {
            // Legacy Android 9 and below fallback
            val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val dir = File(baseDir, "MoodTunes").apply { if (!exists()) mkdirs() }
            val targetFile = File(dir, fileName)

            targetFile.outputStream().use { output ->
                copyStreamWithProgress(inputStream, output, totalBytes, onProgress)
            }
            Uri.fromFile(targetFile)
        }
    }

    private fun copyStreamWithProgress(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long,
        onProgress: (Int) -> Unit
    ) {
        val buffer = ByteArray(8192)
        var bytesCopied: Long = 0
        var read: Int

        input.use { inStream ->
            output.use { outStream ->
                while (inStream.read(buffer).also { read = it } != -1) {
                    outStream.write(buffer, 0, read)
                    bytesCopied += read
                    if (totalBytes > 0) {
                        val percent = ((bytesCopied * 100) / totalBytes).toInt().coerceIn(0, 100)
                        onProgress(percent)
                    }
                }
                outStream.flush()
            }
        }
        onProgress(100)
    }

    private fun cacheAlbumArt(song: Song): Uri? {
        val artUri = song.albumArtUri ?: return null
        if (artUri.scheme == "file") return artUri

        return runCatching {
            val artDir = File(context.filesDir, "custom_artwork").apply { if (!exists()) mkdirs() }
            val artFile = File(artDir, "art_${song.id}.jpg")

            val request = Request.Builder().url(artUri.toString()).build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(artFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Uri.fromFile(artFile)
                } else null
            }
        }.getOrNull()
    }

    fun deleteDownloadedSong(song: Song) {
        scope.launch {
            try {
                if (song.uri.scheme == "content") {
                    context.contentResolver.delete(song.uri, null, null)
                } else if (song.uri.scheme == "file") {
                    val path = song.uri.path
                    if (path != null) {
                        val file = File(path)
                        if (file.exists()) file.delete()
                    }
                }
                // Also remove cached art
                val artFile = File(context.filesDir, "custom_artwork/art_${song.id}.jpg")
                if (artFile.exists()) artFile.delete()

                removeDownloadedSongId(song.id)
                _downloadStates.update { it + (song.id to DownloadState.Idle) }
                _userFeedback.tryEmit("Removed download for \"${song.title}\"")
            } catch (e: Exception) {
                Timber.w(e, "Error deleting downloaded song ${song.id}")
            }
        }
    }

    // ── Notifications ────────────────────────────────────────────────────────

    private fun showProgressNotification(id: Int, title: String, artist: String, progress: Int) {
        val builder = NotificationCompat.Builder(context, MoodTunesApp.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading $title")
            .setContentText(if (progress > 0) "$artist • $progress%" else artist)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        notificationManager.notify(id, builder.build())
    }

    private fun showCompletedNotification(id: Int, title: String) {
        val builder = NotificationCompat.Builder(context, MoodTunesApp.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Downloaded $title")
            .setContentText("Saved to Music/MoodTunes for offline listening")
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        notificationManager.notify(id, builder.build())
    }

    private fun showFailedNotification(id: Int, title: String, reason: String) {
        val builder = NotificationCompat.Builder(context, MoodTunesApp.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Failed to download $title")
            .setContentText(reason)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        notificationManager.notify(id, builder.build())
    }

    private fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
