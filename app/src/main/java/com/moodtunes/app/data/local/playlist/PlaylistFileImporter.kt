package com.moodtunes.app.data.local.playlist

import com.moodtunes.app.data.remote.api.YouTubeMusicApiService
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.usecase.AddSongToPlaylistUseCase
import com.moodtunes.app.domain.usecase.CreatePlaylistUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

data class ImportedPlaylistResult(
    val playlistId: Long,
    val playlistName: String,
    val trackCount: Int
)

@Singleton
class PlaylistFileImporter @Inject constructor(
    private val createPlaylistUseCase: CreatePlaylistUseCase,
    private val addSongToPlaylistUseCase: AddSongToPlaylistUseCase,
    private val youTubeMusicApiService: YouTubeMusicApiService
) {

    suspend fun importPlaylistFromM3U(
        name: String,
        inputStream: InputStream
    ): ImportedPlaylistResult = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<String>()
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            var line = reader.readLine()
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.startsWith("#EXTINF:")) {
                    // #EXTINF:123,Artist - Title
                    val info = trimmed.substringAfter(",")
                    if (info.isNotBlank()) {
                        tracks.add(info)
                    }
                } else if (!trimmed.startsWith("#") && trimmed.isNotBlank() && !trimmed.startsWith("http")) {
                    // Local filename fallback
                    val title = trimmed.substringAfterLast("/").substringAfterLast("\\").substringBeforeLast(".")
                    if (title.isNotBlank()) {
                        tracks.add(title)
                    }
                }
                line = reader.readLine()
            }
        }

        val playlistId = createPlaylistUseCase(name)
        var addedCount = 0

        for (trackQuery in tracks.take(50)) {
            val results = runCatching { youTubeMusicApiService.searchSongs(trackQuery, limit = 1) }.getOrDefault(emptyList())
            if (results.isNotEmpty()) {
                addSongToPlaylistUseCase(playlistId, results.first())
                addedCount++
            }
        }

        ImportedPlaylistResult(
            playlistId = playlistId,
            playlistName = name,
            trackCount = addedCount
        )
    }

    fun exportPlaylistToM3U(name: String, songs: List<Song>): String {
        val sb = StringBuilder()
        sb.appendLine("#EXTM3U")
        sb.appendLine("#PLAYLIST:$name")
        for (song in songs) {
            val durationSec = song.duration / 1000
            sb.appendLine("#EXTINF:$durationSec,${song.artist} - ${song.title}")
            sb.appendLine(song.uri.toString())
        }
        return sb.toString()
    }
}

