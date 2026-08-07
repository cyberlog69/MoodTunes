package com.moodtunes.app.data.local.backup

import android.content.Context
import android.net.Uri
import com.moodtunes.app.data.local.db.SongJsonSerializer
import com.moodtunes.app.data.local.db.dao.SongDao
import com.moodtunes.app.data.local.db.entity.SongEntity
import com.moodtunes.app.domain.model.Playlist
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.repository.IPlaylistRepository
import com.moodtunes.app.domain.repository.IMusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class BackupImportResult(
    val favoritesImported: Int = 0,
    val playlistsImported: Int = 0
)

/**
 * Exports and imports user data (favorites + playlists) as a single JSON file
 * through the Storage Access Framework.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: IMusicRepository,
    private val playlistRepository: IPlaylistRepository,
    private val songDao: SongDao
) {

    suspend fun exportBackup(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val favorites = musicRepository.getFavoriteSongs().first()
            val playlists = playlistRepository.getPlaylists().first()

            val root = JSONObject().apply {
                put("version", 1)
                put("exportedAt", System.currentTimeMillis())
                put("favorites", JSONArray().apply {
                    favorites.forEach { put(JSONObject(SongJsonSerializer.serialize(it))) }
                })
                put("playlists", JSONArray().apply {
                    playlists.forEach { playlist ->
                        put(JSONObject().apply {
                            put("name", playlist.name)
                            put("createdAt", playlist.createdAt)
                            put("songs", JSONArray().apply {
                                playlist.songs.forEach { put(JSONObject(SongJsonSerializer.serialize(it))) }
                            })
                        })
                    }
                })
            }

            context.contentResolver.openOutputStream(uri, "w")?.use { out ->
                out.write(root.toString(2).toByteArray(Charsets.UTF_8))
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    suspend fun importBackup(uri: Uri): BackupImportResult = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: return@runCatching BackupImportResult()

            val root = JSONObject(text)
            val favoritesArr = root.optJSONArray("favorites") ?: JSONArray()
            val favorites = (0 until favoritesArr.length()).mapNotNull { i ->
                SongJsonSerializer.deserialize(favoritesArr.getJSONObject(i).toString())
            }

            val playlistsArr = root.optJSONArray("playlists") ?: JSONArray()
            val playlists = (0 until playlistsArr.length()).mapNotNull { i ->
                val obj = playlistsArr.getJSONObject(i)
                val songsArr = obj.optJSONArray("songs") ?: JSONArray()
                val songs = (0 until songsArr.length()).mapNotNull { j ->
                    SongJsonSerializer.deserialize(songsArr.getJSONObject(j).toString())
                }
                Playlist(
                    name = obj.optString("name", "Imported Playlist"),
                    moodType = null,
                    songs = songs,
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
            }

            favorites.forEach { song ->
                songDao.upsertSong(song.toFavoriteEntity())
            }

            playlists.forEach { playlist ->
                val playlistId = playlistRepository.createPlaylist(playlist.name)
                playlistRepository.replacePlaylistSongs(playlistId, playlist.songs)
            }

            BackupImportResult(
                favoritesImported = favorites.size,
                playlistsImported = playlists.size
            )
        }.getOrDefault(BackupImportResult())
    }

    private fun Song.toFavoriteEntity() = SongEntity(
        id = id,
        title = title,
        artist = artist,
        album = album,
        duration = duration,
        uriString = uri.toString(),
        albumArtUriString = albumArtUri?.toString(),
        genre = genre,
        isFavorite = true,
        moodTagsJson = moodTags.joinToString(",") { it.name },
        audioFormatName = audioFormat.name,
        isStream = isStream
    )
}
