package com.moodtunes.app.service.dsp

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance real-time vocal suppression / karaoke DSP audio processor.
 * Features:
 * - Center-channel vocal subtraction (L - R mid-side separation in the 200 Hz - 4.5 kHz vocal band)
 * - Low-frequency preservation (< 200 Hz) to keep the kick drum and bassline punchy and intact
 * - High-frequency preservation (> 5 kHz) for cymbals and high-end air
 * - Continuous variable vocal attenuation slider from 0.0f (original) to 1.0f (full karaoke)
 * - Seamless bypass with zero CPU overhead when attenuation is 0.0f
 */
@Singleton
@OptIn(UnstableApi::class)
class KaraokeAudioProcessor @Inject constructor() : BaseAudioProcessor() {

    // 0.0f = full original vocals, 1.0f = full vocal suppression
    private val _vocalAttenuation = MutableStateFlow(0f)
    val vocalAttenuation: StateFlow<Float> = _vocalAttenuation.asStateFlow()

    @Volatile
    private var attenuationStrength: Float = 0f

    // 1st order crossover state variables
    private var lpStateL = 0f
    private var lpStateR = 0f
    private var hpStateL = 0f
    private var hpStateR = 0f

    // Filter coefficients calculated upon sample rate configuration
    private var lpAlpha = 0.03f // ~200 Hz low-pass for bass preservation
    private var hpAlpha = 0.5f  // ~5000 Hz high-pass for treble preservation

    fun setVocalAttenuation(strength: Float) {
        val clamped = strength.coerceIn(0f, 1f)
        attenuationStrength = clamped
        _vocalAttenuation.value = clamped
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        if (inputAudioFormat.channelCount != 2) {
            // Karaoke mid-side processing requires a stereo signal (2 channels)
            return AudioProcessor.AudioFormat.NOT_SET
        }

        // Calculate single-pole filter coefficients for sample rate
        val sr = inputAudioFormat.sampleRate.toFloat()
        if (sr > 0f) {
            // Cutoff ~200 Hz: alpha = dt / (RC + dt) = 2*pi*fc / (2*pi*fc + sr)
            val fcLow = 200f
            val dtLow = 2f * Math.PI.toFloat() * fcLow
            lpAlpha = (dtLow / (dtLow + sr)).coerceIn(0.001f, 0.5f)

            // Cutoff ~4500 Hz
            val fcHigh = 4500f
            val dtHigh = 2f * Math.PI.toFloat() * fcHigh
            hpAlpha = (sr / (dtHigh + sr)).coerceIn(0.001f, 0.99f)
        }

        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val strength = attenuationStrength
        val outputBuffer = replaceOutputBuffer(remaining)

        // Bypass when vocal attenuation is 0.0f
        if (strength <= 0.001f) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val encoding = inputAudioFormat.encoding

        if (encoding == C.ENCODING_PCM_16BIT) {
            process16BitPcm(inputBuffer, outputBuffer, strength)
        } else if (encoding == C.ENCODING_PCM_FLOAT) {
            processFloatPcm(inputBuffer, outputBuffer, strength)
        } else {
            outputBuffer.put(inputBuffer)
        }

        outputBuffer.flip()
    }

    private fun process16BitPcm(input: ByteBuffer, output: ByteBuffer, strength: Float) {
        // Gain boost to compensate for energy reduction in mid channel
        val compensationGain = 1.0f + 0.25f * strength

        while (input.remaining() >= 4) {
            val leftShort = input.short
            val rightShort = input.short

            val left = leftShort.toFloat()
            val right = rightShort.toFloat()

            // Mid (center) and Side (stereo) channels
            val mid = (left + right) * 0.5f

            // Low-pass filter (preserves bass/kick below ~200 Hz)
            lpStateL += lpAlpha * (mid - lpStateL)
            val bassComponent = lpStateL

            // High-pass filter (preserves cymbals/highs above ~4500 Hz)
            val prevMid = hpStateR
            hpStateR = mid
            hpStateL = hpAlpha * (hpStateL + mid - prevMid)
            val trebleComponent = hpStateL

            // Center vocal component in the 200 Hz - 4.5 kHz range
            val centerVocal = mid - bassComponent - trebleComponent

            // Attenuate center vocal while preserving side stereo and bass/treble
            val outLeft = (left - strength * centerVocal) * compensationGain
            val outRight = (right - strength * centerVocal) * compensationGain

            val clampedLeft = max(-32768f, min(32767f, outLeft)).toInt().toShort()
            val clampedRight = max(-32768f, min(32767f, outRight)).toInt().toShort()

            output.putShort(clampedLeft)
            output.putShort(clampedRight)
        }
    }

    private fun processFloatPcm(input: ByteBuffer, output: ByteBuffer, strength: Float) {
        val compensationGain = 1.0f + 0.25f * strength

        while (input.remaining() >= 8) {
            val left = input.float
            val right = input.float

            val mid = (left + right) * 0.5f

            lpStateL += lpAlpha * (mid - lpStateL)
            val bassComponent = lpStateL

            val prevMid = hpStateR
            hpStateR = mid
            hpStateL = hpAlpha * (hpStateL + mid - prevMid)
            val trebleComponent = hpStateL

            val centerVocal = mid - bassComponent - trebleComponent

            val outLeft = (left - strength * centerVocal) * compensationGain
            val outRight = (right - strength * centerVocal) * compensationGain

            output.putFloat(max(-1.0f, min(1.0f, outLeft)))
            output.putFloat(max(-1.0f, min(1.0f, outRight)))
        }
    }

    override fun onReset() {
        lpStateL = 0f
        lpStateR = 0f
        hpStateL = 0f
        hpStateR = 0f
    }
}
