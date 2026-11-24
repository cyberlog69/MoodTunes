package com.moodtunes.app.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class DarkModeOption(val displayName: String) {
    SYSTEM("Follow System"),
    DARK("Always Dark"),
    LIGHT("Always Light")
}

enum class StreamQuality(val displayName: String, val badgeText: String) {
    AUTO("Adaptive (Auto)", "Auto"),
    LOSSLESS("Maximum (best available)", "Max"),
    HIGH("High (up to 192 kbps)", "192k"),
    STANDARD("Standard (128 kbps)", "128k"),
    DATA_SAVER("Data Saver (48 kbps)", "48k")
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

enum class PlayerDeckStyle(val displayName: String, val icon: String) {
    VINYL("Vinyl Turntable", "💿"),
    CASSETTE("Retro Cassette", "📼"),
    STANDARD("Modern Cover Art", "🖼️")
}

data class AppUserSettings(
    val darkModeOption: DarkModeOption = DarkModeOption.DARK,
    val useDynamicColors: Boolean = true,
    val performanceMode: Boolean = false,
    val playerDeckStyle: PlayerDeckStyle = PlayerDeckStyle.VINYL,
    val streamQuality: StreamQuality = StreamQuality.LOSSLESS,
    val wifiStreamQuality: StreamQuality = StreamQuality.LOSSLESS,
    val mobileStreamQuality: StreamQuality = StreamQuality.HIGH,
    val wifiOnlyStreaming: Boolean = false,
    val mobileDataHighQuality: Boolean = true,
    val preferredLanguages: Set<MusicLanguage> = setOf(MusicLanguage.ALL),
    // 🧠 ListenBrainz
    val listenBrainzToken: String = "",
    val listenBrainzUsername: String = "",
    val isListenBrainzScrobblingEnabled: Boolean = false,
    // 📻 Last.fm
    val lastFmSessionKey: String = "",
    val lastFmUsername: String = "",
    val isLastFmScrobblingEnabled: Boolean = false
) {
    val preferredLanguage: MusicLanguage
        get() = preferredLanguages.firstOrNull() ?: MusicLanguage.ALL
}

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("moodtunes_settings", Context.MODE_PRIVATE)

    private val secretCipher = SecretCipher()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppUserSettings> = _settings.asStateFlow()

    /** Reads a secret, transparently migrating legacy plaintext values. */
    private fun readSecret(key: String): String {
        val stored = prefs.getString(key, "") ?: ""
        if (stored.isEmpty()) return ""
        if (secretCipher.isEncrypted(stored)) return secretCipher.decrypt(stored) ?: ""

        secretCipher.encrypt(stored)?.let { encrypted ->
            prefs.edit().putString(key, encrypted).apply()
        }
        return stored
    }

    /** Writes a secret encrypted at rest; never persists plaintext. */
    private fun putSecret(
        editor: SharedPreferences.Editor,
        key: String,
        value: String
    ): SharedPreferences.Editor {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return editor.putString(key, "")

        val encrypted = secretCipher.encrypt(trimmed)
        if (encrypted == null) {
            Timber.e("refusing to persist %s in plaintext", key)
            return editor.remove(key)
        }
        return editor.putString(key, encrypted)
    }

    private fun loadSettings(): AppUserSettings {
        val modeStr = prefs.getString("dark_mode", DarkModeOption.DARK.name) ?: DarkModeOption.DARK.name
        val qualityStr = prefs.getString("stream_quality", StreamQuality.LOSSLESS.name) ?: StreamQuality.LOSSLESS.name
        val wifiQualityStr = prefs.getString("wifi_stream_quality", qualityStr) ?: qualityStr
        val defaultMobileQuality = if (prefs.getBoolean("mobile_data_hq", true)) StreamQuality.HIGH.name else StreamQuality.DATA_SAVER.name
        val mobileQualityStr = prefs.getString("mobile_stream_quality", defaultMobileQuality) ?: defaultMobileQuality
        val deckStr = prefs.getString("player_deck_style", PlayerDeckStyle.VINYL.name) ?: PlayerDeckStyle.VINYL.name
        
        val rawLangs = prefs.getString("preferred_languages", prefs.getString("preferred_language", MusicLanguage.ALL.name)) 
            ?: MusicLanguage.ALL.name
        val parsedLangs = rawLangs.split(",")
            .mapNotNull { runCatching { MusicLanguage.valueOf(it.trim()) }.getOrNull() }
            .toSet()
            .ifEmpty { setOf(MusicLanguage.ALL) }

        return AppUserSettings(
            darkModeOption = runCatching { DarkModeOption.valueOf(modeStr) }.getOrDefault(DarkModeOption.DARK),
            useDynamicColors = prefs.getBoolean("dynamic_colors", true),
            performanceMode = prefs.getBoolean("performance_mode", false),
            playerDeckStyle = runCatching { PlayerDeckStyle.valueOf(deckStr) }.getOrDefault(PlayerDeckStyle.VINYL),
            streamQuality = runCatching { StreamQuality.valueOf(qualityStr) }.getOrDefault(StreamQuality.LOSSLESS),
            wifiStreamQuality = runCatching { StreamQuality.valueOf(wifiQualityStr) }.getOrDefault(StreamQuality.LOSSLESS),
            mobileStreamQuality = runCatching { StreamQuality.valueOf(mobileQualityStr) }.getOrDefault(StreamQuality.HIGH),
            wifiOnlyStreaming = prefs.getBoolean("wifi_only_streaming", false),
            mobileDataHighQuality = prefs.getBoolean("mobile_data_hq", true),
            preferredLanguages = parsedLangs,
            listenBrainzToken = readSecret("listenbrainz_token"),
            listenBrainzUsername = prefs.getString("listenbrainz_username", "") ?: "",
            isListenBrainzScrobblingEnabled = prefs.getBoolean("listenbrainz_scrobble_enabled", false),
            lastFmSessionKey = readSecret("lastfm_session_key"),
            lastFmUsername = prefs.getString("lastfm_username", "") ?: "",
            isLastFmScrobblingEnabled = prefs.getBoolean("lastfm_scrobble_enabled", false)
        )
    }

    fun updatePerformanceMode(enabled: Boolean) {
        prefs.edit().putBoolean("performance_mode", enabled).apply()
        _settings.value = _settings.value.copy(performanceMode = enabled)
    }

    fun updatePlayerDeckStyle(style: PlayerDeckStyle) {
        prefs.edit().putString("player_deck_style", style.name).apply()
        _settings.value = _settings.value.copy(playerDeckStyle = style)
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
        prefs.edit().putString("wifi_stream_quality", quality.name).apply()
        _settings.value = _settings.value.copy(streamQuality = quality, wifiStreamQuality = quality)
    }

    fun updateWifiStreamQuality(quality: StreamQuality) {
        prefs.edit().putString("wifi_stream_quality", quality.name).apply()
        prefs.edit().putString("stream_quality", quality.name).apply()
        _settings.value = _settings.value.copy(wifiStreamQuality = quality, streamQuality = quality)
    }

    fun updateMobileStreamQuality(quality: StreamQuality) {
        prefs.edit().putString("mobile_stream_quality", quality.name).apply()
        _settings.value = _settings.value.copy(mobileStreamQuality = quality)
    }

    fun getActiveStreamQuality(): StreamQuality {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val isMetered = cm?.isActiveNetworkMetered == true
        val isWifi = cm?.getNetworkCapabilities(cm.activeNetwork)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        return if (isWifi && !isMetered) {
            _settings.value.wifiStreamQuality
        } else {
            _settings.value.mobileStreamQuality
        }
    }

    fun updateWifiOnlyStreaming(enabled: Boolean) {
        prefs.edit().putBoolean("wifi_only_streaming", enabled).apply()
        _settings.value = _settings.value.copy(wifiOnlyStreaming = enabled)
    }

    fun updateMobileDataHighQuality(enabled: Boolean) {
        prefs.edit().putBoolean("mobile_data_hq", enabled).apply()
        _settings.value = _settings.value.copy(mobileDataHighQuality = enabled)
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
            .let { putSecret(it, "listenbrainz_token", token) }
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

    // ── Last.fm Scrobbling ───────────────────────────────────────────────────
    fun updateLastFm(sessionKey: String, username: String, enabled: Boolean) {
        prefs.edit()
            .let { putSecret(it, "lastfm_session_key", sessionKey) }
            .putString("lastfm_username", username)
            .putBoolean("lastfm_scrobble_enabled", enabled)
            .apply()
        _settings.value = _settings.value.copy(
            lastFmSessionKey = sessionKey.trim(),
            lastFmUsername = username,
            isLastFmScrobblingEnabled = enabled
        )
    }

    fun toggleLastFmScrobbling(enabled: Boolean) {
        prefs.edit().putBoolean("lastfm_scrobble_enabled", enabled).apply()
        _settings.value = _settings.value.copy(isLastFmScrobblingEnabled = enabled)
    }
}
