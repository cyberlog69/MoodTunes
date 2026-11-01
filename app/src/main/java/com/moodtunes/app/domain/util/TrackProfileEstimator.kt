package com.moodtunes.app.domain.util

import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.model.Song
import kotlin.math.abs

/**
 * Estimated characteristics of a track.
 *
 * Every field is derived from metadata (artist / title / album / duration), not
 * from the decoded audio signal, so it is a stable *heuristic estimate* used to
 * vary ordering and transport controls — not a measurement.
 */
data class TrackProfile(
    val songId: Long,
    val bpm: Int,             // Estimated tempo, not analysed
    val energy: Float,        // 0.0 to 1.0 (chill to explosive)
    val danceability: Float,  // 0.0 to 1.0
    val valence: Float,       // 0.0 to 1.0 (melancholic to joyful)
    val acousticness: Float,  // 0.0 to 1.0 (synthetic to natural acoustic)
    val camelotKey: String,   // e.g. "8A", "11B" — estimated, not detected
    val detectedMood: MoodType // Heuristic mood guess
)

/**
 * Metadata-based track profile estimator.
 *
 * Derives a deterministic pseudo-profile from the track's metadata so that the
 * same song always produces the same values. This is intentionally *not* audio
 * signal analysis: real BPM/key detection would require decoding PCM and
 * running onset/chroma analysis. The values are only used for soft heuristics
 * (weighted shuffling, UI labels); they must not be presented as measurements.
 */
object TrackProfileEstimator {

    private val CAMELOT_KEYS = listOf(
        "1A", "1B", "2A", "2B", "3A", "3B", "4A", "4B",
        "5A", "5B", "6A", "6B", "7A", "7B", "8A", "8B",
        "9A", "9B", "10A", "10B", "11A", "11B", "12A", "12B"
    )

    fun estimate(song: Song): TrackProfile {
        val seed = abs("${song.artist}_${song.title}_${song.album}".hashCode())
        val durationSec = (song.duration / 1000L).coerceAtLeast(30L)

        // Estimated key (metadata hash, not chroma analysis)
        val key = CAMELOT_KEYS[seed % CAMELOT_KEYS.size]

        // Energy: short radio-length tracks skew higher, long ambient tracks lower
        val rawEnergy = ((seed % 100) / 100f)
        val durationFactor = when {
            durationSec in 150..220 -> 0.15f // Standard high-energy pop single length
            durationSec > 360 -> -0.15f       // Long ambient / progressive tracks
            else -> 0f
        }
        val energy = (rawEnergy + durationFactor).coerceIn(0.1f, 0.98f)

        // Estimated BPM derived from the energy estimate
        val baseBpm = 80 + (energy * 80).toInt() // 80 - 160
        val jitter = (seed % 11) - 5
        val bpm = (baseBpm + jitter).coerceIn(60, 180)

        // Valence (estimated musical positiveness)
        val valence = (((seed shr 3) % 100) / 100f).coerceIn(0.05f, 0.95f)

        // Danceability: correlated with steady mid-high BPM
        val bpmSweetSpot = 1.0f - (abs(bpm - 122) / 60f).coerceIn(0f, 0.8f)
        val danceability = (bpmSweetSpot * 0.7f + energy * 0.3f).coerceIn(0.1f, 0.95f)

        // Acousticness
        val acousticness = (1.0f - energy * 0.7f - ((seed shr 5) % 30) / 100f).coerceIn(0.05f, 0.95f)

        // Heuristic mood classification
        val detectedMood = when {
            energy >= 0.75f && valence >= 0.65f -> MoodType.EUPHORIC
            energy >= 0.65f && valence < 0.65f -> MoodType.ENERGETIC
            energy in 0.45f..0.75f && valence >= 0.50f -> MoodType.HAPPY
            energy in 0.30f..0.55f && valence in 0.35f..0.65f -> MoodType.CALM
            energy < 0.30f -> MoodType.SLEEP
            else -> MoodType.SAD
        }

        return TrackProfile(
            songId = song.id,
            bpm = bpm,
            energy = energy,
            danceability = danceability,
            valence = valence,
            acousticness = acousticness,
            camelotKey = key,
            detectedMood = detectedMood
        )
    }
}
