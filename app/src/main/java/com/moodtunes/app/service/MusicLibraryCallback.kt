package com.moodtunes.app.service

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.repository.IMusicRepository
import com.moodtunes.app.domain.repository.IPlaylistRepository
import com.moodtunes.app.data.remote.OnlineStreamRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Media3 library callback that powers Android Auto (and any other media browser).
 * Exposes the app's moods, playlists, favorites, and all songs as a browsable tree.
 */
class MusicLibraryCallback(
    private val musicRepository: IMusicRepository,
    private val playlistRepository: IPlaylistRepository,
    private val onlineStreamRepository: OnlineStreamRepository,
    private val scope: CoroutineScope
) : MediaLibraryService.MediaLibrarySession.Callback {

    companion object {
        const val ROOT_ID = "root"
        const val MOODS_ID = "moods"
        const val ALL_SONGS_ID = "all_songs"
        const val FAVORITES_ID = "favorites"
        const val MOOD_PREFIX = "mood:"
        const val PLAYLIST_PREFIX = "playlist:"
        private const val MAX_CHILDREN = 100
    }

    override fun onGetLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession,
        controller: MediaSession.ControllerInfo,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val future = SettableFuture.create<LibraryResult<MediaItem>>()
        val root = MediaItem.Builder()
            .setMediaId(ROOT_ID)
            .setMediaMetadata(MediaMetadata.Builder().setTitle("MoodTunes").build())
            .build()
        future.set(LibraryResult.ofItem(root, params))
        return future
    }

    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession,
        controller: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        scope.launch {
            try {
                val children = when (parentId) {
                    ROOT_ID -> rootChildren()
                    MOODS_ID -> moodFolders()
                    ALL_SONGS_ID -> songsToMediaItems(musicRepository.getAllSongs())
                    FAVORITES_ID -> songsToMediaItems(musicRepository.getFavoriteSongs().first())
                    else -> when {
                        parentId.startsWith(MOOD_PREFIX) ->
                            songsToMediaItems(moodSongs(parentId.removePrefix(MOOD_PREFIX)))
                        parentId.startsWith(PLAYLIST_PREFIX) ->
                            songsToMediaItems(playlistSongs(parentId.removePrefix(PLAYLIST_PREFIX)))
                        else -> error("Unknown parent id: $parentId")
                    }
                }
                val paged = children.take(pageSize.coerceAtLeast(MAX_CHILDREN))
                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(paged), params))
            } catch (e: Exception) {
                future.set(LibraryResult.ofError(PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
            }
        }
        return future
    }

    override fun onAddMediaItems(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        val future = SettableFuture.create<List<MediaItem>>()
        scope.launch {
            try {
                val resolved = mutableListOf<MediaItem>()
                for (item in mediaItems) {
                    resolved += resolveMediaItem(item)
                }
                future.set(resolved)
            } catch (e: Exception) {
                future.set(emptyList())
            }
        }
        return future
    }

    // ── Hierarchy building ───────────────────────────────────────────────────

    private suspend fun rootChildren(): List<MediaItem> {
        val children = mutableListOf<MediaItem>()
        children += folder(MOODS_ID, "Moods", "Browse by mood")
        children += folder(ALL_SONGS_ID, "All Songs", null)
        children += folder(FAVORITES_ID, "Favorites", null)
        playlistRepository.getPlaylists().first().forEach { playlist ->
            children += folder("$PLAYLIST_PREFIX${playlist.id}", playlist.name, "${playlist.songCount} songs")
        }
        return children
    }

    private fun moodFolders(): List<MediaItem> = MoodType.entries.map { mood ->
        folder("$MOOD_PREFIX${mood.name}", "${mood.emoji} ${mood.displayName}", mood.description)
    }

    private fun folder(mediaId: String, title: String, subtitle: String?): MediaItem =
        MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()

    private fun songsToMediaItems(songs: List<Song>): List<MediaItem> =
        songs.map { MediaItemFactory.songToMediaItem(it) }

    private suspend fun moodSongs(name: String): List<Song> {
        val mood = MoodType.entries.firstOrNull { it.name == name } ?: return emptyList()
        return musicRepository.getSongsByMood(mood)
    }

    private suspend fun playlistSongs(idStr: String): List<Song> {
        val id = idStr.toLongOrNull() ?: return emptyList()
        return playlistRepository.getPlaylists().first().firstOrNull { it.id == id }?.songs ?: emptyList()
    }

    private suspend fun resolveMediaItem(item: MediaItem): List<MediaItem> {
        val mediaId = item.mediaId
        return when {
            mediaId == ALL_SONGS_ID -> songsToMediaItems(musicRepository.getAllSongs())
            mediaId == FAVORITES_ID -> songsToMediaItems(musicRepository.getFavoriteSongs().first())
            mediaId.startsWith(MOOD_PREFIX) -> songsToMediaItems(moodSongs(mediaId.removePrefix(MOOD_PREFIX)))
            mediaId.startsWith(PLAYLIST_PREFIX) -> songsToMediaItems(playlistSongs(mediaId.removePrefix(PLAYLIST_PREFIX)))
            // Already a concrete, playable leaf item (has a real uri).
            else -> {
                val uri = item.localConfiguration?.uri
                if (uri != null) listOf(item) else emptyList()
            }
        }
    }
}
