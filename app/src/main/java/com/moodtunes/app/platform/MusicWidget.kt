package com.moodtunes.app.platform

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.moodtunes.app.MainActivity
import com.moodtunes.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Home screen widget with play/pause, previous/next, and current-track info.
 * Renders fresh state on every [GlanceAppWidget] update and on button presses.
 */
class MusicWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = withContext(Dispatchers.IO) { loadState(context) }
        provideContent {
            MusicWidgetContent(state = state)
        }
    }

    private fun loadState(context: Context): WidgetPlayerState {
        val controller = PlayerConnection.connect(context) ?: return WidgetPlayerState()
        return try {
            val item = controller.currentMediaItem
            val title = item?.mediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() } ?: "MoodTunes"
            val artist = item?.mediaMetadata?.artist?.toString()?.takeIf { it.isNotBlank() } ?: "Nothing playing"
            WidgetPlayerState(
                title = title,
                artist = artist,
                isPlaying = controller.isPlaying,
                hasSong = item != null
            )
        } finally {
            controller.release()
        }
    }
}

data class WidgetPlayerState(
    val title: String = "MoodTunes",
    val artist: String = "Nothing playing",
    val isPlaying: Boolean = false,
    val hasSong: Boolean = false
)

@Composable
private fun MusicWidgetContent(state: WidgetPlayerState) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF1E1E28)))
            .padding(12)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            // Track info (tap to open the app)
            Column(
                modifier = GlanceModifier
                    .size(130, 90)
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Text(
                    text = state.title,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Text(
                    text = state.artist,
                    style = TextStyle(
                        color = ColorProvider(Color(0xB3FFFFFF)),
                        fontSize = 12.sp
                    ),
                    maxLines = 1
                )
            }

            // Transport controls
            WidgetIconButton(
                iconRes = R.drawable.ic_widget_prev,
                contentDesc = "Previous",
                action = actionRunCallback<MusicWidgetActionCallback>(
                    actionParametersOf(
                        MusicWidgetActionCallback.ACTION_KEY to MusicWidgetActionCallback.ACTION_PREVIOUS
                    )
                )
            )
            WidgetIconButton(
                iconRes = if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                contentDesc = if (state.isPlaying) "Pause" else "Play",
                action = actionRunCallback<MusicWidgetActionCallback>(
                    actionParametersOf(
                        MusicWidgetActionCallback.ACTION_KEY to MusicWidgetActionCallback.ACTION_PLAY_PAUSE
                    )
                )
            )
            WidgetIconButton(
                iconRes = R.drawable.ic_widget_next,
                contentDesc = "Next",
                action = actionRunCallback<MusicWidgetActionCallback>(
                    actionParametersOf(
                        MusicWidgetActionCallback.ACTION_KEY to MusicWidgetActionCallback.ACTION_NEXT
                    )
                )
            )
        }
    }
}

@Composable
private fun WidgetIconButton(iconRes: Int, contentDesc: String, action: Action) {
    Image(
        provider = ImageProvider(iconRes),
        contentDescription = contentDesc,
        modifier = GlanceModifier
            .size(38, 38)
            .clickable(action)
    )
}
