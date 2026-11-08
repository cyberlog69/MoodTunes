package com.moodtunes.app.data.remote.api

import android.net.Uri
import com.moodtunes.app.domain.model.AudioFormat
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.domain.model.YouTubeItem
import com.moodtunes.app.domain.model.YouTubePage
import com.moodtunes.app.domain.model.YouTubeRadioPage
import com.moodtunes.app.domain.model.YouTubeSearchFilter
import com.moodtunes.app.domain.model.YouTubeSearchPage
import com.moodtunes.app.domain.model.YouTubeSearchSection
import com.moodtunes.app.domain.model.YouTubeShelf
import org.json.JSONObject
import java.util.Locale

/**
 * Parses YouTube Music browse/search/next payloads into [YouTubeItem]s.
 *
 * Renderers are read defensively: the same row can arrive as a two-row item,
 * a responsive list item, a navigation button or a card depending on the page.
 */
internal object YouTubeParser {

    private val durationRegex = Regex("""^\d{1,2}(?::\d{2}){1,2}$""")
    private val yearRegex = Regex("""^(19|20)\d{2}$""")
    private val playsRegex = Regex("""[\d.,]+\s*[KMB]?\s*(plays|views)""", RegexOption.IGNORE_CASE)

    private const val PAGE_ALBUM = "MUSIC_PAGE_TYPE_ALBUM"
    private const val PAGE_ARTIST = "MUSIC_PAGE_TYPE_ARTIST"
    private const val PAGE_LIBRARY_ARTIST = "MUSIC_PAGE_TYPE_LIBRARY_ARTIST"
    private const val PAGE_PLAYLIST = "MUSIC_PAGE_TYPE_PLAYLIST"
    private const val PAGE_AUDIO_PLAYLIST = "MUSIC_PAGE_TYPE_AUDIO_PLAYLIST"
    private const val PAGE_PODCAST = "MUSIC_PAGE_TYPE_PODCAST"

    // ── Section discovery ────────────────────────────────────────────────────

    fun browseSections(root: JSONObject): List<JSONObject> {
        val lists = root.findAll("sectionListRenderer")
        for (list in lists) {
            val sections = list.arr("contents")?.objects().orEmpty()
            if (sections.isNotEmpty()) return sections
        }
        return emptyList()
    }

    // ── Shelves ──────────────────────────────────────────────────────────────

    fun shelves(root: JSONObject): List<YouTubeShelf> =
        browseSections(root).mapNotNull(::shelf).filter { it.items.isNotEmpty() }

    fun shelf(node: JSONObject): YouTubeShelf? {
        node.optJSONObject("musicCarouselShelfRenderer")?.let { carousel ->
            val title = carousel.obj("header", "musicCarouselShelfBasicHeaderRenderer", "title")?.text()
                ?: carousel.obj("header", "musicCarouselShelfBasicHeaderRenderer", "strapline")?.text()
                ?: ""
            val items = carousel.arr("contents").objects().mapNotNull(::item)
            if (items.isEmpty()) return null
            return YouTubeShelf(title = title, items = items)
        }
        node.optJSONObject("musicShelfRenderer")?.let { shelf ->
            val title = shelf.obj("title")?.text() ?: ""
            val subtitle = shelf.obj("subtitle")?.text()
            val items = shelf.arr("contents").objects().mapNotNull(::item)
            if (items.isEmpty() && title.isBlank()) return null
            return YouTubeShelf(title = title, subtitle = subtitle, items = items)
        }
        node.optJSONObject("musicPlaylistShelfRenderer")?.let { shelf ->
            val items = shelf.arr("contents").objects().mapNotNull(::item)
            if (items.isEmpty()) return null
            return YouTubeShelf(title = "", items = items)
        }
        node.optJSONObject("gridRenderer")?.let { grid ->
            val items = grid.arr("items").objects().mapNotNull(::item)
            if (items.isEmpty()) return null
            return YouTubeShelf(title = "", items = items)
        }
        return null
    }

    // ── Rows ─────────────────────────────────────────────────────────────────

    fun item(node: JSONObject): YouTubeItem? {
        node.optJSONObject("musicResponsiveListItemRenderer")?.let { return responsive(it) }
        node.optJSONObject("musicTwoRowItemRenderer")?.let { return twoRow(it) }
        node.optJSONObject("musicNavigationButtonRenderer")?.let { return category(it) }
        node.optJSONObject("musicCardShelfRenderer")?.let { return card(it) }
        node.optJSONObject("musicMultiRowListItemRenderer")?.let { return responsive(it) }
        return null
    }

    private fun responsive(row: JSONObject): YouTubeItem? {
        val columns = row.arr("flexColumns").objects()
        val title = columns.getOrNull(0)
            ?.obj("musicResponsiveListItemFlexColumnRenderer", "text")
            ?.text()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val subtitleRuns = columns.drop(1).flatMap { column ->
            column.obj("musicResponsiveListItemFlexColumnRenderer", "text")
                ?.arr("runs")
                .objects()
        }
        val subtitle = columns.drop(1)
            .mapNotNull { it.obj("musicResponsiveListItemFlexColumnRenderer", "text")?.text() }
            .joinToString(" • ")

        val thumbnail = upscaleArtwork(row.bestThumbnail())
        val watch = row.obj(
            "overlay", "musicItemThumbnailOverlayRenderer", "content",
            "musicPlayButtonRenderer", "playNavigationEndpoint", "watchEndpoint"
        ) ?: row.obj("navigationEndpoint", "watchEndpoint")
        val videoId = row.obj("playlistItemData")?.optString("videoId")
            ?.takeIf { it.isNotBlank() }
            ?: watch?.optString("videoId")?.takeIf { it.isNotBlank() }

        val browseId = row.obj("navigationEndpoint", "browseEndpoint")
            ?.optString("browseId")?.takeIf { it.isNotBlank() }

        val explicit = isExplicit(row)
        val videoType = watch?.obj(
            "watchEndpointMusicSupportedConfigs", "watchEndpointMusicConfig"
        )?.optString("musicVideoType")?.takeIf { it.isNotBlank() }
        // Playlist/album rows keep the duration in a fixed column, not the
        // subtitle runs, so read it before falling back to segment parsing.
        val fixedDuration = row.arr("fixedColumns").objects().firstNotNullOfOrNull { column ->
            parseDurationMs(
                column.obj("musicResponsiveListItemFixedColumnRenderer", "text")?.text()
            ).takeIf { it > 0L }
        } ?: 0L

        if (videoId != null) {
            return YouTubeItem.Track(
                songFrom(
                    videoId = videoId,
                    title = title,
                    subtitle = subtitle,
                    subtitleRuns = subtitleRuns,
                    thumbnail = thumbnail,
                    explicit = explicit,
                    videoType = videoType,
                    browseEndpoint = row.obj("navigationEndpoint", "browseEndpoint"),
                    durationOverride = fixedDuration
                )
            )
        }
        if (browseId != null) {
            return browseItem(
                browseId = browseId,
                title = title,
                subtitle = subtitle,
                thumbnail = thumbnail,
                pageType = row.obj("navigationEndpoint").pageType()
            )
        }
        return null
    }

    private fun twoRow(row: JSONObject): YouTubeItem? {
        val title = row.obj("title")?.text()?.takeIf { it.isNotBlank() } ?: return null
        val subtitle = row.obj("subtitle")?.text().orEmpty()
        val subtitleRuns = row.obj("subtitle")?.arr("runs").objects()
        val thumbnail = upscaleArtwork(row.bestThumbnail())
        val navigation = row.obj("navigationEndpoint")

        val watch = navigation?.obj("watchEndpoint")
        val videoId = watch?.optString("videoId")?.takeIf { it.isNotBlank() }
        if (videoId != null) {
            val videoType = watch.obj(
                "watchEndpointMusicSupportedConfigs", "watchEndpointMusicConfig"
            )?.optString("musicVideoType")?.takeIf { it.isNotBlank() }
            return YouTubeItem.Track(
                songFrom(
                    videoId = videoId,
                    title = title,
                    subtitle = subtitle,
                    subtitleRuns = subtitleRuns,
                    thumbnail = thumbnail,
                    explicit = isExplicit(row),
                    videoType = videoType,
                    browseEndpoint = null
                )
            )
        }

        val browseEndpoint = navigation?.obj("browseEndpoint")
        val browseId = browseEndpoint?.optString("browseId")?.takeIf { it.isNotBlank() } ?: return null
        return browseItem(
            browseId = browseId,
            title = title,
            subtitle = subtitle,
            thumbnail = thumbnail,
            pageType = navigation.pageType()
        )
    }

    private fun category(row: JSONObject): YouTubeItem? {
        val title = row.obj("buttonText")?.text()?.takeIf { it.isNotBlank() } ?: return null
        val endpoint = row.obj("clickCommand", "browseEndpoint") ?: return null
        val browseId = endpoint.optString("browseId").takeIf { it.isNotBlank() } ?: return null
        val params = endpoint.optString("params").takeIf { it.isNotBlank() }
        val color = row.obj("solid")?.opt("leftStripeColor")
            ?.let { if (it is Number) it.toLong() else it.toString().toLongOrNull() }
        val subtitle = row.obj("subtitle")?.text().orEmpty()
        return YouTubeItem.Category(
            browseId = browseId,
            params = params,
            title = title,
            subtitle = subtitle,
            thumbnail = upscaleArtwork(row.bestThumbnail()),
            color = color
        )
    }

    private fun card(row: JSONObject): YouTubeItem? {
        val title = row.obj("title")?.text()?.takeIf { it.isNotBlank() } ?: return null
        val subtitle = row.obj("subtitle")?.text().orEmpty()
        val thumbnail = upscaleArtwork(row.bestThumbnail())
        val titleRun = row.obj("title")?.arr("runs").objects().firstOrNull()
        val navigation = titleRun?.obj("navigationEndpoint") ?: row.obj("onTap")
        val watch = navigation?.obj("watchEndpoint")
        val videoId = watch?.optString("videoId")?.takeIf { it.isNotBlank() }
        if (videoId != null) {
            return YouTubeItem.Track(
                songFrom(
                    videoId = videoId,
                    title = title,
                    subtitle = subtitle,
                    subtitleRuns = row.obj("subtitle")?.arr("runs").objects(),
                    thumbnail = thumbnail,
                    explicit = isExplicit(row),
                    videoType = watch.obj(
                        "watchEndpointMusicSupportedConfigs", "watchEndpointMusicConfig"
                    )?.optString("musicVideoType")?.takeIf { it.isNotBlank() },
                    browseEndpoint = null
                )
            )
        }
        val browseId = navigation?.obj("browseEndpoint")
            ?.optString("browseId")?.takeIf { it.isNotBlank() } ?: return null
        return browseItem(browseId, title, subtitle, thumbnail, navigation.pageType())
    }

    private fun browseItem(
        browseId: String,
        title: String,
        subtitle: String,
        thumbnail: Uri?,
        pageType: String?
    ): YouTubeItem? = when {
        pageType == PAGE_ALBUM || browseId.startsWith("MPRE") ->
            YouTubeItem.Album(browseId, title, subtitle, thumbnail)
        pageType == PAGE_ARTIST || pageType == PAGE_LIBRARY_ARTIST ||
            browseId.startsWith("UC") || browseId.startsWith("MPLA") ->
            YouTubeItem.Artist(browseId, title, subtitle, thumbnail)
        pageType == PAGE_PLAYLIST || pageType == PAGE_AUDIO_PLAYLIST ||
            pageType == PAGE_PODCAST || browseId.startsWith("VL") ||
            browseId.startsWith("PL") || browseId.startsWith("RD") ||
            browseId.startsWith("OLAK") ->
            YouTubeItem.Playlist(browseId, title, subtitle, thumbnail)
        else ->
            YouTubeItem.Category(
                browseId = browseId,
                params = null,
                title = title,
                subtitle = subtitle,
                thumbnail = thumbnail
            )
    }

    // ── Songs ────────────────────────────────────────────────────────────────

    private fun songFrom(
        videoId: String,
        title: String,
        subtitle: String,
        subtitleRuns: List<JSONObject>,
        thumbnail: Uri?,
        explicit: Boolean,
        videoType: String?,
        browseEndpoint: JSONObject?,
        durationOverride: Long = 0L
    ): Song {
        val segments = splitSubtitle(subtitle)
        val artistRef = subtitleRuns.firstNotNullOfOrNull { run ->
            val endpoint = run.obj("navigationEndpoint")
            if (endpoint.pageType() in setOf(PAGE_ARTIST, PAGE_LIBRARY_ARTIST)) {
                endpoint.obj("browseEndpoint")?.optString("browseId")
                    ?.takeIf { it.isNotBlank() }
            } else null
        }
        val albumRef = subtitleRuns.firstNotNullOfOrNull { run ->
            val endpoint = run.obj("navigationEndpoint")
            if (endpoint.pageType() == PAGE_ALBUM) {
                endpoint.obj("browseEndpoint")?.optString("browseId")
                    ?.takeIf { it.isNotBlank() }
            } else null
        }

        val artist = segments.firstOrNull { segment ->
            !durationRegex.matches(segment) &&
                !yearRegex.matches(segment) &&
                !playsRegex.matches(segment) &&
                !segment.equals("Song", true) &&
                !segment.equals("Video", true) &&
                !segment.equals("Album", true) &&
                !segment.equals("Single", true) &&
                !segment.equals("EP", true)
        } ?: "YouTube Music"

        val album = segments.firstOrNull { segment ->
            segment != artist &&
                !durationRegex.matches(segment) &&
                !yearRegex.matches(segment) &&
                !playsRegex.matches(segment) &&
                !segment.equals("Song", true) &&
                !segment.equals("Video", true)
        } ?: "YouTube Music"

        val duration = durationOverride.takeIf { it > 0L }
            ?: segments.firstNotNullOfOrNull { segment ->
                parseDurationMs(segment).takeIf { it > 0L }
            }
            ?: 0L
        val year = segments.firstNotNullOfOrNull { segment ->
            segment.toIntOrNull()?.takeIf { yearRegex.matches(segment) }
        }
        val plays = segments.firstNotNullOfOrNull { segment ->
            parsePlayCount(segment)
        }

        return Song(
            id = stableSongId(videoId),
            title = title.trim(),
            artist = artist.trim(),
            album = album.trim(),
            duration = duration,
            uri = Uri.parse("https://music.youtube.com/watch?v=$videoId"),
            albumArtUri = thumbnail,
            genre = "YouTube Music",
            isStream = true,
            audioFormat = AudioFormat.STREAM,
            videoId = videoId,
            albumBrowseId = browseEndpoint?.optString("browseId")?.takeIf { it.isNotBlank() } ?: albumRef,
            artistBrowseId = artistRef,
            isExplicit = explicit,
            year = year,
            playCountLong = plays,
            videoType = videoType
        )
    }

    private fun isExplicit(row: JSONObject): Boolean {
        val badges = row.arr("badges").objects()
        val subtitleBadges = row.obj("subtitle")?.arr("runs").objects()
            .flatMap { it.obj("navigationEndpoint")?.let { _ -> emptyList<JSONObject>() } ?: emptyList() }
        return (badges + subtitleBadges).any { badge ->
            val type = badge.obj("musicInlineBadgeRenderer", "icon", "iconType")?.toString()
                ?: badge.obj("icon", "iconType")?.toString()
            type?.contains("EXPLICIT", ignoreCase = true) == true
        }
    }

    // ── Pages ────────────────────────────────────────────────────────────────

    fun page(browseId: String, root: JSONObject): YouTubePage {
        val header = root.obj("header", "musicDetailHeaderRenderer")
            ?: root.obj("header", "musicResponsiveHeaderRenderer")
            ?: root.obj("header", "musicImmersiveHeaderRenderer")
            ?: root.obj("header", "musicVisualHeaderRenderer")
            ?: root.obj("header", "musicHeaderRenderer")

        // Playlist pages have no header renderer; their metadata lives in
        // microformat. Category pages use a bare musicHeaderRenderer above.
        val microformat = root.obj("microformat", "microformatDataRenderer")

        val title = header?.obj("title")?.text()
            ?: header?.obj("heading")?.text()
            ?: root.obj("header")?.obj("musicDetailHeaderRenderer", "title")?.text()
            ?: microformat?.optString("title")?.takeIf { it.isNotBlank() }
            ?: ""
        val subtitle = header?.obj("subtitle")?.text()
            ?: header?.obj("straplineTextOne")?.text()
            ?: header?.obj("description")?.text()
            ?: microformat?.optString("description")?.takeIf { it.isNotBlank() }
        val thumbnail = upscaleArtwork(header?.bestThumbnail())
            ?: upscaleArtwork(microformat?.bestThumbnail())

        val shelves = shelves(root)

        val headerText = (title + " " + subtitle.orEmpty()).lowercase(Locale.ROOT)
        val isAlbum = browseId.startsWith("MPRE") || headerText.contains("album")
        val isArtist = browseId.startsWith("UC") || browseId.startsWith("MPLA")
        val isPlaylist = browseId.startsWith("VL") || browseId.startsWith("PL") ||
            browseId.startsWith("RD")
        val resolvedTitle = title.ifBlank { "YouTube Music" }

        // Album track rows omit the artist column (it is the album artist), so
        // fall back to the second segment of "Album • Artist" for both artist
        // and album name instead of leaving every track as "YouTube Music".
        val albumArtist = if (isAlbum) {
            subtitle?.split("•", "·")
                ?.getOrNull(1)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        } else {
            null
        }

        val songs = shelves.flatMap { it.playableSongs }
            .distinctBy { it.videoId ?: it.id.toString() }
            .map { song ->
                if (!isAlbum) {
                    song
                } else {
                    song.copy(
                        artist = song.artist.takeUnless { it == "YouTube Music" }
                            ?: albumArtist
                            ?: song.artist,
                        album = song.album.takeUnless { it == "YouTube Music" } ?: resolvedTitle
                    )
                }
            }

        return YouTubePage(
            browseId = browseId,
            title = resolvedTitle,
            subtitle = subtitle,
            thumbnail = thumbnail,
            shelves = shelves,
            songs = songs,
            continuation = continuation(root),
            isAlbum = isAlbum,
            isArtist = isArtist,
            isPlaylist = isPlaylist
        )
    }

    fun search(query: String, root: JSONObject, filter: YouTubeSearchFilter): YouTubeSearchPage {
        val sections = browseSections(root)
        val top = sections.firstNotNullOfOrNull {
            it.obj("musicCardShelfRenderer")?.let(::card)
        }
        val items = sections
            .filter { it.obj("musicCardShelfRenderer") == null }
            .mapNotNull(::shelf)
            .flatMap { it.items }
        return YouTubeSearchPage(
            query = query,
            top = top,
            sections = listOf(YouTubeSearchSection(filter, items)),
            continuation = continuation(root)
        )
    }

    fun continuation(root: JSONObject): String? {
        val sections = browseSections(root)
        val sectionToken = sections.lastOrNull()
            ?.obj("continuationItemRenderer", "continuationEndpoint", "continuationCommand")
            ?.optString("token")
            ?.takeIf { it.isNotBlank() }
        if (sectionToken != null) return sectionToken

        val playlistToken = root.findAll("musicPlaylistShelfRenderer").lastOrNull()
            ?.arr("continuations").objects().firstOrNull()
            ?.obj("nextContinuationData")?.optString("continuation")
            ?.takeIf { it.isNotBlank() }
        if (playlistToken != null) return playlistToken

        val shelfToken = root.findAll("musicShelfRenderer").lastOrNull()
            ?.arr("continuations").objects().firstOrNull()
            ?.obj("nextContinuationData")?.optString("continuation")
            ?.takeIf { it.isNotBlank() }
        if (shelfToken != null) return shelfToken

        val continuationToken = root.obj("continuationContents")
            ?.let { it.findAll("nextContinuationData").lastOrNull() }
            ?.optString("continuation")?.takeIf { it.isNotBlank() }
        if (continuationToken != null) return continuationToken

        return root.findAll("continuationCommand")
            .lastOrNull()
            ?.optString("token")
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * A `/browse` or `/search` continuation response nests its rows inside
     * `continuationContents` instead of the usual section list.
     */
    fun continuationShelves(root: JSONObject): List<YouTubeShelf> {
        val contents = root.optJSONObject("continuationContents")
        if (contents == null) return shelves(root)

        val candidates = listOf(
            "musicShelfContinuation",
            "musicPlaylistShelfContinuation",
            "musicCarouselShelfContinuation",
            "gridContinuation"
        )
        for (name in candidates) {
            val node = contents.optJSONObject(name) ?: continue
            val rows = node.arr("contents").objects()
                .ifEmpty { node.arr("items").objects() }
                .mapNotNull(::item)
            if (rows.isNotEmpty()) {
                return listOf(YouTubeShelf(title = "", items = rows))
            }
        }
        return shelves(root)
    }

    fun continuationSongs(root: JSONObject): List<Song> =
        continuationShelves(root).flatMap { it.playableSongs }
            .distinctBy { it.videoId ?: it.id.toString() }

    // ── Radio ────────────────────────────────────────────────────────────────

    fun radio(root: JSONObject): YouTubeRadioPage {
        val panel = root.findAll("playlistPanelRenderer").firstOrNull()
            ?: return YouTubeRadioPage(emptyList())

        val songs = panel.arr("contents").objects().mapNotNull { entry ->
            val row = entry.obj("playlistPanelVideoRenderer")
                ?: entry.obj("playlistPanelVideoWrapperRenderer", "primaryRenderer", "playlistPanelVideoRenderer")
                ?: return@mapNotNull null
            panelSong(row)
        }

        val continuation = panel.arr("continuations").objects().firstOrNull()
            ?.obj("nextRadioContinuationData")?.optString("continuation")
            ?.takeIf { it.isNotBlank() }
            ?: panel.obj("continuationEndpoint", "continuationCommand")?.optString("token")
                ?.takeIf { it.isNotBlank() }

        val playlistId = panel.optString("playlistId").takeIf { it.isNotBlank() }
        return YouTubeRadioPage(songs, continuation, playlistId)
    }

    private fun panelSong(row: JSONObject): Song? {
        val videoId = row.optString("videoId").takeIf { it.isNotBlank() } ?: return null
        val title = row.obj("title")?.text() ?: return null
        val artist = row.obj("longBylineText")?.arr("runs").objects()
            .firstOrNull()?.optString("text")?.takeIf { it.isNotBlank() }
            ?: row.obj("shortBylineText")?.arr("runs").objects()
                .firstOrNull()?.optString("text")
            ?: "YouTube Music"
        val duration = parseDurationMs(row.obj("lengthText")?.text())
        val thumbnail = upscaleArtwork(row.bestThumbnail())
        val playlistId = row.obj("navigationEndpoint", "watchEndpoint")
            ?.optString("playlistId")?.takeIf { it.isNotBlank() }

        return Song(
            id = stableSongId(videoId),
            title = title,
            artist = artist,
            album = "Up Next Radio",
            duration = duration,
            uri = Uri.parse("https://music.youtube.com/watch?v=$videoId"),
            albumArtUri = thumbnail,
            genre = "Radio",
            isStream = true,
            audioFormat = AudioFormat.STREAM,
            videoId = videoId,
            playlistId = playlistId
        )
    }

    // ── Convenience ──────────────────────────────────────────────────────────

    fun firstSongs(root: JSONObject): List<Song> =
        shelves(root).flatMap { it.playableSongs }.distinctBy { it.videoId ?: it.id.toString() }
}
