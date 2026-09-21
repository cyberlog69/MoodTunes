package com.moodtunes.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodtunes.app.data.local.preferences.AppUserSettings
import com.moodtunes.app.data.local.preferences.UserPreferencesRepository
import com.moodtunes.app.data.remote.AppUpdateManager
import com.moodtunes.app.data.remote.UpdateCheckResult
import com.moodtunes.app.data.remote.UpdateChecker
import com.moodtunes.app.platform.AppUpdateNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    preferencesRepository: UserPreferencesRepository,
    private val updateChecker: UpdateChecker,
    private val appUpdateNotifier: AppUpdateNotifier,
    private val appUpdateManager: AppUpdateManager
) : ViewModel() {

    val settings: StateFlow<AppUserSettings> = preferencesRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppUserSettings()
        )

    private val _updateResult = MutableStateFlow<UpdateCheckResult?>(null)
    val updateResult: StateFlow<UpdateCheckResult?> = _updateResult.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    private val _isDownloadingUpdate = MutableStateFlow(false)
    val isDownloadingUpdate: StateFlow<Boolean> = _isDownloadingUpdate.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    init {
        checkForUpdatesOnLaunch()
    }

    fun checkForUpdatesOnLaunch() {
        viewModelScope.launch {
            try {
                val result = updateChecker.checkForUpdates(force = false)
                if (result.isUpdateAvailable) {
                    Timber.i("MainViewModel: Update available: %s", result.latestVersion)
                    _updateResult.value = result
                    _showUpdateDialog.value = true
                    // Post system notification if not already shown
                    appUpdateNotifier.notifyUpdate(result, force = false)
                }
            } catch (e: Exception) {
                Timber.w(e, "MainViewModel: Startup update check encountered an error")
            }
        }
    }

    fun showUpdateDialogExplicitly(fallbackResult: UpdateCheckResult? = null) {
        if (fallbackResult != null) {
            _updateResult.value = fallbackResult
            _showUpdateDialog.value = true
        } else if (_updateResult.value != null) {
            _showUpdateDialog.value = true
        } else {
            // Trigger check to populate dialog
            viewModelScope.launch {
                val result = updateChecker.checkForUpdates(force = true)
                _updateResult.value = result
                _showUpdateDialog.value = true
            }
        }
    }

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }

    fun startInAppUpdate(context: Context) {
        val result = _updateResult.value ?: return
        viewModelScope.launch {
            _isDownloadingUpdate.value = true
            _downloadProgress.value = 0

            val apkUrl = result.apkDownloadUrl.ifEmpty { result.downloadUrl }
            val apkFile = appUpdateManager.downloadApk(
                context = context,
                apkUrl = apkUrl,
                onProgress = { progress ->
                    _downloadProgress.value = progress
                }
            )

            _isDownloadingUpdate.value = false
            if (apkFile != null) {
                _showUpdateDialog.value = false
                appUpdateNotifier.clearNotification()
                appUpdateManager.installApk(context, apkFile)
            }
        }
    }

    fun downloadAndInstallUpdate(context: Context) = startInAppUpdate(context)
}
