package com.moodtunes.app.data.local.download

import android.app.NotificationManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
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
import com.moodtunes.app.data.local.db.entity.toDomain
import com.moodtunes.app.data.local.preferences.UserPreferencesRepository
import com.moodtunes.app.data.remote.OnlineStreamRepository
import com.moodtunes.app.domain.model.AudioFormat
import com.moodtunes.app.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val progress: Float) : DownloadState
    data class Completed(val fileUri: Uri) : DownloadState
    data class Failed(val reason: String) : DownloadState
}

private data class DownloadChunk(
    val index: Int,
    val start: Long,
    val end: Long
)

@Singleton
class SongDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val onlineStreamRepository: OnlineStreamRepository,
    private val songDao: SongDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    companion object {
        private const val YOUTUBE_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15"
        private const val CHUNK_SIZE = 512 * 1024L // 512 KB per burst chunk
        private const val CONCURRENCY = 3 // 3 concurrent Range workers
        private const val BUFFER_SIZE = 65536 // 64 KB read buffer
    }

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
     * Real-time reactive stream of downloaded songs directly from Room DB.
     * Automatically emits whenever a download completes or is deleted.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getDownloadedSongs(): Flow<List<Song>> {
        return _downloadedSongIds.flatMapLatest { ids ->
            songDao.getDownloadedSongsFlow(ids.toList()).map { entities ->
                entities.map { it.toDomain() }
            }
        }
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
        val tempFile = File(context.cacheDir, "dl_${song.id}_${System.currentTimeMillis()}.tmp")

        // Show starting notification
        showProgressNotification(notificationId, song.title, song.artist, 0)

        try {
            // 1. Resolve playable direct audio stream URL
            val directStreamUrl = onlineStreamRepository.resolveDirectStreamUrl(song.uri.toString())
            if (directStreamUrl.isBlank() || !directStreamUrl.startsWith("http") || directStreamUrl.contains("youtube.com/watch")) {
                throw IllegalStateException("Failed to resolve playable audio stream")
            }

            // 2. High-speed multi-chunk download into temporary file
            var lastProgressUpdate = 0
            val (mimeType, audioFormat) = downloadAudioStream(
                directStreamUrl = directStreamUrl,
                tempFile = tempFile,
                onProgress = { percent ->
                    if (percent - lastProgressUpdate >= 3 || percent == 100) {
                        lastProgressUpdate = percent
                        val frac = percent / 100f
                        _downloadStates.update { it + (song.id to DownloadState.Downloading(frac)) }
                        showProgressNotification(notificationId, song.title, song.artist, percent)
                    }
                }
            )

            val extension = if (mimeType.contains("webm") || mimeType.contains("opus")) "opus" else "m4a"
            val safeArtist = song.artist.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(40)
            val safeTitle = song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(50)
            val fileName = "${safeArtist} - ${safeTitle}_${song.id}.$extension"

            // 3. Write tempFile to destination (MediaStore on Android 10+, fallback to public Music directory)
            val fileUri = writeTempFileToStorage(
                tempFile = tempFile,
                fileName = fileName,
                song = song,
                mimeType = mimeType
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
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    /**
     * Downloads an audio stream at high speed using multi-chunk HTTP Range requests when supported,
     * with graceful fallback to single-stream downloading if Range headers are not supported.
     */
    private suspend fun downloadAudioStream(
        directStreamUrl: String,
        tempFile: File,
        onProgress: (Int) -> Unit
    ): Pair<String, AudioFormat> {
        val (totalBytes, contentType, supportsRange) = probeStream(directStreamUrl)

        val audioFormat = when {
            contentType.contains("webm") || contentType.contains("opus") -> AudioFormat.AAC_HQ
            contentType.contains("mp4") || contentType.contains("m4a") -> AudioFormat.AAC_HQ
            contentType.contains("mpeg") || contentType.contains("mp3") -> AudioFormat.MP3
            else -> AudioFormat.AAC_HQ
        }

        if (supportsRange && totalBytes > 0) {
            downloadParallelChunks(directStreamUrl, tempFile, totalBytes, onProgress)
        } else {
            downloadSequentialToTemp(directStreamUrl, tempFile, totalBytes, onProgress)
        }

        return Pair(contentType, audioFormat)
    }

    private fun probeStream(url: String): Triple<Long, String, Boolean> {
        val clenFromUrl = Regex("[?&]clen=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()

        val probeReq = Request.Builder()
            .url(url)
            .addHeader("User-Agent", YOUTUBE_USER_AGENT)
            .addHeader("Origin", "https://www.youtube.com")
            .addHeader("Referer", "https://www.youtube.com/")
            .addHeader("Range", "bytes=0-0")
            .build()

        return try {
            httpClient.newCall(probeReq).execute().use { resp ->
                val contentType = resp.header("Content-Type")?.lowercase() ?: "audio/mp4"
                if (resp.code == 206) {
                    val contentRange = resp.header("Content-Range")
                    val totalFromRange = contentRange?.substringAfterLast('/')?.trim()?.toLongOrNull()
                    val total = totalFromRange ?: clenFromUrl ?: 0L
                    Triple(total, contentType, total > 0)
                } else if (resp.isSuccessful) {
                    val len = resp.body?.contentLength() ?: clenFromUrl ?: 0L
                    Triple(len, contentType, false)
                } else {
                    Triple(clenFromUrl ?: 0L, contentType, false)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Probe failed for stream $url")
            Triple(clenFromUrl ?: 0L, "audio/mp4", false)
        }
    }

    private suspend fun downloadParallelChunks(
        url: String,
        tempFile: File,
        totalBytes: Long,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val raf = RandomAccessFile(tempFile, "rw")
        raf.setLength(totalBytes)
        val fileChannel = raf.channel

        val chunks = mutableListOf<DownloadChunk>()
        var offset = 0L
        var chunkIdx = 0
        while (offset < totalBytes) {
            val end = (offset + CHUNK_SIZE - 1).coerceAtMost(totalBytes - 1)
            chunks.add(DownloadChunk(chunkIdx++, offset, end))
            offset = end + 1
        }

        val channel = Channel<DownloadChunk>(Channel.UNLIMITED)
        chunks.forEach { channel.trySend(it) }
        channel.close()

        val bytesCopied = AtomicLong(0)
        val lastProgress = AtomicInteger(0)

        try {
            coroutineScope {
                val workers = List(CONCURRENCY) {
                    launch {
                        for (chunk in channel) {
                            downloadSingleChunk(
                                url = url,
                                fileChannel = fileChannel,
                                chunk = chunk,
                                bytesCopied = bytesCopied,
                                totalBytes = totalBytes,
                                lastProgress = lastProgress,
                                onProgress = onProgress
                            )
                        }
                    }
                }
                workers.joinAll()
            }
            fileChannel.force(true)
            onProgress(100)
        } finally {
            runCatching { fileChannel.close() }
            runCatching { raf.close() }
        }
    }

    private suspend fun downloadSingleChunk(
        url: String,
        fileChannel: FileChannel,
        chunk: DownloadChunk,
        bytesCopied: AtomicLong,
        totalBytes: Long,
        lastProgress: AtomicInteger,
        onProgress: (Int) -> Unit
    ) {
        var attempt = 0
        var success = false
        var lastException: Exception? = null

        while (attempt < 3 && !success) {
            attempt++
            try {
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", YOUTUBE_USER_AGENT)
                    .addHeader("Origin", "https://www.youtube.com")
                    .addHeader("Referer", "https://www.youtube.com/")
                    .addHeader("Range", "bytes=${chunk.start}-${chunk.end}")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful && response.code != 206) {
                        throw IOException("HTTP ${response.code} on chunk ${chunk.index}")
                    }
                    val body = response.body ?: throw IOException("Empty body on chunk ${chunk.index}")
                    body.byteStream().use { inStream ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var read: Int
                        var chunkWritten = 0L
                        var currentPos = chunk.start

                        while (inStream.read(buffer).also { read = it } != -1) {
                            val byteBuf = ByteBuffer.wrap(buffer, 0, read)
                            while (byteBuf.hasRemaining()) {
                                val written = fileChannel.write(byteBuf, currentPos)
                                currentPos += written
                                chunkWritten += written
                            }
                            val currentTotal = bytesCopied.addAndGet(read.toLong())
                            val percent = ((currentTotal * 100) / totalBytes).toInt().coerceIn(0, 100)
                            val prev = lastProgress.get()
                            if (percent - prev >= 3 || percent == 100) {
                                if (lastProgress.compareAndSet(prev, percent)) {
                                    onProgress(percent)
                                }
                            }
                        }

                        val expectedBytes = chunk.end - chunk.start + 1
                        if (chunkWritten != expectedBytes) {
                            throw IOException("Chunk ${chunk.index} wrote $chunkWritten bytes, expected $expectedBytes")
                        }
                    }
                }
                success = true
            } catch (e: Exception) {
                lastException = e
                delay(100L * attempt)
            }
        }

        if (!success) {
            throw (lastException ?: IOException("Failed to download chunk ${chunk.index} after 3 attempts"))
        }
    }

    private fun downloadSequentialToTemp(
        url: String,
        tempFile: File,
        totalBytes: Long,
        onProgress: (Int) -> Unit
    ) {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", YOUTUBE_USER_AGENT)
            .addHeader("Origin", "https://www.youtube.com")
            .addHeader("Referer", "https://www.youtube.com/")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} downloading stream")
            }
            val body = response.body ?: throw IOException("Empty stream body")
            val effectiveTotal = if (totalBytes > 0) totalBytes else body.contentLength()
            body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesCopied = 0L
                    var read: Int
                    var lastProg = 0
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        if (effectiveTotal > 0) {
                            val percent = ((bytesCopied * 100) / effectiveTotal).toInt().coerceIn(0, 100)
                            if (percent - lastProg >= 3 || percent == 100) {
                                lastProg = percent
                                onProgress(percent)
                            }
                        }
                    }
                    output.flush()
                }
            }
        }
        onProgress(100)
    }

    private fun writeTempFileToStorage(
        tempFile: File,
        fileName: String,
        song: Song,
        mimeType: String
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
                    tempFile.inputStream().use { input ->
                        input.copyTo(output, bufferSize = BUFFER_SIZE)
                    }
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
            val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val dir = File(baseDir, "MoodTunes").apply { if (!exists()) mkdirs() }
            val targetFile = File(dir, fileName)

            tempFile.copyTo(targetFile, overwrite = true)
            MediaScannerConnection.scanFile(
                context,
                arrayOf(targetFile.absolutePath),
                arrayOf(mimeType),
                null
            )
            Uri.fromFile(targetFile)
        }
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

                // Remove from Room DB
                songDao.deleteSongById(song.id)

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
