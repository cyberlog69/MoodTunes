package com.moodtunes.app.service

import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.core.content.ContextCompat
import com.moodtunes.app.data.local.preferences.PlaybackPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sin

enum class VisualizerMode(val id: Int, val title: String, val icon: String) {
    OFF(0, "Off", "🚫"),
    BARS(1, "Neon Bars", "📊"),
    PULSE_AURA(2, "Pulse Aura", "💫"),
    PARTICLES(3, "Particles", "✨");

    companion object {
        fun fromId(id: Int): VisualizerMode = entries.firstOrNull { it.id == id } ?: BARS
    }
}

/**
 * Manages audio frequency data capture via Android's [Visualizer] API with smooth
 * fallbacks and decaying peak physics for fluid 60fps rendering in Compose.
 */
@Singleton
class VisualizerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackPreferencesRepository: PlaybackPreferencesRepository
) {
    companion object {
        const val BAND_COUNT = 32
        private const val DECAY_RATE = 0.88f // Smooth drop-off per frame
    }

    private var visualizer: Visualizer? = null
    private var attachedSessionId: Int = -1
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var proceduralJob: Job? = null

    private val _rawBands = MutableStateFlow(FloatArray(BAND_COUNT) { 0f })
    val fftBands: StateFlow<FloatArray> = _rawBands.asStateFlow()

    private val _currentMode = MutableStateFlow(VisualizerMode.fromId(playbackPreferencesRepository.visualizerMode))
    val currentMode: StateFlow<VisualizerMode> = _currentMode.asStateFlow()

    private val currentMagnitudes = FloatArray(BAND_COUNT) { 0f }

    fun setMode(mode: VisualizerMode) {
        playbackPreferencesRepository.visualizerMode = mode.id
        _currentMode.value = mode
    }

    fun cycleMode(): VisualizerMode {
        val nextIndex = (_currentMode.value.ordinal + 1) % VisualizerMode.entries.size
        val nextMode = VisualizerMode.entries[nextIndex]
        setMode(nextMode)
        return nextMode
    }

    /**
     * Attempts to bind Android's native Visualizer effect to the active audio session.
     * If RECORD_AUDIO is granted, uses real FFT capture; otherwise starts procedural generator.
     */
    fun attach(audioSessionId: Int, isPlaying: Boolean) {
        if (audioSessionId <= 0) {
            startProceduralFallback(isPlaying)
            return
        }

        if (attachedSessionId == audioSessionId && visualizer != null) {
            return
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            startProceduralFallback(isPlaying)
            return
        }

        release()
        attachedSessionId = audioSessionId

        try {
            val viz = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[0].coerceAtLeast(64)
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}

                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft == null || _currentMode.value == VisualizerMode.OFF) return
                        processRealFft(fft)
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
            visualizer = viz
            proceduralJob?.cancel()
            proceduralJob = null
        } catch (e: Exception) {
            Timber.w(e, "Visualizer attach failed; using procedural generator")
            visualizer = null
            startProceduralFallback(isPlaying)
        }
    }

    private fun processRealFft(fft: ByteArray) {
        val n = fft.size / 2
        val step = (n / BAND_COUNT).coerceAtLeast(1)

        for (i in 0 until BAND_COUNT) {
            val idx = (i * step * 2).coerceIn(0, fft.size - 2)
            val r = fft[idx].toFloat()
            val im = fft[idx + 1].toFloat()
            val rawMag = kotlin.math.sqrt((r * r + im * im).toDouble()).toFloat() / 128f
            val target = rawMag.coerceIn(0f, 1f)

            // Apply smooth decay
            if (target > currentMagnitudes[i]) {
                currentMagnitudes[i] = target
            } else {
                currentMagnitudes[i] = (currentMagnitudes[i] * DECAY_RATE).coerceAtLeast(0f)
            }
        }
        _rawBands.value = currentMagnitudes.copyOf()
    }

    fun startProceduralFallback(isPlaying: Boolean) {
        if (!isPlaying || _currentMode.value == VisualizerMode.OFF) {
            proceduralJob?.cancel()
            _rawBands.value = FloatArray(BAND_COUNT) { 0f }
            return
        }

        if (proceduralJob?.isActive == true) return

        proceduralJob = scope.launch {
            var phase = 0f
            while (isActive) {
                if (_currentMode.value == VisualizerMode.OFF) {
                    _rawBands.value = FloatArray(BAND_COUNT) { 0f }
                    delay(200)
                    continue
                }

                phase += 0.12f
                for (i in 0 until BAND_COUNT) {
                    val freq = (i + 1) * 0.4f
                    val wave1 = abs(sin((phase * 1.5f + freq).toDouble())).toFloat()
                    val wave2 = abs(sin((phase * 0.8f + i * 0.2f).toDouble())).toFloat()
                    val target = ((wave1 * 0.6f + wave2 * 0.4f) * (1f - (i.toFloat() / BAND_COUNT) * 0.4f)).coerceIn(0.1f, 1f)

                    if (target > currentMagnitudes[i]) {
                        currentMagnitudes[i] = target
                    } else {
                        currentMagnitudes[i] = (currentMagnitudes[i] * 0.85f).coerceAtLeast(0f)
                    }
                }
                _rawBands.value = currentMagnitudes.copyOf()
                delay(33) // ~30-60 fps
            }
        }
    }

    fun release() {
        runCatching {
            visualizer?.enabled = false
            visualizer?.release()
        }
        visualizer = null
        attachedSessionId = -1
        proceduralJob?.cancel()
        proceduralJob = null
        _rawBands.value = FloatArray(BAND_COUNT) { 0f }
    }
}
