package com.moodtunes.app.service

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import com.moodtunes.app.data.local.preferences.PlaybackPreferencesRepository
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
 * Wraps platform AudioFX (Equalizer, BassBoost, 3D Virtualizer, PresetReverb)
 * and binds them to the active ExoPlayer audio session.
 */
@Singleton
class AudioEffectsManager @Inject constructor(
    private val playbackPreferencesRepository: PlaybackPreferencesRepository
) {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var presetReverb: PresetReverb? = null
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

    private val _bandLevels = MutableStateFlow<List<Float>>(emptyList())
    val bandLevels: StateFlow<List<Float>> = _bandLevels.asStateFlow()

    private val _bandFrequencies = MutableStateFlow<List<Int>>(emptyList())
    val bandFrequencies: StateFlow<List<Int>> = _bandFrequencies.asStateFlow()

    private val _presets = MutableStateFlow<List<String>>(emptyList())
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
            _presets.value = (0 until eq.numberOfPresets.toInt()).map { preset -> eq.getPresetName(preset.toShort()) }
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

            applyEqualizer()
            applyBassBoost()
            applyVirtualizer()
            applyReverb()
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
        equalizer = null
        bassBoost = null
        virtualizer = null
        presetReverb = null
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
        val eq = equalizer ?: return
        if (presetIndex !in 0 until eq.numberOfPresets.toInt()) return
        runCatching {
            eq.usePreset(presetIndex.toShort())
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
}
