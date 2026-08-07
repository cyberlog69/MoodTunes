package com.moodtunes.app.service

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import com.moodtunes.app.data.local.preferences.PlaybackPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps the platform AudioFX equalizer + bass boost and binds it to the audio
 * session owned by the active ExoPlayer (see [PlaybackManager.ensureAudioEffectsAttached]).
 *
 * Band levels are exposed as normalized floats in the range [-1f, 1f] where 0f
 * is a flat response. All state is persisted via [PlaybackPreferencesRepository].
 */
@Singleton
class AudioEffectsManager @Inject constructor(
    private val playbackPreferencesRepository: PlaybackPreferencesRepository
) {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var attachedSessionId: Int = -1

    private val _isEqualizerEnabled = MutableStateFlow(playbackPreferencesRepository.equalizerEnabled)
    val isEqualizerEnabled: StateFlow<Boolean> = _isEqualizerEnabled.asStateFlow()

    private val _isBassBoostEnabled = MutableStateFlow(playbackPreferencesRepository.bassBoostEnabled)
    val isBassBoostEnabled: StateFlow<Boolean> = _isBassBoostEnabled.asStateFlow()

    private val _bassBoostStrength = MutableStateFlow(playbackPreferencesRepository.bassBoostStrength)
    val bassBoostStrength: StateFlow<Short> = _bassBoostStrength.asStateFlow()

    private val _bandLevels = MutableStateFlow<List<Float>>(emptyList())
    val bandLevels: StateFlow<List<Float>> = _bandLevels.asStateFlow()

    private val _bandFrequencies = MutableStateFlow<List<Int>>(emptyList())
    val bandFrequencies: StateFlow<List<Int>> = _bandFrequencies.asStateFlow()

    private val _presets = MutableStateFlow<List<String>>(emptyList())
    val presets: StateFlow<List<String>> = _presets.asStateFlow()

    /** @return true when the Equalizer was successfully bound to the session. */
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

            val bb = BassBoost(0, audioSessionId)
            bassBoost = bb

            applyEqualizer()
            applyBassBoost()
            true
        }.getOrElse {
            release()
            false
        }
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        equalizer = null
        bassBoost = null
        attachedSessionId = -1
    }

    fun toggleEqualizer(enabled: Boolean) {
        playbackPreferencesRepository.equalizerEnabled = enabled
        _isEqualizerEnabled.value = enabled
        applyEqualizer()
    }

    fun toggleBassBoost(enabled: Boolean) {
        playbackPreferencesRepository.bassBoostEnabled = enabled
        _isBassBoostEnabled.value = enabled
        applyBassBoost()
    }

    fun setBassBoostStrength(strength: Short) {
        val normalized = strength.coerceIn(0, 1000)
        playbackPreferencesRepository.bassBoostStrength = normalized
        _bassBoostStrength.value = normalized
        runCatching {
            bassBoost?.setStrength(if (_isBassBoostEnabled.value) normalized else 0)
        }
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
        runCatching { bb.setStrength(if (_isBassBoostEnabled.value) _bassBoostStrength.value else 0) }
    }
}
