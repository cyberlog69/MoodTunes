package com.moodtunes.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodtunes.app.data.local.preferences.DarkModeOption
import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.platform.CrashHandler
import com.moodtunes.app.presentation.navigation.MoodTunesNavGraph
import com.moodtunes.app.presentation.ui.theme.MoodTunesTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var crashHandler: CrashHandler

    private var deepLinkMood by mutableStateOf<MoodType?>(null)
    private var crashReport by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkMood = parseMood(intent)
        crashReport = crashHandler.latestCrashReport()
        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val settings by mainViewModel.settings.collectAsStateWithLifecycle()

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
