package com.moodtunes.app.domain.util

import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.model.Song
import kotlin.random.Random

/**
 * Scores and orders songs by how well they match a mood, enabling
 * mood-weighted ("smart") shuffling. Songs explicitly tagged with the mood are
 * strongly preferred; keyword matches in title/artist/album/genre add weight.
 */
object MoodScoring {

    fun score(song: Song, mood: MoodType): Float {
        var score = 0f
        if (mood in song.moodTags) score += 4f

        val text = buildString {
            append(song.title)
            append(' ')
            append(song.artist)
            append(' ')
            append(song.album)
            song.genre?.let { append(' '); append(it) }
        }.lowercase()

        for (keyword in mood.keywords) {
            if (text.contains(keyword)) score += 1f
        }
        return score
    }

    /**
     * Returns a mood-weighted shuffle of [songs]. Higher-scoring songs are more
     * likely to appear near the front of the queue. A small minimum weight keeps
     * the shuffle stochastic for songs with no mood match.
     */
    fun smartOrder(songs: List<Song>, mood: MoodType, random: Random = Random.Default): List<Song> {
        if (songs.size < 2) return songs
        val weighted = songs.map { song -> song to (score(song, mood) + 0.1f) }
        val result = mutableListOf<Song>()
        val remaining = weighted.toMutableList()
        var remainingTotal = weighted.sumOf { it.second.toDouble() }

        while (remaining.isNotEmpty()) {
            var pick = random.nextDouble() * remainingTotal
            var index = remaining.lastIndex
            for (i in remaining.indices) {
                pick -= remaining[i].second
                if (pick <= 0) {
                    index = i
                    break
                }
            }
            val chosen = remaining.removeAt(index)
            remainingTotal -= chosen.second
            result.add(chosen.first)
        }
        return result
    }
}
