package com.moodtunes.app.service

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import com.moodtunes.app.data.local.preferences.PlaybackPreferencesRepository
import com.moodtunes.app.domain.model.MoodType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reverb preset descriptor for user-friendly UI display and audio engine mapping.
 */
enum class ReverbPreset(val id: Short, val displayName: String, val icon: String) {
    NONE(PresetReverb.PRESET_NONE, "Off", "🔇"),
    SMALL_ROOM(PresetReverb.PRESET_SMALLROOM, "Studio Room", "🎙️"),
    MEDIUM_ROOM(PresetReverb.PRESET_MEDIUMROOM, "Live Room", "🏠"),
    LARGE_ROOM(PresetReverb.PRESET_LARGEROOM, "Concert Hall", "🏛️"),
    MEDIUM_HALL(PresetReverb.PRESET_MEDIUMHALL, "Auditorium", "🎭"),
    LARGE_HALL(PresetReverb.PRESET_LARGEHALL, "Cathedral", "⛪"),
    PLATE(PresetReverb.PRESET_PLATE, "Plate Reverb", "✨");

    companion object {
        fun fromId(id: Short): ReverbPreset = entries.firstOrNull { it.id == id } ?: NONE
    }
}

/**
 * Wraps platform AudioFX (Equalizer, BassBoost, 3D Virtualizer, PresetReverb, LoudnessEnhancer)
 * and binds them to the active ExoPlayer audio session.
 */
@Singleton
class AudioEffectsManager @Inject constructor(
    private val playbackPreferencesRepository: PlaybackPreferencesRepository
) {
    companion object {
        // Built-in curated EQ curves (5-band normalized: -1.0f to 1.0f)
        val CURATED_PRESETS: Map<String, List<Float>> = mapOf(
            "Flat" to listOf(0f, 0f, 0f, 0f, 0f),
            "Bass Booster" to listOf(0.6f, 0.4f, 0.1f, 0f, -0.1f),
            "Treble Booster" to listOf(-0.2f, 0f, 0.1f, 0.45f, 0.7f),
            "Vocal Booster" to listOf(-0.2f, 0.2f, 0.55f, 0.35f, -0.1f),
            "Acoustic" to listOf(0.35f, 0.25f, 0f, 0.25f, 0.45f),
            "Electronic" to listOf(0.6f, 0.35f, -0.1f, 0.35f, 0.6f),
            "Rock" to listOf(0.5f, 0.25f, -0.15f, 0.25f, 0.5f)
        )
    }

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var presetReverb: PresetReverb? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var attachedSessionId: Int = -1

    private val _isEqualizerEnabled = MutableStateFlow(playbackPreferencesRepository.equalizerEnabled)
    val isEqualizerEnabled: StateFlow<Boolean> = _isEqualizerEnabled.asStateFlow()

    private val _isBassBoostEnabled = MutableStateFlow(playbackPreferencesRepository.bassBoostEnabled)
    val isBassBoostEnabled: StateFlow<Boolean> = _isBassBoostEnabled.asStateFlow()

    private val _bassBoostStrength = MutableStateFlow(playbackPreferencesRepository.bassBoostStrength)
    val bassBoostStrength: StateFlow<Short> = _bassBoostStrength.asStateFlow()

    private val _isVirtualizerEnabled = MutableStateFlow(playbackPreferencesRepository.virtualizerEnabled)
    val isVirtualizerEnabled: StateFlow<Boolean> = _isVirtualizerEnabled.asStateFlow()

    private val _virtualizerStrength = MutableStateFlow(playbackPreferencesRepository.virtualizerStrength)
    val virtualizerStrength: StateFlow<Short> = _virtualizerStrength.asStateFlow()

    private val _reverbPreset = MutableStateFlow(ReverbPreset.fromId(playbackPreferencesRepository.reverbPreset))
    val reverbPreset: StateFlow<ReverbPreset> = _reverbPreset.asStateFlow()

    private val _isLoudnessNormalizationEnabled = MutableStateFlow(playbackPreferencesRepository.loudnessNormalizationEnabled)
    val isLoudnessNormalizationEnabled: StateFlow<Boolean> = _isLoudnessNormalizationEnabled.asStateFlow()

    private val _loudnessGainMb = MutableStateFlow(playbackPreferencesRepository.loudnessGainMb)
    val loudnessGainMb: StateFlow<Int> = _loudnessGainMb.asStateFlow()

    private val _isAutoMoodEqEnabled = MutableStateFlow(playbackPreferencesRepository.autoMoodEqEnabled)
    val isAutoMoodEqEnabled: StateFlow<Boolean> = _isAutoMoodEqEnabled.asStateFlow()

    private val _selectedPresetName = MutableStateFlow(playbackPreferencesRepository.selectedPresetName)
    val selectedPresetName: StateFlow<String> = _selectedPresetName.asStateFlow()

    private val _bandLevels = MutableStateFlow<List<Float>>(emptyList())
    val bandLevels: StateFlow<List<Float>> = _bandLevels.asStateFlow()

    private val _bandFrequencies = MutableStateFlow<List<Int>>(emptyList())
    val bandFrequencies: StateFlow<List<Int>> = _bandFrequencies.asStateFlow()

    private val _presets = MutableStateFlow<List<String>>(CURATED_PRESETS.keys.toList())
    val presets: StateFlow<List<String>> = _presets.asStateFlow()

    /** @return true when AudioFX was successfully bound to the session. */
    fun attach(audioSessionId: Int): Boolean {
        if (audioSessionId <= 0) return false
        if (attachedSessionId == audioSessionId && equalizer != null) return true
        release()
        attachedSessionId = audioSessionId
        return runCatching {
            val eq = Equalizer(0, audioSessionId)
            val bandCount = eq.numberOfBands.toInt()
            val savedLevels = playbackPreferencesRepository.loadEqualizerLevels(bandCount)
            _bandLevels.value = if (savedLevels.size == bandCount) savedLevels else List(bandCount) { 0f }
            _bandFrequencies.value = (0 until bandCount).map { band -> eq.getCenterFreq(band.toShort()) }
            
            // Combine curated presets with any system hardware presets
            val systemPresets = (0 until eq.numberOfPresets.toInt()).map { preset -> eq.getPresetName(preset.toShort()) }
            val combined = (CURATED_PRESETS.keys + systemPresets).distinct()
            _presets.value = combined
            equalizer = eq

            runCatching {
                bassBoost = BassBoost(0, audioSessionId)
            }
            runCatching {
                virtualizer = Virtualizer(0, audioSessionId)
            }
            runCatching {
                presetReverb = PresetReverb(0, audioSessionId)
            }
            runCatching {
                loudnessEnhancer = LoudnessEnhancer(audioSessionId)
            }

            applyEqualizer()
            applyBassBoost()
            applyVirtualizer()
            applyReverb()
            applyLoudnessNormalization()
            true
        }.getOrElse {
            release()
            false
        }
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { presetReverb?.release() }
        runCatching { loudnessEnhancer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        presetReverb = null
        loudnessEnhancer = null
        attachedSessionId = -1
    }

    // ── Equalizer ────────────────────────────────────────────────────────────
    fun toggleEqualizer(enabled: Boolean) {
        playbackPreferencesRepository.equalizerEnabled = enabled
        _isEqualizerEnabled.value = enabled
        applyEqualizer()
    }

    fun setBandLevel(bandIndex: Int, normalized: Float) {
        val levels = _bandLevels.value.toMutableList()
        if (bandIndex !in levels.indices) return
        levels[bandIndex] = normalized.coerceIn(-1f, 1f)
        _bandLevels.value = levels
        val eq = equalizer
        if (eq != null) {
            runCatching {
                val range = eq.bandLevelRange
                val millibels = (range[0] + (range[1] - range[0]) * (normalized + 1f) / 2f).toInt().toShort()
                eq.setBandLevel(bandIndex.toShort(), millibels)
            }
        }
        playbackPreferencesRepository.saveEqualizerLevels(levels)
    }

    fun resetEqualizer() {
        val flat = List(_bandLevels.value.size) { 0f }
        _bandLevels.value = flat
        applyEqualizer()
        playbackPreferencesRepository.saveEqualizerLevels(flat)
    }

    fun applyPreset(presetIndex: Int) {
        val presetName = _presets.value.getOrNull(presetIndex)
        if (presetName != null) {
            applyPresetByName(presetName)
        }
    }

    fun applyPresetByName(name: String) {
        _selectedPresetName.value = name
        playbackPreferencesRepository.selectedPresetName = name

        // 1. Check curated presets first
        val curated = CURATED_PRESETS[name]
        if (curated != null) {
            val bandCount = _bandLevels.value.size
            if (bandCount > 0) {
                val interpolated = sampleCurve(curated, bandCount)
                _bandLevels.value = interpolated
                applyEqualizer()
                playbackPreferencesRepository.saveEqualizerLevels(interpolated)
            }
            return
        }

        // 2. Hardware system presets fallback
        val eq = equalizer ?: return
        val systemPresets = (0 until eq.numberOfPresets.toInt()).map { eq.getPresetName(it.toShort()) }
        val systemIdx = systemPresets.indexOf(name)
        if (systemIdx >= 0) {
            runCatching {
                eq.usePreset(systemIdx.toShort())
                val range = eq.bandLevelRange
                val min = range[0].toInt()
                val max = range[1].toInt()
                val span = (max - min).coerceAtLeast(1)
                val levels = (0 until eq.numberOfBands.toInt()).map { band ->
                    ((eq.getBandLevel(band.toShort()).toInt() - min).toFloat() / span.toFloat()) * 2f - 1f
                }
                _bandLevels.value = levels
                playbackPreferencesRepository.saveEqualizerLevels(levels)
            }
        }
    }

    /**
     * Automatically adapts the equalizer curve to match the emotional mood profile.
     */
    fun applyMood(mood: MoodType) {
        if (!_isAutoMoodEqEnabled.value) return
        val presetName = when (mood) {
            MoodType.ENERGETIC -> "Bass Booster"
            MoodType.CALM, MoodType.SLEEP -> "Acoustic"
            MoodType.HAPPY, MoodType.EUPHORIC -> "Rock"
            MoodType.SAD -> "Vocal Booster"
        }
        applyPresetByName(presetName)
    }

    fun toggleAutoMoodEq(enabled: Boolean) {
        playbackPreferencesRepository.autoMoodEqEnabled = enabled
        _isAutoMoodEqEnabled.value = enabled
    }

    // ── Loudness Normalization (EBU R128 / ReplayGain) ────────────────────────
    fun toggleLoudnessNormalization(enabled: Boolean) {
        playbackPreferencesRepository.loudnessNormalizationEnabled = enabled
        _isLoudnessNormalizationEnabled.value = enabled
        applyLoudnessNormalization()
    }

    fun setLoudnessGainMb(gainMb: Int) {
        val clamped = gainMb.coerceIn(0, 1000) // 0 to +10 dB boost
        playbackPreferencesRepository.loudnessGainMb = clamped
        _loudnessGainMb.value = clamped
        applyLoudnessNormalization()
    }

    // ── Bass Boost ───────────────────────────────────────────────────────────
    fun toggleBassBoost(enabled: Boolean) {
        playbackPreferencesRepository.bassBoostEnabled = enabled
        _isBassBoostEnabled.value = enabled
        applyBassBoost()
    }

    fun setBassBoostStrength(strength: Short) {
        val normalized = strength.coerceIn(0, 1000)
        playbackPreferencesRepository.bassBoostStrength = normalized
        _bassBoostStrength.value = normalized
        applyBassBoost()
    }

    // ── 3D Virtualizer (Spatial Audio) ───────────────────────────────────────
    fun toggleVirtualizer(enabled: Boolean) {
        playbackPreferencesRepository.virtualizerEnabled = enabled
        _isVirtualizerEnabled.value = enabled
        applyVirtualizer()
    }

    fun setVirtualizerStrength(strength: Short) {
        val normalized = strength.coerceIn(0, 1000)
        playbackPreferencesRepository.virtualizerStrength = normalized
        _virtualizerStrength.value = normalized
        applyVirtualizer()
    }

    // ── Reverb Presets ───────────────────────────────────────────────────────
    fun setReverbPreset(preset: ReverbPreset) {
        playbackPreferencesRepository.reverbPreset = preset.id
        _reverbPreset.value = preset
        applyReverb()
    }

    // ── Private Appliers ─────────────────────────────────────────────────────
    private fun applyEqualizer() {
        val eq = equalizer ?: return
        if (!_isEqualizerEnabled.value) {
            val range = eq.bandLevelRange
            val neutral = ((range[0] + range[1]) / 2).toShort()
            (0 until eq.numberOfBands.toInt()).forEach { band ->
                runCatching { eq.setBandLevel(band.toShort(), neutral) }
            }
        } else {
            _bandLevels.value.forEachIndexed { index, level ->
                val range = eq.bandLevelRange
                val millibels = (range[0] + (range[1] - range[0]) * (level + 1f) / 2f).toInt().toShort()
                runCatching { eq.setBandLevel(index.toShort(), millibels) }
            }
        }
    }

    private fun applyLoudnessNormalization() {
        val le = loudnessEnhancer ?: return
        runCatching {
            le.enabled = _isLoudnessNormalizationEnabled.value
            if (_isLoudnessNormalizationEnabled.value) {
                le.setTargetGain(_loudnessGainMb.value)
            }
        }
    }

    private fun applyBassBoost() {
        val bb = bassBoost ?: return
        runCatching {
            bb.enabled = _isBassBoostEnabled.value
            if (_isBassBoostEnabled.value) {
                bb.setStrength(_bassBoostStrength.value)
            }
        }
    }

    private fun applyVirtualizer() {
        val virt = virtualizer ?: return
        runCatching {
            virt.enabled = _isVirtualizerEnabled.value
            if (_isVirtualizerEnabled.value) {
                virt.setStrength(_virtualizerStrength.value)
            }
        }
    }

    private fun applyReverb() {
        val rev = presetReverb ?: return
        val current = _reverbPreset.value
        runCatching {
            rev.enabled = current != ReverbPreset.NONE
            rev.preset = current.id
        }
    }

    private fun sampleCurve(curve5: List<Float>, bandCount: Int): List<Float> {
        if (bandCount <= 0) return emptyList()
        if (bandCount == 5) return curve5
        return (0 until bandCount).map { i ->
            val pos = i.toFloat() / (bandCount - 1).coerceAtLeast(1) * 4f
            val idx = pos.toInt().coerceIn(0, 3)
            val frac = pos - idx
            val v0 = curve5[idx]
            val v1 = curve5[minOf(idx + 1, 4)]
            (v0 + (v1 - v0) * frac).coerceIn(-1f, 1f)
        }
    }
}
