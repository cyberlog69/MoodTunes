package com.moodtunes.app.service

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.repository.IMusicRepository
import com.moodtunes.app.domain.repository.IPlaylistRepository
import com.moodtunes.app.data.remote.OnlineStreamRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Media3 library callback that powers Android Auto (and any other media browser).
 * Exposes favorites and local playlists of YouTube Music tracks.
 */
@OptIn(UnstableApi::class)
class MusicLibraryCallback(
    private val musicRepository: IMusicRepository,
    private val playlistRepository: IPlaylistRepository,
    private val onlineStreamRepository: OnlineStreamRepository,
    private val scope: CoroutineScope
) : MediaLibraryService.MediaLibrarySession.Callback {

    companion object {
        const val ROOT_ID = "root"
        const val FAVORITES_ID = "favorites"
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
                    FAVORITES_ID -> songsToMediaItems(musicRepository.getFavoriteSongs().first())
                    else -> when {
                        parentId.startsWith(PLAYLIST_PREFIX) ->
                            songsToMediaItems(playlistSongs(parentId.removePrefix(PLAYLIST_PREFIX)))
                        else -> error("Unknown parent id: $parentId")
                    }
                }
                val paged = children.take(pageSize.coerceAtLeast(MAX_CHILDREN))
                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(paged), params))
            } catch (e: Exception) {
                future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
            }
        }
        return future
    }

    override fun onAddMediaItems(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        // Assistant / Android Auto "play <query>" arrives as a media item with
        // a search query instead of a concrete media id.
        val searchQuery = mediaItems.firstOrNull()?.requestMetadata?.searchQuery
        if (!searchQuery.isNullOrBlank()) {
            val searchFuture = SettableFuture.create<List<MediaItem>>()
            scope.launch {
                try {
                    searchFuture.set(
                        songsToMediaItems(
                            onlineStreamRepository.searchSongs(searchQuery.toString(), MAX_CHILDREN)
                        )
                    )
                } catch (e: Exception) {
                    searchFuture.set(emptyList())
                }
            }
            return searchFuture
        }

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
        children += folder(FAVORITES_ID, "Favorites", null)
        playlistRepository.getPlaylists().first().forEach { playlist ->
            children += folder("$PLAYLIST_PREFIX${playlist.id}", playlist.name, "${playlist.songCount} songs")
        }
        return children
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

    private suspend fun playlistSongs(idStr: String): List<Song> {
        val id = idStr.toLongOrNull() ?: return emptyList()
        return playlistRepository.getPlaylists().first().firstOrNull { it.id == id }?.songs ?: emptyList()
    }

    private suspend fun resolveMediaItem(item: MediaItem): List<MediaItem> {
        val mediaId = item.mediaId
        return when {
            mediaId == FAVORITES_ID -> songsToMediaItems(musicRepository.getFavoriteSongs().first())
            mediaId.startsWith(PLAYLIST_PREFIX) -> songsToMediaItems(playlistSongs(mediaId.removePrefix(PLAYLIST_PREFIX)))
            // Already a concrete, playable leaf item (has a real uri).
            else -> {
                val uri = item.localConfiguration?.uri
                if (uri != null) {
                    val uriStr = uri.toString()
                    val cached = if (uriStr.contains("youtube.com/watch") || uriStr.contains("youtu.be")) {
                        onlineStreamRepository.getCachedStreamUrl(uriStr)
                    } else null
                    if (cached != null) {
                        listOf(item.buildUpon().setUri(android.net.Uri.parse(cached)).build())
                    } else {
                        listOf(item)
                    }
                } else emptyList()
            }
        }
    }
}
