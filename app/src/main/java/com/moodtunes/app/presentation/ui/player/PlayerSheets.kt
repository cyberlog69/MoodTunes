package com.moodtunes.app.presentation.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moodtunes.app.domain.model.LyricsLine
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.presentation.ui.theme.White

/** Which bottom sheet is currently open on the player screen. */
enum class PlayerSheet { NONE, QUEUE, LYRICS, SPEED, SLEEP_TIMER, EQUALIZER, CROSSFADE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerBottomSheet(
    sheet: PlayerSheet,
    uiState: PlayerUiState,
    onDismiss: () -> Unit,
    viewModel: PlayerViewModel
) {
    if (sheet == PlayerSheet.NONE) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        when (sheet) {
            PlayerSheet.QUEUE -> QueueSheetContent(
                songs = uiState.songs,
                currentIndex = uiState.currentSongIndex,
                isPlaying = uiState.isPlaying,
                onSongClick = viewModel::playSongAtIndex,
                onMoveUp = viewModel::moveQueueItem,
                onMoveDown = { from, to -> viewModel.moveQueueItem(from, to) },
                onRemove = viewModel::removeFromQueue,
                onShuffleQueue = viewModel::shuffleQueue,
                onClearQueue = { viewModel.clearQueue(keepCurrent = true) }
            )
            PlayerSheet.LYRICS -> LyricsSheetContent(
                lyrics = uiState.lyrics,
                currentPositionMs = uiState.currentPositionMs,
                isLoading = uiState.isLyricsLoading
            )
            PlayerSheet.SPEED -> SpeedSheetContent(
                speed = uiState.playbackSpeed,
                onSpeedSelected = viewModel::setPlaybackSpeed
            )
            PlayerSheet.SLEEP_TIMER -> SleepTimerSheetContent(
                remainingMs = uiState.sleepTimerRemainingMs,
                onStart = viewModel::startSleepTimer,
                onCancel = viewModel::cancelSleepTimer
            )
            PlayerSheet.EQUALIZER -> EqualizerSheetContent(
                isEqualizerEnabled = uiState.isEqualizerEnabled,
                isBassBoostEnabled = uiState.isBassBoostEnabled,
                bassBoostStrength = uiState.bassBoostStrength,
                bandLevels = uiState.equalizerLevels,
                bandFrequencies = uiState.equalizerFrequencies,
                presets = uiState.equalizerPresets,
                onToggleEqualizer = viewModel::toggleEqualizer,
                onToggleBassBoost = viewModel::toggleBassBoost,
                onBassBoostStrength = viewModel::setBassBoostStrength,
                onBandLevel = viewModel::setBandLevel,
                onReset = viewModel::resetEqualizer,
                onPreset = viewModel::applyEqualizerPreset
            )
            PlayerSheet.CROSSFADE -> CrossfadeSheetContent(
                isEnabled = uiState.isCrossfadeEnabled,
                durationMs = uiState.crossfadeDurationMs,
                onEnabledChanged = viewModel::setCrossfadeEnabled,
                onDurationChanged = viewModel::setCrossfadeDurationMs
            )
            PlayerSheet.NONE -> Unit
        }
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
}

// ─── Queue editor ────────────────────────────────────────────────────────────

@Composable
private fun QueueSheetContent(
    songs: List<Song>,
    currentIndex: Int,
    isPlaying: Boolean,
    onSongClick: (Int) -> Unit,
    onMoveUp: (Int, Int) -> Unit,
    onMoveDown: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onShuffleQueue: () -> Unit,
    onClearQueue: () -> Unit
) {
    val totalRemainingMs = remember(songs, currentIndex) {
        songs.drop(currentIndex.coerceAtLeast(0)).sumOf { it.duration }
    }
    val formattedRemaining = remember(totalRemainingMs) {
        val totalSec = totalRemainingMs / 1000
        val mins = totalSec / 60
        if (mins > 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "Up Next Queue",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${songs.size} tracks • $formattedRemaining left",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (songs.size > 2) {
                IconButton(
                    onClick = onShuffleQueue,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle upcoming",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (songs.size > 1) {
                IconButton(
                    onClick = onClearQueue,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Rounded.DeleteSweep,
                        contentDescription = "Clear upcoming",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }

    if (songs.isEmpty()) {
        EmptySheetMessage(text = "Queue is empty.\nSelect a song to start listening.")
    } else {
        val listState = rememberLazyListState()
        LaunchedEffect(currentIndex) {
            if (currentIndex in songs.indices) {
                listState.animateScrollToItem(currentIndex.coerceAtLeast(0))
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
        ) {
            itemsIndexed(songs, key = { index, song -> "queue_${song.id}_$index" }) { index, song ->
                val isCurrent = index == currentIndex
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSongClick(index) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Index or Play indicator
                        Box(
                            modifier = Modifier.width(30.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCurrent) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                                    contentDescription = "Playing",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Text(
                                    text = (index + 1).toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Artwork
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            coil.compose.AsyncImage(
                                model = song.albumArtUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                            if (song.albumArtUri == null) {
                                Icon(
                                    Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        // Title & Artist
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrent) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "• ${song.formattedDuration}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }

                        // Move Up
                        IconButton(
                            onClick = { onMoveUp(index, index - 1) },
                            enabled = index > 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.KeyboardArrowUp,
                                contentDescription = "Move up",
                                tint = if (index > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Move Down
                        IconButton(
                            onClick = { onMoveDown(index, index + 1) },
                            enabled = index < songs.lastIndex,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Move down",
                                tint = if (index < songs.lastIndex) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Remove
                        IconButton(
                            onClick = { onRemove(index) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Remove from queue",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Synced lyrics ───────────────────────────────────────────────────────────

@Composable
private fun LyricsSheetContent(
    lyrics: List<LyricsLine>,
    currentPositionMs: Long,
    isLoading: Boolean
) {
    SheetHeader(icon = Icons.Rounded.Subtitles, title = "Lyrics", subtitle = "Synced lyrics")
    if (isLoading) {
        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
        }
    } else if (lyrics.isEmpty()) {
        EmptySheetMessage(text = "No synced lyrics found.\nPlace a .lrc file next to the audio file.")
    } else {
        val listState = rememberLazyListState()
        val currentLineIndex = remember(currentPositionMs, lyrics) {
            lyrics.indexOfLast { it.timeMs <= currentPositionMs }.coerceAtLeast(0)
        }
        LaunchedEffect(currentLineIndex) {
            listState.animateScrollToItem(currentLineIndex)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
        ) {
            itemsIndexed(lyrics, key = { index, line -> "lyric_${line.timeMs}_$index" }) { index, line ->
                val isCurrent = index == currentLineIndex
                Text(
                    text = line.text.ifEmpty { "♪" },
                    style = if (isCurrent) MaterialTheme.typography.titleLarge
                    else MaterialTheme.typography.titleMedium,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ─── Playback speed ──────────────────────────────────────────────────────────

@Composable
private fun SpeedSheetContent(
    speed: Float,
    onSpeedSelected: (Float) -> Unit
) {
    val options = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    SheetHeader(icon = Icons.Rounded.Speed, title = "Playback Speed", subtitle = "Change playback speed")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        options.forEach { option ->
            val isSelected = speed == option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else Color.Transparent
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (option == 1f) "Normal" else "${formatSpeed(option)}×",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// ─── Sleep timer ─────────────────────────────────────────────────────────────

@Composable
private fun SleepTimerSheetContent(
    remainingMs: Long?,
    onStart: (Int) -> Unit,
    onCancel: () -> Unit
) {
    val options = listOf(10, 15, 20, 30, 45, 60, 90)
    SheetHeader(icon = Icons.Rounded.Bedtime, title = "Sleep Timer", subtitle = "Auto-pause after a set time")
    if (remainingMs != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⏳ Stopping in ${formatRemaining(remainingMs)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        itemsIndexed(options) { _, minutes ->
            FilterChip(
                selected = remainingMs != null,
                onClick = { onStart(minutes) },
                label = { Text("$minutes min") },
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

// ─── Equalizer & bass boost ──────────────────────────────────────────────────

@Composable
private fun EqualizerSheetContent(
    isEqualizerEnabled: Boolean,
    isBassBoostEnabled: Boolean,
    bassBoostStrength: Short,
    bandLevels: List<Float>,
    bandFrequencies: List<Int>,
    presets: List<String>,
    onToggleEqualizer: (Boolean) -> Unit,
    onToggleBassBoost: (Boolean) -> Unit,
    onBassBoostStrength: (Short) -> Unit,
    onBandLevel: (Int, Float) -> Unit,
    onReset: () -> Unit,
    onPreset: (Int) -> Unit
) {
    SheetHeader(
        icon = Icons.Rounded.Equalizer,
        title = "Equalizer",
        subtitle = "10-band audio equalizer & bass boost"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        // Equalizer enable
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = if (isEqualizerEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Equalizer",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = isEqualizerEnabled,
                onCheckedChange = onToggleEqualizer
            )
        }

        if (isEqualizerEnabled) {
            if (presets.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(presets) { index, name ->
                        FilterChip(
                            selected = false,
                            onClick = { onPreset(index) },
                            label = { Text(name) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            if (bandLevels.isEmpty()) {
                Text(
                    text = "Equalizer unavailable on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                bandLevels.forEachIndexed { bandIndex, level ->
                    val freq = bandFrequencies.getOrNull(bandIndex) ?: 0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatFrequency(freq),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(64.dp)
                        )
                        Slider(
                            value = level.coerceIn(-1f, 1f),
                            onValueChange = { onBandLevel(bandIndex, it) },
                            valueRange = -1f..1f,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${((level.coerceIn(-1f, 1f)) * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(48.dp)
                        )
                    }
                }
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Reset")
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Bass boost
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = if (isBassBoostEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Bass Boost",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = isBassBoostEnabled,
                onCheckedChange = onToggleBassBoost
            )
        }
        if (isBassBoostEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = bassBoostStrength.toInt().toFloat(),
                    onValueChange = { onBassBoostStrength(it.toInt().toShort()) },
                    valueRange = 0f..1000f,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(bassBoostStrength.toInt() / 10)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(48.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ─── Crossfade ───────────────────────────────────────────────────────────────

@Composable
private fun CrossfadeSheetContent(
    isEnabled: Boolean,
    durationMs: Int,
    onEnabledChanged: (Boolean) -> Unit,
    onDurationChanged: (Int) -> Unit
) {
    val options = listOf(500, 1000, 1500, 2000, 3000, 5000)
    SheetHeader(icon = Icons.Rounded.SwapHoriz, title = "Crossfade", subtitle = "Smooth transitions between tracks")
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Enable crossfade",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = isEnabled,
                onCheckedChange = onEnabledChanged
            )
        }
        if (isEnabled) {
            Text(
                text = "Transition duration",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(options) { _, duration ->
                    FilterChip(
                        selected = durationMs == duration,
                        onClick = { onDurationChanged(duration) },
                        label = { Text("${duration / 1000.0}s") },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
    }
}

// ─── Shared helpers ──────────────────────────────────────────────────────────

@Composable
private fun SheetHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptySheetMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatSpeed(speed: Float): String {
    return if (speed % 1f == 0f) speed.toInt().toString() else String.format("%.2f", speed).trimEnd('0').trimEnd('.')
}

private fun formatRemaining(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatFrequency(hz: Int): String {
    return if (hz >= 1000) {
        val khz = hz / 1000f
        if (khz % 1f == 0f) "${khz.toInt()}k" else String.format("%.1fk", khz)
    } else {
        "${hz}Hz"
    }
}
