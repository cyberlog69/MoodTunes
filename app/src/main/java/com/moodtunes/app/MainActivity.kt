package com.moodtunes.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodtunes.app.data.local.preferences.DarkModeOption
import com.moodtunes.app.presentation.navigation.MoodTunesNavGraph
import com.moodtunes.app.presentation.ui.theme.MoodTunesTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                    MoodTunesNavGraph()
                }
            }
        }
    }
}
