package com.moodtunes.app.presentation.navigation

import android.net.Uri

/** All route definitions for the app's navigation graph. */
object Routes {
    const val HOME = "home"
    const val PLAYER = "player"
    const val LIBRARY = "library"
    const val HISTORY = "history"
    const val PERMISSION = "permission"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val DISCOVER = "discover"
    const val COLLECTION = "collection"
    const val YOUTUBE_ACCOUNT = "youtube_account"
    const val YOUTUBE_PAGE = "youtube_page/{browseId}?params={params}"
    const val PLAYLIST_DETAIL = "playlist/{playlistId}"

    fun playlistDetail(playlistId: Long) = "playlist/$playlistId"

    fun youTubePage(browseId: String, params: String? = null): String {
        val encodedId = Uri.encode(browseId)
        val encodedParams = Uri.encode(params.orEmpty())
        return "youtube_page/$encodedId?params=$encodedParams"
    }
}
