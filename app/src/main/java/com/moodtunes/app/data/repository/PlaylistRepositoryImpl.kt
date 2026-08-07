package com.moodtunes.app.data.repository

import com.moodtunes.app.data.local.db.SongJsonSerializer
import com.moodtunes.app.data.local.db.dao.PlaylistDao
import com.moodtunes.app.data.local.db.entity.PlaylistEntity
import com.moodtunes.app.data.local.db.entity.PlaylistSongEntity
import com.moodtunes.app.domain.model.Playlist
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.repository.IPlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao
) : IPlaylistRepository {

    override fun getPlaylists(): Flow<List<Playlist>> =
        playlistDao.getPlaylists().combine(playlistDao.getAllPlaylistSongs()) { playlists, entries ->
            playlists.map { playlist ->
                val songs = entries
                    .filter { it.playlistId == playlist.id }
                    .sortedBy { it.position }
                    .mapNotNull { SongJsonSerializer.deserialize(it.songJson) }
                Playlist(
                    id = playlist.id,
                    name = playlist.name,
                    moodType = null,
                    songs = songs,
                    createdAt = playlist.createdAt
                )
            }
        }

    override fun getPlaylist(playlistId: Long): Flow<Playlist?> =
        playlistDao.getPlaylists().map { playlists ->
            playlists.firstOrNull { it.id == playlistId }
        }.combine(playlistDao.getSongsForPlaylist(playlistId)) { playlist, entries ->
            playlist?.let {
                Playlist(
                    id = it.id,
                    name = it.name,
                    moodType = null,
                    songs = entries.sortedBy { e -> e.position }
                        .mapNotNull { SongJsonSerializer.deserialize(it.songJson) },
                    createdAt = it.createdAt
                )
            }
        }

    override suspend fun createPlaylist(name: String): Long =
        playlistDao.insertPlaylist(PlaylistEntity(name = name))

    override suspend fun renamePlaylist(playlistId: Long, name: String) {
        val existing = playlistDao.getPlaylist(playlistId) ?: return
        playlistDao.updatePlaylist(existing.copy(name = name))
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        val existing = playlistDao.getPlaylist(playlistId) ?: return
        playlistDao.deletePlaylist(existing)
    }

    override suspend fun addSongToPlaylist(playlistId: Long, song: Song) {
        val existing = playlistDao.getSongEntryBySongId(playlistId, song.id)
        if (existing != null) return // already present; avoid duplicates
        val nextPosition = playlistDao.maxPosition(playlistId) + 1
        playlistDao.insertSong(
            PlaylistSongEntity(
                playlistId = playlistId,
                songId = song.id,
                position = nextPosition,
                songJson = SongJsonSerializer.serialize(song)
            )
        )
    }

    override suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        playlistDao.deleteSongBySongId(playlistId, songId)
    }

    override suspend fun moveSong(playlistId: Long, fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) return
        val entries = playlistDao.getSongsForPlaylistSync(playlistId)
            .sortedBy { it.position }
        if (fromPosition !in entries.indices || toPosition !in entries.indices) return

        val reordered = entries.toMutableList().apply {
            val item = removeAt(fromPosition)
            add(toPosition, item)
        }
        reordered.forEachIndexed { index, entry ->
            playlistDao.updateSong(entry.copy(position = index))
        }
    }

    override suspend fun replacePlaylistSongs(playlistId: Long, songs: List<Song>) {
        playlistDao.clearSongs(playlistId)
        songs.forEachIndexed { index, song ->
            playlistDao.insertSong(
                PlaylistSongEntity(
                    playlistId = playlistId,
                    songId = song.id,
                    position = index,
                    songJson = SongJsonSerializer.serialize(song)
                )
            )
        }
    }
}
