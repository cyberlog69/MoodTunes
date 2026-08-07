package com.moodtunes.app.presentation.navigation

/** All route definitions for the app's navigation graph. */
object Routes {
    const val HOME = "home"
    const val PLAYER = "player"
    const val LIBRARY = "library"
    const val HISTORY = "history"
    const val PERMISSION = "permission"
    const val SETTINGS = "settings"
    const val PLAYLIST_DETAIL = "playlist/{playlistId}"

    fun playlistDetail(playlistId: Long) = "playlist/$playlistId"
}
