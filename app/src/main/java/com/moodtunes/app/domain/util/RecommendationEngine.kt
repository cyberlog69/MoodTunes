package com.moodtunes.app.domain.util

import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.model.Song

/**
 * Personalization engine that scores songs by how similar they are to a user's
 * taste profile (favorite + most-played seeds) and their dominant mood.
 */
object RecommendationEngine {

    /**
     * Returns up to [limit] songs recommended for the user.
     *
     * Seeds are the user's favorites + most-played songs. A candidate scores
     * higher when it shares the artist/album/genre of seeds the user plays a
     * lot, matches their [topMood], and has seen some play itself. Songs
     * already in the seed set are excluded.
     */
    fun recommendForYou(
        allSongs: List<Song>,
        favoriteSongs: List<Song>,
        mostPlayed: List<Song>,
        topMood: MoodType?,
        limit: Int = 20
    ): List<Song> {
        if (allSongs.isEmpty()) return emptyList()

        val seeds = (favoriteSongs + mostPlayed)
            .distinctBy { it.id }
            .sortedByDescending { it.playCount }
            .take(24)
        if (seeds.isEmpty()) {
            // Cold start: surface whatever the user already listened to.
            return mostPlayed.take(limit)
        }

        val seedIds = seeds.mapTo(mutableSetOf()) { it.id }
        val seedArtists = seeds.mapTo(mutableSetOf()) { it.artist.lowercase() }
        val seedAlbums = seeds.mapTo(mutableSetOf()) { it.album.lowercase() }
        val seedGenres = seeds.mapNotNullTo(mutableSetOf()) { it.genre?.lowercase() }
        // Weight each seed by how much it was played so "liked" artists dominate.
        val artistWeight = HashMap<String, Float>()
        seeds.forEach { seed ->
            val key = seed.artist.lowercase()
            artistWeight[key] = (artistWeight[key] ?: 0f) + 1f + seed.playCount * 0.1f
        }

        return allSongs
            .asSequence()
            .filter { it.id !in seedIds }
            .mapNotNull { song ->
                val score = scoreCandidate(song, artistWeight, seedAlbums, seedGenres, topMood)
                if (score > 0f) song to score else null
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
            .toList()
    }

    private fun scoreCandidate(
        song: Song,
        artistWeight: Map<String, Float>,
        seedAlbums: Set<String>,
        seedGenres: Set<String>,
        topMood: MoodType?
    ): Float {
        var score = 0f
        artistWeight[song.artist.lowercase()]?.let { score += 3f * it }
        if (song.album.lowercase() in seedAlbums) score += 2f
        if (song.genre?.lowercase() in seedGenres) score += 1.5f
        if (topMood != null) score += MoodScoring.score(song, topMood) * 0.6f
        // A track that was already played a little is more likely to fit.
        score += kotlin.math.min(song.playCount, 10) * 0.1f
        return score
    }
}
