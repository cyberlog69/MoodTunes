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
    ALL_COMBINED("🌟 All Services Combined (Recommended)"),
    JIOSAAVN_REGIONAL("🇮🇳 JioSaavn Regional & Traditional"),
    AUDIUS_ONLY("🎵 Audius Only"),
    ITUNES_DEEZER("🍏 iTunes & Deezer Previews"),
    JAMENDO_ONLY("🎸 Jamendo Indie Only"),
    INTERNET_RADIO("📻 Global Internet Radio Only")
}

enum class MusicLanguage(
    val displayName: String,
    val flagEmoji: String,
    val searchQueryPrefix: String
) {
    ALL("All Languages", "🌐", ""),
    HINDI("Hindi", "🇮🇳", "Hindi"),
    ENGLISH("English", "🇬🇧", "English"),
    PUNJABI("Punjabi", "🌾", "Punjabi"),
    TAMIL("Tamil", "🏛️", "Tamil"),
    TELUGU("Telugu", "🕌", "Telugu"),
    SPANISH("Spanish", "💃", "Spanish"),
    KPOP("K-Pop", "🇰🇷", "K-Pop Korean"),
    JPOP("J-Pop", "🇯🇵", "J-Pop Japanese"),
    INSTRUMENTAL("Instrumental", "🎻", "Instrumental")
}

data class AppUserSettings(
    val darkModeOption: DarkModeOption = DarkModeOption.DARK,
    val useDynamicColors: Boolean = true,
    val streamQuality: StreamQuality = StreamQuality.LOSSLESS,
    val wifiOnlyStreaming: Boolean = false,
    val wifiOnlyDownloads: Boolean = true,
    val mobileDataHighQuality: Boolean = true,
    val audioSourceMode: AudioSourceMode = AudioSourceMode.BOTH,
    val streamingProvider: StreamingProvider = StreamingProvider.ALL_COMBINED,
    val preferredLanguages: Set<MusicLanguage> = setOf(MusicLanguage.ALL),
    // 🧠 ListenBrainz
    val listenBrainzToken: String = "",
    val listenBrainzUsername: String = "",
    val isListenBrainzScrobblingEnabled: Boolean = false,
    // 🏠 Navidrome / Subsonic
    val navidromeServerUrl: String = "",
    val navidromeUsername: String = "",
    val navidromePassword: String = "",
    val isNavidromeEnabled: Boolean = false
) {
    val preferredLanguage: MusicLanguage
        get() = preferredLanguages.firstOrNull() ?: MusicLanguage.ALL
}

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
        val providerStr = prefs.getString("streaming_provider", StreamingProvider.ALL_COMBINED.name) ?: StreamingProvider.ALL_COMBINED.name

        val providerEnum = runCatching { StreamingProvider.valueOf(providerStr) }.getOrElse { StreamingProvider.ALL_COMBINED }
        
        val rawLangs = prefs.getString("preferred_languages", prefs.getString("preferred_language", MusicLanguage.ALL.name)) 
            ?: MusicLanguage.ALL.name
        val parsedLangs = rawLangs.split(",")
            .mapNotNull { runCatching { MusicLanguage.valueOf(it.trim()) }.getOrNull() }
            .toSet()
            .ifEmpty { setOf(MusicLanguage.ALL) }

        return AppUserSettings(
            darkModeOption = runCatching { DarkModeOption.valueOf(modeStr) }.getOrDefault(DarkModeOption.DARK),
            useDynamicColors = prefs.getBoolean("dynamic_colors", true),
            streamQuality = runCatching { StreamQuality.valueOf(qualityStr) }.getOrDefault(StreamQuality.LOSSLESS),
            wifiOnlyStreaming = prefs.getBoolean("wifi_only_streaming", false),
            wifiOnlyDownloads = prefs.getBoolean("wifi_only_downloads", true),
            mobileDataHighQuality = prefs.getBoolean("mobile_data_hq", true),
            audioSourceMode = runCatching { AudioSourceMode.valueOf(sourceStr) }.getOrDefault(AudioSourceMode.BOTH),
            streamingProvider = providerEnum,
            preferredLanguages = parsedLangs,
            listenBrainzToken = prefs.getString("listenbrainz_token", "") ?: "",
            listenBrainzUsername = prefs.getString("listenbrainz_username", "") ?: "",
            isListenBrainzScrobblingEnabled = prefs.getBoolean("listenbrainz_scrobble_enabled", false),
            navidromeServerUrl = prefs.getString("navidrome_server_url", "") ?: "",
            navidromeUsername = prefs.getString("navidrome_username", "") ?: "",
            navidromePassword = prefs.getString("navidrome_password", "") ?: "",
            isNavidromeEnabled = prefs.getBoolean("navidrome_enabled", false)
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

    fun togglePreferredLanguage(language: MusicLanguage) {
        val currentSet = _settings.value.preferredLanguages.toMutableSet()
        if (language == MusicLanguage.ALL) {
            currentSet.clear()
            currentSet.add(MusicLanguage.ALL)
        } else {
            currentSet.remove(MusicLanguage.ALL)
            if (currentSet.contains(language)) {
                currentSet.remove(language)
            } else {
                currentSet.add(language)
            }
            if (currentSet.isEmpty()) {
                currentSet.add(MusicLanguage.ALL)
            }
        }
        val joined = currentSet.joinToString(",") { it.name }
        prefs.edit().putString("preferred_languages", joined).apply()
        _settings.value = _settings.value.copy(preferredLanguages = currentSet)
    }

    fun updatePreferredLanguage(language: MusicLanguage) {
        togglePreferredLanguage(language)
    }

    // ── ListenBrainz Preferences ─────────────────────────────────────────────
    fun updateListenBrainzConfig(token: String, username: String, enabled: Boolean) {
        prefs.edit()
            .putString("listenbrainz_token", token.trim())
            .putString("listenbrainz_username", username.trim())
            .putBoolean("listenbrainz_scrobble_enabled", enabled)
            .apply()
        _settings.value = _settings.value.copy(
            listenBrainzToken = token.trim(),
            listenBrainzUsername = username.trim(),
            isListenBrainzScrobblingEnabled = enabled
        )
    }

    fun updateListenBrainzScrobbling(enabled: Boolean) {
        prefs.edit().putBoolean("listenbrainz_scrobble_enabled", enabled).apply()
        _settings.value = _settings.value.copy(isListenBrainzScrobblingEnabled = enabled)
    }

    // ── Navidrome / Subsonic Preferences ─────────────────────────────────────
    fun updateNavidromeConfig(serverUrl: String, username: String, password: String, enabled: Boolean) {
        prefs.edit()
            .putString("navidrome_server_url", serverUrl.trim())
            .putString("navidrome_username", username.trim())
            .putString("navidrome_password", password.trim())
            .putBoolean("navidrome_enabled", enabled)
            .apply()
        _settings.value = _settings.value.copy(
            navidromeServerUrl = serverUrl.trim(),
            navidromeUsername = username.trim(),
            navidromePassword = password.trim(),
            isNavidromeEnabled = enabled
        )
    }

    fun updateNavidromeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("navidrome_enabled", enabled).apply()
        _settings.value = _settings.value.copy(isNavidromeEnabled = enabled)
    }

    fun checkShouldShowWhatsNew(): String? {
        val lastSeen = prefs.getString("last_seen_version", "") ?: ""
        val currentVersion = com.moodtunes.app.BuildConfig.VERSION_NAME
        if (lastSeen.isEmpty()) {
            prefs.edit().putString("last_seen_version", currentVersion).apply()
            return null
        }
        if (lastSeen != currentVersion) {
            return lastSeen
        }
        return null
    }

    fun markCurrentVersionSeen() {
        val currentVersion = com.moodtunes.app.BuildConfig.VERSION_NAME
        prefs.edit().putString("last_seen_version", currentVersion).apply()
    }

    // ── Recent Searches ──────────────────────────────────────────────────────
    fun getRecentSearches(): List<String> {
        val raw = prefs.getString("recent_searches", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("|||").filter { it.isNotBlank() }
    }

    fun addRecentSearch(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        val current = getRecentSearches().toMutableList()
        current.remove(clean)
        current.add(0, clean)
        val trimmed = current.take(15)
        prefs.edit().putString("recent_searches", trimmed.joinToString("|||")).apply()
    }

    fun removeRecentSearch(query: String) {
        val current = getRecentSearches().toMutableList()
        current.remove(query)
        prefs.edit().putString("recent_searches", current.joinToString("|||")).apply()
    }

    fun clearRecentSearches() {
        prefs.edit().remove("recent_searches").apply()
    }
}
