package com.moodtunes.app.platform

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/**
 * Handles tap actions coming from the home screen widget buttons, then
 * triggers a widget refresh so the new state is rendered.
 */
class MusicWidgetActionCallback : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        when (parameters[ACTION_KEY]) {
            ACTION_PLAY_PAUSE -> PlayerConnection.playPause(context)
            ACTION_NEXT -> PlayerConnection.next(context)
            ACTION_PREVIOUS -> PlayerConnection.previous(context)
        }
        MusicWidget().update(context, glanceId)
    }

    companion object {
        val ACTION_KEY: ActionParameters.Key<String> = ActionParameters.Key("widget_action")

        const val ACTION_PLAY_PAUSE = "play_pause"
        const val ACTION_NEXT = "next"
        const val ACTION_PREVIOUS = "previous"
    }
}
