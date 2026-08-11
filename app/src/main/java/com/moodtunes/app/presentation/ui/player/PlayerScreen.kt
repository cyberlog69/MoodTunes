package com.moodtunes.app.presentation.ui.player

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.mediarouter.app.MediaRouteButton
import coil.compose.AsyncImage
import com.google.android.gms.cast.framework.CastButtonFactory
import com.moodtunes.app.R
import com.moodtunes.app.domain.model.LyricsLine
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.presentation.ui.theme.*
import com.moodtunes.app.service.PlaybackError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val song = uiState.currentSong

    var activeSheet by remember { mutableStateOf(PlayerSheet.NONE) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Surface one-shot cast feedback messages.
    LaunchedEffect(uiState.castMessage) {
        uiState.castMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearCastMessage()
        }
    }

    val gradientStart = uiState.selectedMood?.gradientStart ?: EuphoricGradientStart
    val gradientEnd = uiState.selectedMood?.gradientEnd ?: EuphoricGradientEnd

    val animatedGradientStart by animateColorAsState(
        targetValue = gradientStart,
        animationSpec = tween(800),
        label = "gradStart"
    )
    val animatedGradientEnd by animateColorAsState(
        targetValue = gradientEnd,
        animationSpec = tween(800),
        label = "gradEnd"
    )

    // Vinyl spin animation
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        animatedGradientStart.copy(alpha = 0.8f),
                        animatedGradientEnd.copy(alpha = 0.95f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ─── Top Bar ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Now Playing",
                        style = MaterialTheme.typography.labelMedium,
                        color = White.copy(alpha = 0.7f)
                    )
                    uiState.selectedMood?.let { mood ->
                        Text(
                            text = "${mood.emoji} ${mood.displayName} Mood",
                            style = MaterialTheme.typography.labelLarge,
                            color = White
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Share the current song
                    // Visualizer Mode Cycle Button
                    IconButton(onClick = { viewModel.cycleVisualizerMode() }) {
                        Icon(
                            imageVector = when (uiState.visualizerMode) {
                                com.moodtunes.app.service.VisualizerMode.OFF -> Icons.Rounded.GraphicEq
                                com.moodtunes.app.service.VisualizerMode.BARS -> Icons.Rounded.Equalizer
                                com.moodtunes.app.service.VisualizerMode.PULSE_AURA -> Icons.Rounded.Grain
                                com.moodtunes.app.service.VisualizerMode.PARTICLES -> Icons.Rounded.AutoAwesome
                            },
                            contentDescription = "Visualizer: ${uiState.visualizerMode.title}",
                            tint = if (uiState.visualizerMode != com.moodtunes.app.service.VisualizerMode.OFF) White else White.copy(alpha = 0.5f)
                        )
                    }

                    val shareContext = LocalContext.current
                    IconButton(onClick = {
                        song?.let { shareSong(shareContext, it) }
                    }) {
                        Icon(
                            Icons.Rounded.Share,
                            contentDescription = "Share",
                            tint = White
                        )
                    }
                    // Chromecast device picker (framework button wired via CastButtonFactory)
                    AndroidView(
                        factory = { ctx ->
                            MediaRouteButton(ctx).apply {
                                visibility = View.VISIBLE
                                runCatching {
                                    CastButtonFactory.setUpMediaRouteButton(ctx, this)
                                }
                            }
                        },
                        modifier = Modifier.size(44.dp, 44.dp)
                    )
                    IconButton(onClick = { activeSheet = PlayerSheet.QUEUE }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = "Queue",
                            tint = White
                        )
                    }
                }
            }

            // Casting status
            if (uiState.isCasting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Cast,
                        contentDescription = "Casting",
                        tint = White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Casting to ${uiState.castDeviceName ?: "Chromecast"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = White
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ─── Visualizer & Vinyl Turntable ────────────────────────────────
            Box(
                modifier = Modifier
                    .size(310.dp),
                contentAlignment = Alignment.Center
            ) {
                // Live Reactive Canvas Visualizer
                com.moodtunes.app.presentation.ui.components.AudioVisualizerView(
                    mode = uiState.visualizerMode,
                    fftBands = uiState.fftBands,
                    primaryColor = animatedGradientStart,
                    secondaryColor = animatedGradientEnd,
                    turntableDiameter = 250.dp,
                    modifier = Modifier.fillMaxSize()
                )

                // Vinyl Disc
                Box(
                    modifier = Modifier
                        .size(250.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF2A2A3A),
                                    Color(0xFF0A0A0F)
                                )
                            )
                        )
                        .rotate(if (uiState.isPlaying) rotation else 0f),
                    contentAlignment = Alignment.Center
                ) {
                    // Vinyl grooves
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0x15FFFFFF),
                                        Color.Transparent,
                                        Color(0x10FFFFFF),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Album art in center
                    Box(
                        modifier = Modifier
                            .size(125.dp)
                            .clip(CircleShape)
                            .background(SurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = song?.albumArtUri,
                            contentDescription = "Album Art",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            onError = {}
                        )
                        if (song?.albumArtUri == null) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = OnSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }

                    // Center hole
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Background)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ─── Song Info ───────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song?.title ?: "No song playing",
                        style = MaterialTheme.typography.headlineSmall,
                        color = White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = song?.artist ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        song?.audioFormat?.let { format ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(format.badgeColorHex).copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = format.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(format.badgeColorHex),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = viewModel::toggleFavorite) {
                    Icon(
                        imageVector = if (song?.isFavorite == true)
                            Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (song?.isFavorite == true) FavoriteRed else White.copy(alpha = 0.6f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Active audio output device (Bluetooth / wired / etc.)
            uiState.audioOutput?.let { output ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (output.type == "Bluetooth")
                            Icons.Rounded.BluetoothAudio else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = null,
                        tint = White.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Playing on ${output.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = White.copy(alpha = 0.6f)
                    )
                }
            }

            // Playback error banner with Retry / Skip actions
            uiState.playbackError?.let { error ->
                PlaybackErrorBanner(
                    error = error,
                    onRetry = viewModel::retryPlayback,
                    onSkip = viewModel::skipOnError
                )
            }

            Spacer(Modifier.height(20.dp))

            // ─── Seek Bar ───────────────────────────────────────────────────
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = uiState.progress,
                    onValueChange = { viewModel.seekTo(it) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = White,
                        activeTrackColor = White,
                        inactiveTrackColor = White.copy(alpha = 0.3f)
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatMs(uiState.currentPositionMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = White.copy(alpha = 0.7f)
                    )
                    Text(
                        text = formatMs(uiState.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = White.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ─── Secondary Tools (Lyrics / Speed / EQ / Sleep / Crossfade / Smart) ─
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerToolButton(
                    icon = Icons.Rounded.Subtitles,
                    label = "Lyrics",
                    active = uiState.lyrics.isNotEmpty(),
                    onClick = { activeSheet = PlayerSheet.LYRICS }
                )
                PlayerToolButton(
                    icon = Icons.Rounded.Speed,
                    label = "${formatSpeedLabel(uiState.playbackSpeed)}×",
                    active = uiState.playbackSpeed != 1f,
                    onClick = { activeSheet = PlayerSheet.SPEED }
                )
                PlayerToolButton(
                    icon = Icons.Rounded.Equalizer,
                    label = "Equalizer",
                    active = uiState.isEqualizerEnabled || uiState.isBassBoostEnabled,
                    onClick = { activeSheet = PlayerSheet.EQUALIZER }
                )
                PlayerToolButton(
                    icon = Icons.Rounded.Bedtime,
                    label = "Sleep",
                    active = uiState.sleepTimerRemainingMs != null,
                    onClick = { activeSheet = PlayerSheet.SLEEP_TIMER }
                )
                PlayerToolButton(
                    icon = Icons.Rounded.SwapHoriz,
                    label = "Crossfade",
                    active = uiState.isCrossfadeEnabled,
                    onClick = { activeSheet = PlayerSheet.CROSSFADE }
                )
                PlayerToolButton(
                    icon = Icons.Rounded.AutoAwesome,
                    label = "Smart",
                    active = uiState.isSmartShuffleEnabled,
                    onClick = viewModel::toggleSmartShuffle
                )
            }

            Spacer(Modifier.height(12.dp))

            // ─── Playback Controls ───────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                IconButton(onClick = viewModel::toggleShuffle) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (uiState.isShuffleEnabled) animatedGradientStart else White.copy(alpha = 0.5f),
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Skip Previous
                IconButton(
                    onClick = viewModel::skipPrevious,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint = White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Play / Pause (large center button)
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(White),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = viewModel::playPause,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = if (uiState.isPlaying)
                                Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = animatedGradientEnd,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                // Skip Next
                IconButton(
                    onClick = viewModel::skipNext,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Repeat
                IconButton(onClick = viewModel::toggleRepeat) {
                    Icon(
                        imageVector = when (uiState.repeatMode) {
                            RepeatMode.ONE -> Icons.Rounded.RepeatOne
                            else -> Icons.Rounded.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = when (uiState.repeatMode) {
                            RepeatMode.OFF -> White.copy(alpha = 0.5f)
                            else -> animatedGradientStart
                        },
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ─── Album name ─────────────────────────────────────────────────
            song?.let {
                Text(
                    text = it.album,
                    style = MaterialTheme.typography.bodySmall,
                    color = White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // ─── Spotify-Style Live Lyrics Card ─────────────────────────────
            if (uiState.lyrics.isNotEmpty() || uiState.isLyricsLoading) {
                Spacer(Modifier.height(20.dp))
                SpotifyLyricsCard(
                    lyrics = uiState.lyrics,
                    currentPositionMs = uiState.currentPositionMs,
                    isLoading = uiState.isLyricsLoading,
                    backgroundColor = animatedGradientStart.copy(alpha = 0.28f),
                    onClick = { activeSheet = PlayerSheet.LYRICS }
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }

    // ─── Bottom sheets (queue / lyrics / speed / sleep / eq / crossfade) ─────
    PlayerBottomSheet(
        sheet = activeSheet,
        uiState = uiState,
        onDismiss = { activeSheet = PlayerSheet.NONE },
        viewModel = viewModel
    )
}

@Composable
private fun PlaybackErrorBanner(
    error: PlaybackError,
    onRetry: () -> Unit,
    onSkip: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = FavoriteRed.copy(alpha = 0.95f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodySmall,
                color = White,
                modifier = Modifier.weight(1f)
            )
            if (error.isRetryable) {
                TextButton(onClick = onRetry) {
                    Text("Retry", color = White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }
            TextButton(onClick = onSkip) {
                Text("Skip", color = White)
            }
        }
    }
}

@Composable
private fun PlayerToolButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) White else White.copy(alpha = 0.55f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) White else White.copy(alpha = 0.55f)
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun shareSong(context: Context, song: Song) {
    val text = buildString {
        append("\"${song.title}\" by ${song.artist}")
        if (song.album.isNotBlank()) append(" (${song.album})")
        append(" on MoodTunes")
        if (song.isStream) append("\n${song.uri}")
    }
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, song.title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(
        Intent.createChooser(sendIntent, context.getString(R.string.share_song))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private fun formatSpeedLabel(speed: Float): String {
    return if (speed % 1f == 0f) speed.toInt().toString()
    else String.format("%.1f", speed)
}

@Composable
private fun SpotifyLyricsCard(
    lyrics: List<LyricsLine>,
    currentPositionMs: Long,
    isLoading: Boolean,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    val currentLineIndex = remember(lyrics, currentPositionMs) {
        if (lyrics.isEmpty()) -1
        else {
            val idx = lyrics.indexOfLast { it.timeMs <= currentPositionMs }
            idx.coerceAtLeast(0)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, White.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Subtitles,
                        contentDescription = null,
                        tint = White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Lyrics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = White
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = White.copy(alpha = 0.15f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInFull,
                            contentDescription = "Expand Lyrics",
                            tint = White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    CircularProgressIndicator(
                        color = White,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Loading synced lyrics...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = White.copy(alpha = 0.7f)
                    )
                }
            } else if (lyrics.isNotEmpty()) {
                val line1 = lyrics.getOrNull(currentLineIndex)?.text?.takeIf { it.isNotBlank() }
                    ?: lyrics.firstOrNull { it.text.isNotBlank() }?.text ?: ""
                val line2 = lyrics.getOrNull(currentLineIndex + 1)?.text ?: ""
                val line3 = lyrics.getOrNull(currentLineIndex + 2)?.text ?: ""

                if (line1.isNotBlank()) {
                    Text(
                        text = line1,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                        color = White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (line2.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = line2,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (line3.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = line3,
                        style = MaterialTheme.typography.bodyMedium,
                        color = White.copy(alpha = 0.4f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
