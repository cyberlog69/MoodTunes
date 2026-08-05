package com.moodtunes.app.presentation.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodtunes.app.data.local.preferences.AppUserSettings
import com.moodtunes.app.data.local.preferences.AudioSourceMode
import com.moodtunes.app.data.local.preferences.DarkModeOption
import com.moodtunes.app.data.local.preferences.StreamQuality
import com.moodtunes.app.data.local.preferences.StreamingProvider
import com.moodtunes.app.data.local.preferences.UserPreferencesRepository
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
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val updateChecker: UpdateChecker
) : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateCheckResult?>(null)
    private val _isCheckingUpdate = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.settings,
        _isCheckingUpdate,
        _updateState
    ) { settings, checking, updateResult ->
        SettingsUiState(
            userSettings = settings,
            isCheckingUpdate = checking,
            updateResult = updateResult
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

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

    fun onPreferredLanguageChanged(language: com.moodtunes.app.data.local.preferences.MusicLanguage) {
        preferencesRepository.updatePreferredLanguage(language)
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            val result = updateChecker.checkForUpdates(currentVersion = "1.0.0")
            _updateState.value = result
            _isCheckingUpdate.value = false
        }
    }
}
