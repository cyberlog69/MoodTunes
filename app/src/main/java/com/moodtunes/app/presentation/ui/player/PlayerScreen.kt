package com.moodtunes.app.presentation.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.moodtunes.app.presentation.ui.theme.*

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val song = uiState.currentSong

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
                        Background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
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

                IconButton(onClick = { /* Queue */ }) {
                    Icon(
                        Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = "Queue",
                        tint = White
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ─── Album Art (Vinyl Disc) ──────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF2A2A3A),
                                Color(0xFF0A0A0F)
                            )
                        )
                    )
                    .rotate(if (uiState.isPlaying) rotation else rotation),
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
                        .size(140.dp)
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

            Spacer(Modifier.height(40.dp))

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
                    Text(
                        text = song?.artist ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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

            Spacer(Modifier.height(32.dp))

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

            Spacer(Modifier.height(24.dp))

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

            Spacer(Modifier.height(32.dp))

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
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
