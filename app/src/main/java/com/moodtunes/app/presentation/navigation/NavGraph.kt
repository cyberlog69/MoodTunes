package com.moodtunes.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.moodtunes.app.presentation.ui.account.YouTubeAccountScreen
import com.moodtunes.app.presentation.ui.collection.CollectionScreen
import com.moodtunes.app.presentation.ui.discover.DiscoverScreen
import com.moodtunes.app.presentation.ui.player.PlayerScreen
import com.moodtunes.app.presentation.ui.playlistdetail.PlaylistDetailScreen
import com.moodtunes.app.presentation.ui.search.SearchScreen
import com.moodtunes.app.presentation.ui.settings.SettingsScreen
import com.moodtunes.app.presentation.ui.youtubepage.YouTubePageScreen

@Composable
fun MoodTunesNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            DiscoverScreen(
                isRoot = true,
                onNavigateToPlayer = { navController.navigate(Routes.PLAYER) },
                onNavigateToLibrary = {
                    navController.navigate(Routes.LIBRARY) {
                        launchSingleTop = true
                    }
                },
                onNavigateToSearch = {
                    navController.navigate(Routes.SEARCH) {
                        launchSingleTop = true
                    }
                },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenYouTubePage = { browseId, params ->
                    navController.navigate(Routes.youTubePage(browseId, params))
                }
            )
        }

        composable(Routes.PLAYER) {
            PlayerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPlayer = { navController.navigate(Routes.PLAYER) },
                onNavigateToPlaylist = { playlistId ->
                    navController.navigate(Routes.playlistDetail(playlistId))
                },
                onNavigateToYouTubePage = { browseId, params ->
                    navController.navigate(Routes.youTubePage(browseId, params))
                }
            )
        }

        composable(Routes.LIBRARY) {
            CollectionScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPlayer = { navController.navigate(Routes.PLAYER) },
                onNavigateToAccount = { navController.navigate(Routes.YOUTUBE_ACCOUNT) },
                onOpenYouTubePage = { browseId, params ->
                    navController.navigate(Routes.youTubePage(browseId, params))
                },
                onNavigateToPlaylist = { playlistId ->
                    navController.navigate(Routes.playlistDetail(playlistId))
                }
            )
        }

        composable(Routes.YOUTUBE_ACCOUNT) {
            YouTubeAccountScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.YOUTUBE_PAGE,
            arguments = listOf(
                navArgument("browseId") { type = NavType.StringType },
                navArgument("params") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) {
            YouTubePageScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPlayer = { navController.navigate(Routes.PLAYER) },
                onOpenYouTubePage = { browseId, params ->
                    navController.navigate(Routes.youTubePage(browseId, params))
                }
            )
        }

        composable(
            route = Routes.PLAYLIST_DETAIL,
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
        ) {
            PlaylistDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAccount = { navController.navigate(Routes.YOUTUBE_ACCOUNT) }
            )
        }
    }
}
