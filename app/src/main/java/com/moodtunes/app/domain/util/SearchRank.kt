package com.moodtunes.app.domain.util

import com.moodtunes.app.domain.model.Song
import java.text.Normalizer

/**
 * Deterministic relevance ranking for YouTube Music results.
 *
 * InnerTube already returns a sensible order, but the mixed "top result"
 * bucket sometimes puts karaoke covers or hour-long compilations above the
 * studio track. This re-ranks only within a result set using signals we can
 * read from the payload: title/artist overlap, explicit play counts, duration
 * plausibility and whether the result is an official song rather than a video.
 */
object SearchRank {

    private val durationIdealMs = 210_000L
    private val noiseWords = setOf(
        "official", "video", "audio", "lyric", "lyrics", "hd", "hq", "4k",
        "remaster", "remastered", "version", "explicit", "clean", "feat", "ft"
    )

    fun rankSongs(songs: List<Song>, query: String): List<Song> {
        if (songs.size <= 1 || query.isBlank()) return songs
        val tokens = tokens(query)
        return songs
            .mapIndexed { index, song -> song to score(song, tokens, index) }
            .sortedWith(
                compareByDescending<Pair<Song, Double>> { it.second }
                    .thenBy { songs.indexOf(it.first) }
            )
            .map { it.first }
    }

    fun score(song: Song, tokens: Set<String>, originalIndex: Int = 0): Double {
        if (tokens.isEmpty()) return -originalIndex.toDouble()

        val title = normalise(song.title)
        val artist = normalise(song.artist)
        val album = normalise(song.album)

        var score = 0.0
        var titleHits = 0
        var artistHits = 0
        for (token in tokens) {
            if (title.contains(token)) {
                titleHits++
                score += 6.0
            }
            if (artist.contains(token)) {
                artistHits++
                score += 5.0
            }
            if (album.contains(token)) score += 1.5
        }

        score += titleHits.toDouble() / tokens.size * 12.0
        score += artistHits.toDouble() / tokens.size * 8.0

        song.videoType?.let { type ->
            if (type.contains("MUSIC_VIDEO_TYPE_ATV", ignoreCase = true)) score += 4.0
            if (type.contains("OFFICIAL_SOURCE_MUSIC", ignoreCase = true)) score += 2.0
        }
        if (song.isExplicit) score -= 0.2

        song.playCountLong?.let { plays ->
            score += when {
                plays >= 100_000_000 -> 5.0
                plays >= 10_000_000 -> 4.0
                plays >= 1_000_000 -> 3.0
                plays >= 100_000 -> 2.0
                plays >= 10_000 -> 1.0
                else -> 0.0
            }
        }

        if (song.duration > 0) {
            val distance = kotlin.math.abs(song.duration - durationIdealMs)
            score += when {
                distance <= 45_000 -> 2.0
                distance <= 120_000 -> 1.0
                song.duration > 15 * 60_000 -> -4.0
                song.duration < 45_000 -> -2.0
                else -> 0.0
            }
        }

        if (tokens.any { title.startsWith(it) }) score += 2.0
        if (title == tokens.joinToString(" ")) score += 6.0

        // Preserve the provider's own ordering as a tiny tie-breaker.
        score -= originalIndex * 0.001
        return score
    }

    fun rankTitles(values: List<String>, query: String): List<String> {
        val tokens = tokens(query)
        if (tokens.isEmpty()) return values
        return values.sortedWith(
            compareByDescending<String> { value ->
                val normalized = normalise(value)
                tokens.count { normalized.contains(it) }
            }.thenBy { normalise(it).length }
        )
    }

    private fun tokens(value: String): Set<String> =
        normalise(value)
            .split(' ')
            .filter { it.length > 1 && it !in noiseWords }
            .toSet()

    private fun normalise(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9 ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
