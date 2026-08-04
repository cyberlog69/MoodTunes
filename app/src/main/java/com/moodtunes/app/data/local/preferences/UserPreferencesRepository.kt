package com.moodtunes.app.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class DarkModeOption(val displayName: String) {
    SYSTEM("Follow System"),
    DARK("Always Dark"),
    LIGHT("Always Light")
}

enum class StreamQuality(val displayName: String, val badgeText: String) {
    AUTO("Adaptive (Auto)", "Auto"),
    LOSSLESS("Lossless (FLAC 24-bit)", "FLAC 24-bit"),
    HIGH("High Quality (320 kbps)", "320 kbps"),
    STANDARD("Standard (128 kbps)", "128 kbps")
}

enum class AudioSourceMode(val displayName: String) {
    BOTH("Both Local & Online"),
    LOCAL_ONLY("Local Device Only"),
    STREAM_ONLY("Online Stream Only")
}

enum class StreamingProvider(val displayName: String) {
    BOTH("Both (Audius + YouTube)"),
    AUDIUS_ONLY("Audius Only"),
    YOUTUBE_ONLY("YouTube Only")
}

data class AppUserSettings(
    val darkModeOption: DarkModeOption = DarkModeOption.DARK,
    val useDynamicColors: Boolean = true,
    val streamQuality: StreamQuality = StreamQuality.LOSSLESS,
    val wifiOnlyStreaming: Boolean = false,
    val wifiOnlyDownloads: Boolean = true,
    val mobileDataHighQuality: Boolean = true,
    val audioSourceMode: AudioSourceMode = AudioSourceMode.BOTH,
    val streamingProvider: StreamingProvider = StreamingProvider.BOTH
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("moodtunes_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppUserSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppUserSettings {
        val modeStr = prefs.getString("dark_mode", DarkModeOption.DARK.name) ?: DarkModeOption.DARK.name
        val qualityStr = prefs.getString("stream_quality", StreamQuality.LOSSLESS.name) ?: StreamQuality.LOSSLESS.name
        val sourceStr = prefs.getString("audio_source_mode", AudioSourceMode.BOTH.name) ?: AudioSourceMode.BOTH.name
        val providerStr = prefs.getString("streaming_provider", StreamingProvider.BOTH.name) ?: StreamingProvider.BOTH.name

        return AppUserSettings(
            darkModeOption = runCatching { DarkModeOption.valueOf(modeStr) }.getOrDefault(DarkModeOption.DARK),
            useDynamicColors = prefs.getBoolean("dynamic_colors", true),
            streamQuality = runCatching { StreamQuality.valueOf(qualityStr) }.getOrDefault(StreamQuality.LOSSLESS),
            wifiOnlyStreaming = prefs.getBoolean("wifi_only_streaming", false),
            wifiOnlyDownloads = prefs.getBoolean("wifi_only_downloads", true),
            mobileDataHighQuality = prefs.getBoolean("mobile_data_hq", true),
            audioSourceMode = runCatching { AudioSourceMode.valueOf(sourceStr) }.getOrDefault(AudioSourceMode.BOTH),
            streamingProvider = runCatching { StreamingProvider.valueOf(providerStr) }.getOrDefault(StreamingProvider.BOTH)
        )
    }

    fun updateDarkMode(option: DarkModeOption) {
        prefs.edit().putString("dark_mode", option.name).apply()
        _settings.value = _settings.value.copy(darkModeOption = option)
    }

    fun updateDynamicColors(enabled: Boolean) {
        prefs.edit().putBoolean("dynamic_colors", enabled).apply()
        _settings.value = _settings.value.copy(useDynamicColors = enabled)
    }

    fun updateStreamQuality(quality: StreamQuality) {
        prefs.edit().putString("stream_quality", quality.name).apply()
        _settings.value = _settings.value.copy(streamQuality = quality)
    }

    fun updateWifiOnlyStreaming(enabled: Boolean) {
        prefs.edit().putBoolean("wifi_only_streaming", enabled).apply()
        _settings.value = _settings.value.copy(wifiOnlyStreaming = enabled)
    }

    fun updateWifiOnlyDownloads(enabled: Boolean) {
        prefs.edit().putBoolean("wifi_only_downloads", enabled).apply()
        _settings.value = _settings.value.copy(wifiOnlyDownloads = enabled)
    }

    fun updateMobileDataHighQuality(enabled: Boolean) {
        prefs.edit().putBoolean("mobile_data_hq", enabled).apply()
        _settings.value = _settings.value.copy(mobileDataHighQuality = enabled)
    }

    fun updateAudioSourceMode(mode: AudioSourceMode) {
        prefs.edit().putString("audio_source_mode", mode.name).apply()
        _settings.value = _settings.value.copy(audioSourceMode = mode)
    }

    fun updateStreamingProvider(provider: StreamingProvider) {
        prefs.edit().putString("streaming_provider", provider.name).apply()
        _settings.value = _settings.value.copy(streamingProvider = provider)
    }
}
