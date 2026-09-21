package com.moodtunes.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodtunes.app.data.local.preferences.DarkModeOption
import com.moodtunes.app.data.remote.UpdateCheckResult
import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.platform.AppUpdateNotifier
import com.moodtunes.app.platform.CrashHandler
import com.moodtunes.app.presentation.navigation.MoodTunesNavGraph
import com.moodtunes.app.presentation.ui.components.AppUpdateDialog
import com.moodtunes.app.presentation.ui.theme.MoodTunesTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var crashHandler: CrashHandler

    private var deepLinkMood by mutableStateOf<MoodType?>(null)
    private var crashReport by mutableStateOf<String?>(null)
    private var pendingUpdateIntentResult by mutableStateOf<UpdateCheckResult?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission granted or denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermissionIfNeeded()
        deepLinkMood = parseMood(intent)
        crashReport = crashHandler.latestCrashReport()
        checkUpdateIntent(intent)

        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val settings by mainViewModel.settings.collectAsStateWithLifecycle()
            val context = LocalContext.current

            val showUpdateDialog by mainViewModel.showUpdateDialog.collectAsStateWithLifecycle()
            val updateResult by mainViewModel.updateResult.collectAsStateWithLifecycle()
            val isDownloadingUpdate by mainViewModel.isDownloadingUpdate.collectAsStateWithLifecycle()
            val downloadProgress by mainViewModel.downloadProgress.collectAsStateWithLifecycle()

            LaunchedEffect(pendingUpdateIntentResult) {
                pendingUpdateIntentResult?.let { result ->
                    mainViewModel.showUpdateDialogExplicitly(result)
                    pendingUpdateIntentResult = null
                }
            }

            val isSystemDark = isSystemInDarkTheme()
            val darkTheme = when (settings.darkModeOption) {
                DarkModeOption.SYSTEM -> isSystemDark
                DarkModeOption.DARK -> true
                DarkModeOption.LIGHT -> false
            }

            MoodTunesTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.useDynamicColors
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MoodTunesNavGraph(shortcutMood = deepLinkMood)

                    // In-app Update Dialog (triggered on launch or via notification)
                    if (showUpdateDialog && updateResult != null) {
                        AppUpdateDialog(
                            updateResult = updateResult!!,
                            isDownloading = isDownloadingUpdate,
                            downloadProgress = downloadProgress,
                            onUpdateClick = { mainViewModel.startInAppUpdate(context) },
                            onDismiss = mainViewModel::dismissUpdateDialog
                        )
                    }

                    // Crash Recovery Dialog
                    crashReport?.let { report ->
                        CrashRecoveryDialog(
                            report = report,
                            onDismiss = {
                                crashReport = null
                                crashHandler.clearLatestCrash()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkMood = parseMood(intent)
        checkUpdateIntent(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkUpdateIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(AppUpdateNotifier.EXTRA_SHOW_UPDATE, false) == true) {
            val latestVersion = intent.getStringExtra(AppUpdateNotifier.EXTRA_LATEST_VERSION) ?: "Latest"
            val releaseNotes = intent.getStringExtra(AppUpdateNotifier.EXTRA_RELEASE_NOTES) ?: ""
            val downloadUrl = intent.getStringExtra(AppUpdateNotifier.EXTRA_DOWNLOAD_URL) ?: ""
            pendingUpdateIntentResult = UpdateCheckResult(
                isUpdateAvailable = true,
                latestVersion = latestVersion,
                releaseNotes = releaseNotes,
                downloadUrl = downloadUrl,
                apkDownloadUrl = downloadUrl
            )
        }
    }

    private fun parseMood(intent: Intent?): MoodType? {
        val name = intent?.data?.lastPathSegment ?: return null
        return MoodType.entries.firstOrNull { it.name == name }
    }
}

@Composable
private fun CrashRecoveryDialog(report: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recovered from a crash") },
        text = {
            Column {
                Text("Your music library and settings are safe. The app stopped unexpectedly last time.")
                Spacer(Modifier.height(12.dp))
                Text(
                    text = report.take(500),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        }
    )
}
