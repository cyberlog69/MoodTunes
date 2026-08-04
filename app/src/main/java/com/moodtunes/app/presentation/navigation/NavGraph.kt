package com.moodtunes.app.presentation.navigation

import android.Manifest
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.moodtunes.app.presentation.ui.history.HistoryScreen
import com.moodtunes.app.presentation.ui.home.HomeScreen
import com.moodtunes.app.presentation.ui.library.LibraryScreen
import com.moodtunes.app.presentation.ui.permission.PermissionScreen
import com.moodtunes.app.presentation.ui.player.PlayerScreen
import com.moodtunes.app.presentation.ui.settings.SettingsScreen

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MoodTunesNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val startDestination = if (audioPermission.status.isGranted) {
        Routes.HOME
    } else {
        Routes.PERMISSION
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.PERMISSION) {
            PermissionScreen(
                onPermissionGranted = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.PERMISSION) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToPlayer = { navController.navigate(Routes.PLAYER) },
                onNavigateToLibrary = { navController.navigate(Routes.LIBRARY) },
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.PLAYER) {
            PlayerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.LIBRARY) {
            LibraryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPlayer = { navController.navigate(Routes.PLAYER) }
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
