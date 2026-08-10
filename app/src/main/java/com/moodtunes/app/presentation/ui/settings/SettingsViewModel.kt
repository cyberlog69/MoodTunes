package com.moodtunes.app.presentation.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodtunes.app.BuildConfig
import com.moodtunes.app.data.local.backup.BackupManager
import com.moodtunes.app.data.local.preferences.AppUserSettings
import com.moodtunes.app.data.local.preferences.AudioSourceMode
import com.moodtunes.app.data.local.preferences.DarkModeOption
import com.moodtunes.app.data.local.preferences.MusicLanguage
import com.moodtunes.app.data.local.preferences.StreamQuality
import com.moodtunes.app.data.local.preferences.StreamingProvider
import com.moodtunes.app.data.local.preferences.UserPreferencesRepository
import com.moodtunes.app.data.remote.AppUpdateManager
import com.moodtunes.app.data.remote.UpdateCheckResult
import com.moodtunes.app.data.remote.UpdateChecker
import com.moodtunes.app.data.remote.api.SubsonicApiService
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val isTestingNavidrome: Boolean = false,
    val navidromeTestStatus: String? = null,
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val updateChecker: UpdateChecker,
    private val appUpdateManager: AppUpdateManager,
    private val backupManager: BackupManager,
    private val subsonicApiService: SubsonicApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> = combine(
        _uiState,
        preferencesRepository.settings
    ) { state, settings ->
        state.copy(userSettings = settings)
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

    fun onStreamQualityChanged(quality: StreamQuality) {
        preferencesRepository.updateStreamQuality(quality)
    }

    fun onWifiOnlyStreamingChanged(enabled: Boolean) {
        preferencesRepository.updateWifiOnlyStreaming(enabled)
    }

    fun onWifiOnlyDownloadsChanged(enabled: Boolean) {
        preferencesRepository.updateWifiOnlyDownloads(enabled)
    }

    fun onMobileDataHighQualityChanged(enabled: Boolean) {
        preferencesRepository.updateMobileDataHighQuality(enabled)
    }

    fun onAudioSourceModeChanged(mode: AudioSourceMode) {
        preferencesRepository.updateAudioSourceMode(mode)
    }

    fun onStreamingProviderChanged(provider: StreamingProvider) {
        preferencesRepository.updateStreamingProvider(provider)
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

    // ── ListenBrainz Controls ────────────────────────────────────────────────
    fun onSaveListenBrainz(token: String, username: String, enabled: Boolean) {
        preferencesRepository.updateListenBrainzConfig(token, username, enabled)
        _uiState.update { it.copy(message = "ListenBrainz configuration saved ✅") }
    }

    fun onToggleListenBrainzScrobbling(enabled: Boolean) {
        preferencesRepository.updateListenBrainzScrobbling(enabled)
    }

    // ── Navidrome / Subsonic Controls ─────────────────────────────────────────
    fun onSaveNavidrome(serverUrl: String, username: String, password: String, enabled: Boolean) {
        preferencesRepository.updateNavidromeConfig(serverUrl, username, password, enabled)
        _uiState.update { it.copy(message = "Music server configuration saved ✅") }
    }

    fun onToggleNavidrome(enabled: Boolean) {
        preferencesRepository.updateNavidromeEnabled(enabled)
    }

    fun testNavidromeConnection(serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingNavidrome = true, navidromeTestStatus = null) }
            val result = subsonicApiService.ping(serverUrl, username, password)
            _uiState.update {
                it.copy(
                    isTestingNavidrome = false,
                    navidromeTestStatus = if (result.isSuccess) {
                        "Connected to Subsonic Server successfully! ✅"
                    } else {
                        val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Connection failed"
                        "Connection failed: $errorMsg ❌"
                    }
                )
            }
        }
    }

    fun clearNavidromeTestStatus() {
        _uiState.update { it.copy(navidromeTestStatus = null) }
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
            }
        }
    }
}
