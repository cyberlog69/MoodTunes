package com.moodtunes.app.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists playback-related settings (speed, crossfade, equalizer, audio effects, visualizer)
 * that should survive app restarts.
 */
@Singleton
class PlaybackPreferencesRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("moodtunes_playback", Context.MODE_PRIVATE)

    var playbackSpeed: Float
        get() = prefs.getFloat("playback_speed", 1f)
        set(value) {
            prefs.edit().putFloat("playback_speed", value).apply()
        }

    var playbackPitch: Float
        get() = prefs.getFloat("playback_pitch", 1f)
        set(value) {
            prefs.edit().putFloat("playback_pitch", value).apply()
        }

    var crossfadeEnabled: Boolean
        get() = prefs.getBoolean("crossfade_enabled", false)
        set(value) {
            prefs.edit().putBoolean("crossfade_enabled", value).apply()
        }

    var crossfadeDurationMs: Int
        get() = prefs.getInt("crossfade_duration_ms", 1500)
        set(value) {
            prefs.edit().putInt("crossfade_duration_ms", value).apply()
        }

    var equalizerEnabled: Boolean
        get() = prefs.getBoolean("eq_enabled", false)
        set(value) {
            prefs.edit().putBoolean("eq_enabled", value).apply()
        }

    var bassBoostEnabled: Boolean
        get() = prefs.getBoolean("bass_boost_enabled", false)
        set(value) {
            prefs.edit().putBoolean("bass_boost_enabled", value).apply()
        }

    var bassBoostStrength: Short
        get() = prefs.getInt("bass_boost_strength", 0).toShort()
        set(value) {
            prefs.edit().putInt("bass_boost_strength", value.toInt()).apply()
        }

    var virtualizerEnabled: Boolean
        get() = prefs.getBoolean("virtualizer_enabled", false)
        set(value) {
            prefs.edit().putBoolean("virtualizer_enabled", value).apply()
        }

    var virtualizerStrength: Short
        get() = prefs.getInt("virtualizer_strength", 0).toShort()
        set(value) {
            prefs.edit().putInt("virtualizer_strength", value.toInt()).apply()
        }

    var reverbPreset: Short
        get() = prefs.getInt("reverb_preset", 0).toShort()
        set(value) {
            prefs.edit().putInt("reverb_preset", value.toInt()).apply()
        }

    var skipSilenceEnabled: Boolean
        get() = prefs.getBoolean("skip_silence_enabled", false)
        set(value) {
            prefs.edit().putBoolean("skip_silence_enabled", value).apply()
        }

    var visualizerMode: Int
        get() = prefs.getInt("visualizer_mode", 1) // 0: OFF, 1: BARS, 2: PULSE_AURA, 3: PARTICLES
        set(value) {
            prefs.edit().putInt("visualizer_mode", value).apply()
        }

    fun saveEqualizerLevels(levels: List<Float>) {
        prefs.edit()
            .putString("eq_levels", levels.joinToString(",") { it.toString() })
            .apply()
    }

    fun loadEqualizerLevels(bandCount: Int): List<Float> {
        val raw = prefs.getString("eq_levels", "") ?: ""
        if (raw.isBlank()) return List(bandCount) { 0f }
        val parsed = raw.split(",").mapNotNull { it.toFloatOrNull() }
        return if (parsed.size == bandCount) parsed else List(bandCount) { 0f }
    }
}
