package com.moodtunes.app.presentation.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodtunes.app.BuildConfig
import com.moodtunes.app.data.local.preferences.AppUserSettings
import com.moodtunes.app.data.local.preferences.AudioSourceMode
import com.moodtunes.app.data.local.preferences.DarkModeOption
import com.moodtunes.app.data.local.preferences.StreamQuality
import com.moodtunes.app.data.local.preferences.StreamingProvider
import com.moodtunes.app.data.local.preferences.UserPreferencesRepository
import com.moodtunes.app.data.remote.AppUpdateManager
import com.moodtunes.app.data.remote.UpdateCheckResult
import com.moodtunes.app.data.remote.UpdateChecker
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
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val updateChecker: UpdateChecker,
    private val appUpdateManager: AppUpdateManager
) : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateCheckResult?>(null)
    private val _isCheckingUpdate = MutableStateFlow(false)
    private val _showUpdateDialog = MutableStateFlow(false)
    private val _isDownloading = MutableStateFlow(false)
    private val _downloadProgress = MutableStateFlow(0)
    private val _whatsNewVersion = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.settings,
        _isCheckingUpdate,
        _updateState,
        _showUpdateDialog,
        _isDownloading,
        _downloadProgress,
        _whatsNewVersion
    ) { args: Array<Any?> ->
        val settings = args[0] as AppUserSettings
        val checking = args[1] as Boolean
        val updateResult = args[2] as UpdateCheckResult?
        val showDialog = args[3] as Boolean
        val downloading = args[4] as Boolean
        val progress = args[5] as Int
        val whatsNew = args[6] as String?

        SettingsUiState(
            userSettings = settings,
            isCheckingUpdate = checking,
            updateResult = updateResult,
            showUpdateDialog = showDialog,
            isDownloading = downloading,
            downloadProgress = progress,
            whatsNewVersion = whatsNew
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
            _whatsNewVersion.value = BuildConfig.VERSION_NAME
        }
    }

    fun dismissWhatsNewDialog() {
        preferencesRepository.markCurrentVersionSeen()
        _whatsNewVersion.value = null
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

    fun onTogglePreferredLanguage(language: com.moodtunes.app.data.local.preferences.MusicLanguage) {
        preferencesRepository.togglePreferredLanguage(language)
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            val result = updateChecker.checkForUpdates(currentVersion = BuildConfig.VERSION_NAME)
            _updateState.value = result
            _isCheckingUpdate.value = false
            if (result.isUpdateAvailable) {
                _showUpdateDialog.value = true
            }
        }
    }

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }

    fun startInAppUpdate(context: Context) {
        val updateResult = _updateState.value ?: return
        viewModelScope.launch {
            _isDownloading.value = true
            _downloadProgress.value = 0

            val apkFile = appUpdateManager.downloadApk(
                context = context,
                apkUrl = updateResult.apkDownloadUrl.ifEmpty { updateResult.downloadUrl },
                onProgress = { progress ->
                    _downloadProgress.value = progress
                }
            )

            _isDownloading.value = false
            if (apkFile != null) {
                _showUpdateDialog.value = false
                appUpdateManager.installApk(context, apkFile)
            }
        }
    }
}
