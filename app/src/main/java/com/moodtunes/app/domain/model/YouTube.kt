package com.moodtunes.app.domain.model

import android.net.Uri

/**
 * A row from YouTube Music's browse graph. Unlike [Song] this models the
 * catalogue itself: albums, artists, playlists and mood/genre cards can be
 * opened as pages, not just played.
 */
sealed interface YouTubeItem {
    val id: String
    val title: String
    val subtitle: String
    val thumbnail: Uri?

    data class Track(val song: Song) : YouTubeItem {
        override val id: String = song.videoId ?: song.id.toString()
        override val title: String = song.title
        override val subtitle: String = buildString {
            append(song.artist)
            if (song.album.isNotBlank() && song.album != "YouTube Music") {
                append(" • ").append(song.album)
            }
        }
        override val thumbnail: Uri? = song.albumArtUri
    }

    data class Album(
        val browseId: String,
        override val title: String,
        override val subtitle: String,
        override val thumbnail: Uri?
    ) : YouTubeItem {
        override val id: String = browseId
    }

    data class Artist(
        val browseId: String,
        override val title: String,
        override val subtitle: String,
        override val thumbnail: Uri?
    ) : YouTubeItem {
        override val id: String = browseId
    }

    data class Playlist(
        val browseId: String,
        override val title: String,
        override val subtitle: String,
        override val thumbnail: Uri?
    ) : YouTubeItem {
        override val id: String = browseId
    }

    /** A mood/genre card whose children are behind [browseId] + [params]. */
    data class Category(
        val browseId: String,
        val params: String?,
        override val title: String,
        override val subtitle: String,
        override val thumbnail: Uri?,
        val color: Long? = null
    ) : YouTubeItem {
        override val id: String = "$browseId:$params"
    }
}

data class YouTubeShelf(
    val title: String,
    val subtitle: String? = null,
    val items: List<YouTubeItem> = emptyList()
) {
    val playableSongs: List<Song>
        get() = items.mapNotNull { (it as? YouTubeItem.Track)?.song }
}

/** A browsable page: album, artist, playlist, chart or mood category. */
data class YouTubePage(
    val browseId: String,
    val title: String,
    val subtitle: String? = null,
    val thumbnail: Uri? = null,
    val shelves: List<YouTubeShelf> = emptyList(),
    val songs: List<Song> = emptyList(),
    val continuation: String? = null,
    val isAlbum: Boolean = false,
    val isArtist: Boolean = false,
    val isPlaylist: Boolean = false
) {
    val isEmpty: Boolean get() = shelves.isEmpty() && songs.isEmpty()
}

data class YouTubeSearchSection(
    val filter: YouTubeSearchFilter,
    val items: List<YouTubeItem>
)

data class YouTubeSearchPage(
    val query: String = "",
    val top: YouTubeItem? = null,
    val sections: List<YouTubeSearchSection> = emptyList(),
    val continuation: String? = null
) {
    val isEmpty: Boolean get() = top == null && sections.all { it.items.isEmpty() }

    fun allItems(): List<YouTubeItem> =
        (listOfNotNull(top) + sections.flatMap { it.items }).distinctBy { it.id }
}

/**
 * Search filters use YouTube Music's own opaque params so each kind is asked
 * for separately instead of relying on the mixed "top result" order.
 */
enum class YouTubeSearchFilter(val label: String, val params: String?) {
    ALL("All", null),
    SONGS("Songs", "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"),
    ALBUMS("Albums", "EgWKAQIYAWoMEA4QChADEAQQCRAF"),
    ARTISTS("Artists", "EgWKAQIgAWoMEA4QChADEAQQCRAF"),
    PLAYLISTS("Playlists", "EgeKAQQoAEABagwQDhAKEAMQBBAJEAUA"),
    FEATURED_PLAYLISTS("Featured", "EgeKAQQoADgBagwQDhAKEAMQBBAJEAU%3D"),
    VIDEOS("Videos", "EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D")
}

data class YouTubeRadioPage(
    val songs: List<Song>,
    val continuation: String? = null,
    val playlistId: String? = null
)
