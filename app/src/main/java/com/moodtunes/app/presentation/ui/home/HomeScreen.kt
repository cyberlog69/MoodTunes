package com.moodtunes.app.presentation.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.presentation.ui.components.MiniPlayer
import com.moodtunes.app.presentation.ui.theme.*

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToPlayer: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Animated background gradient that shifts with mood
        val gradientColor = uiState.selectedMood?.gradientStart ?: Color(0xFF1A0A2E)
        val animatedColor by animateColorAsState(
            targetValue = gradientColor.copy(alpha = 0.15f),
            animationSpec = tween(800),
            label = "bgGradient"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(animatedColor, Color.Transparent),
                        center = Offset.Zero,
                        radius = 900f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ─── Top Bar ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MoodTunes",
                        style = MaterialTheme.typography.headlineMedium,
                        color = White
                    )
                    Text(
                        text = "How are you feeling today?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            Icons.Rounded.BarChart,
                            contentDescription = "History",
                            tint = OnSurfaceVariant
                        )
                    }
                    IconButton(onClick = onNavigateToLibrary) {
                        Icon(
                            Icons.Rounded.LibraryMusic,
                            contentDescription = "Library",
                            tint = OnSurfaceVariant
                        )
                    }
                }
            }

            // ─── Mood Grid ──────────────────────────────────────────────────
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = if (uiState.currentSong != null) 120.dp else 32.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(MoodType.entries) { mood ->
                    MoodCard(
                        mood = mood,
                        isSelected = uiState.selectedMood == mood,
                        isLoading = uiState.isLoading && uiState.selectedMood == mood,
                        songCount = if (uiState.selectedMood == mood) uiState.moodSongs.size else null,
                        onClick = {
                            viewModel.onMoodSelected(mood)
                            onNavigateToPlayer()
                        }
                    )
                }
            }
        }

        // ─── Mini Player ────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.currentSong != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            uiState.currentSong?.let { song ->
                MiniPlayer(
                    song = song,
                    isPlaying = uiState.isPlaying,
                    mood = uiState.selectedMood,
                    onPlayPauseClick = { viewModel.playPause() },
                    onSkipPreviousClick = { viewModel.skipPrevious() },
                    onSkipNextClick = { viewModel.skipNext() },
                    onExpandClick = onNavigateToPlayer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(12.dp)
                )
            }
        }
    }
}

// ─── Mood Card Component ─────────────────────────────────────────────────────
@Composable
private fun MoodCard(
    mood: MoodType,
    isSelected: Boolean,
    isLoading: Boolean,
    songCount: Int?,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "moodPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isSelected) 1.03f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val elevation by animateFloatAsState(
        targetValue = if (isSelected) 16f else 4f,
        label = "elevation"
    )

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .scale(pulse),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.dp),
        border = if (isSelected) BorderStroke(2.dp, mood.gradientStart) else null
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            mood.gradientStart,
                            mood.gradientEnd
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Emoji
                Text(
                    text = mood.emoji,
                    style = MaterialTheme.typography.displaySmall
                )

                Column {
                    Text(
                        text = mood.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isLoading) "Loading…"
                        else songCount?.let { "$it songs" } ?: mood.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Loading indicator
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.TopEnd),
                    color = White,
                    strokeWidth = 2.dp
                )
            }

            // Selected checkmark
            if (isSelected && !isLoading) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
