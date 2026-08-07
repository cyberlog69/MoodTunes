package com.moodtunes.app.platform

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.moodtunes.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quick Settings tile that toggles play/pause and shows the current track.
 */
class MusicTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        refreshTile()
    }

    override fun onStopListening() {
        scope.coroutineContext.cancelChildren()
    }

    override fun onClick() {
        if (!isLocked) {
            scope.launch {
                withContext(Dispatchers.IO) { PlayerConnection.playPause(applicationContext) }
                refreshTile()
            }
        }
    }

    private fun refreshTile() {
        val controller = PlayerConnection.connect(this) ?: return
        val playing = controller.isPlaying
        val title = controller.currentMediaItem?.mediaMetadata?.title?.toString()
        controller.release()

        val tile = qsTile ?: return
        tile.label = getString(if (playing) R.string.tile_label_playing else R.string.tile_label_paused)
        tile.subtitle = title ?: getString(R.string.app_name)
        tile.icon = Icon.createWithResource(
            this,
            if (playing) R.drawable.ic_tile_pause else R.drawable.ic_tile_play
        )
        tile.state = if (playing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
