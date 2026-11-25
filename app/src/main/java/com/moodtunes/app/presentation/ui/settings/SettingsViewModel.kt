package com.moodtunes.app.presentation.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodtunes.app.BuildConfig
import com.moodtunes.app.data.local.backup.BackupManager
import com.moodtunes.app.data.local.preferences.AppUserSettings
import com.moodtunes.app.data.local.preferences.DarkModeOption
import com.moodtunes.app.data.local.preferences.MusicLanguage
import com.moodtunes.app.data.local.preferences.StreamQuality
import com.moodtunes.app.data.local.preferences.UserPreferencesRepository
import com.moodtunes.app.data.remote.AppUpdateManager
import com.moodtunes.app.data.remote.UpdateCheckResult
import com.moodtunes.app.data.remote.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import com.moodtunes.app.service.AudioEffectsManager
import com.moodtunes.app.service.PlaybackManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val userSettings: AppUserSettings = AppUserSettings(),
    val isCheckingUpdate: Boolean = false,
    val updateResult: UpdateCheckResult? = null,
    val showUpdateDialog: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0,
    val whatsNewVersion: String? = null,
    val isLoudnessNormalizationEnabled: Boolean = false,
    val isGaplessPlaybackEnabled: Boolean = true,
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val updateChecker: UpdateChecker,
    private val appUpdateManager: AppUpdateManager,
    private val backupManager: BackupManager,
    private val audioEffectsManager: AudioEffectsManager,
    private val playbackManager: PlaybackManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> = combine(
        _uiState,
        preferencesRepository.settings,
        audioEffectsManager.isLoudnessNormalizationEnabled,
        playbackManager.isGaplessPlaybackEnabled
    ) { baseState, settings, loudness, gapless ->
        baseState.copy(
            userSettings = settings,
            isLoudnessNormalizationEnabled = loudness,
            isGaplessPlaybackEnabled = gapless
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    init {
        checkPostUpdateChangelog()
    }

    private fun checkPostUpdateChangelog() {
        val oldVer = preferencesRepository.checkShouldShowWhatsNew()
        if (oldVer != null) {
            _uiState.update { it.copy(whatsNewVersion = BuildConfig.VERSION_NAME) }
        }
    }

    fun dismissWhatsNewDialog() {
        preferencesRepository.markCurrentVersionSeen()
        _uiState.update { it.copy(whatsNewVersion = null) }
    }

    fun onDarkModeChanged(option: DarkModeOption) {
        preferencesRepository.updateDarkMode(option)
    }

    fun onDynamicColorsChanged(enabled: Boolean) {
        preferencesRepository.updateDynamicColors(enabled)
    }

    fun onPerformanceModeChanged(enabled: Boolean) {
        preferencesRepository.updatePerformanceMode(enabled)
    }

    fun onPlayerDeckStyleChanged(style: com.moodtunes.app.data.local.preferences.PlayerDeckStyle) {
        preferencesRepository.updatePlayerDeckStyle(style)
    }

    fun onStreamQualityChanged(quality: StreamQuality) {
        preferencesRepository.updateStreamQuality(quality)
    }

    fun onWifiStreamQualityChanged(quality: StreamQuality) {
        preferencesRepository.updateWifiStreamQuality(quality)
    }

    fun onMobileStreamQualityChanged(quality: StreamQuality) {
        preferencesRepository.updateMobileStreamQuality(quality)
    }

    fun onWifiOnlyStreamingChanged(enabled: Boolean) {
        preferencesRepository.updateWifiOnlyStreaming(enabled)
    }

    fun onMobileDataHighQualityChanged(enabled: Boolean) {
        preferencesRepository.updateMobileDataHighQuality(enabled)
    }

    fun onToggleLoudnessNormalization(enabled: Boolean) {
        audioEffectsManager.toggleLoudnessNormalization(enabled)
    }

    fun onToggleGapless(enabled: Boolean) {
        playbackManager.setGaplessPlaybackEnabled(enabled)
    }


    fun onTogglePreferredLanguage(language: MusicLanguage) {
        preferencesRepository.togglePreferredLanguage(language)
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingUpdate = true) }
            val result = updateChecker.checkForUpdates(currentVersion = BuildConfig.VERSION_NAME)
            _uiState.update {
                it.copy(
                    isCheckingUpdate = false,
                    updateResult = result,
                    showUpdateDialog = result.isUpdateAvailable
                )
            }
        }
    }

    fun dismissUpdateDialog() {
        _uiState.update { it.copy(showUpdateDialog = false) }
    }

    // ── Backup & Restore ─────────────────────────────────────────────────────
    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val ok = backupManager.exportBackup(uri)
            _uiState.update {
                it.copy(message = if (ok) "Backup exported successfully ✅" else "Backup export failed ❌")
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val result = backupManager.importBackup(uri)
            _uiState.update {
                it.copy(message = "Imported ${result.favoritesImported} favorites and ${result.playlistsImported} playlists ✅")
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun startInAppUpdate(context: Context) {
        val updateResult = _uiState.value.updateResult ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true, downloadProgress = 0) }

            val apkFile = appUpdateManager.downloadApk(
                context = context,
                apkUrl = updateResult.apkDownloadUrl.ifEmpty { updateResult.downloadUrl },
                onProgress = { progress ->
                    _uiState.update { it.copy(downloadProgress = progress) }
                }
            )

            _uiState.update { it.copy(isDownloading = false) }
            if (apkFile != null) {
                _uiState.update { it.copy(showUpdateDialog = false) }
                appUpdateManager.installApk(context, apkFile)
            } else {
                _uiState.update { it.copy(showUpdateDialog = false) }
                appUpdateManager.openBrowserFallback(context, updateResult.downloadUrl)
            }
        }
    }
}
