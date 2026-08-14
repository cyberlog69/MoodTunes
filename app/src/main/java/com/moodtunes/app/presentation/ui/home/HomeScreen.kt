package com.moodtunes.app.presentation.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.presentation.ui.components.MiniPlayer
import com.moodtunes.app.presentation.ui.components.SongActionBottomSheet
import com.moodtunes.app.presentation.ui.theme.*

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    deepLinkMood: MoodType? = null,
    onNavigateToPlayer: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSearch: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedSongForAction by remember { mutableStateOf<Song?>(null) }

    // Song Context Menu Sheet
    SongActionBottomSheet(
        song = selectedSongForAction,
        playlists = uiState.userPlaylists,
        onDismiss = { selectedSongForAction = null },
        onPlay = {
            selectedSongForAction?.let { song ->
                viewModel.playForYou(0)
                onNavigateToPlayer()
            }
        },
        onPlayNext = {
            selectedSongForAction?.let { song ->
                viewModel.playNext(song)
            }
        },
        onAddToQueue = {
            selectedSongForAction?.let { song ->
                viewModel.addToQueue(song)
            }
        },
        onToggleFavorite = {
            selectedSongForAction?.let { song ->
                viewModel.toggleFavorite(song)
            }
        },
        onAddToPlaylist = { playlistId ->
            selectedSongForAction?.let { song ->
                viewModel.addToPlaylist(playlistId, song)
            }
        },
        onCreatePlaylist = { name ->
            selectedSongForAction?.let { song ->
                viewModel.createPlaylist(name, song)
            }
        }
    )

    // Triggered by app shortcut deep links (moodtunes://mood/<MOOD>).
    LaunchedEffect(deepLinkMood) {
        if (deepLinkMood != null) {
            viewModel.onMoodSelected(deepLinkMood)
            onNavigateToPlayer()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Animated gradient background accent
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            (uiState.selectedMood?.gradientStart ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.25f),
                            Color.Transparent
                        ),
                        center = Offset(500f, 100f),
                        radius = 800f
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
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    com.moodtunes.app.presentation.ui.components.MoodTunesLogoBadge(size = 42.dp)
                    Column {
                        Text(
                            text = "MoodTunes",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "How are you feeling today?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            Icons.Rounded.BarChart,
                            contentDescription = "History",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onNavigateToLibrary) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = "Songs",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ─── Quick Search Launch Bar ────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clickable(onClick = onNavigateToSearch)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Search songs, artists, albums, streams...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ─── Mood Grid + Discovery Rails ────────────────────────────────
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = if (uiState.currentSong != null) 140.dp else 90.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (uiState.forYou.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(title = "For You", subtitle = "Based on your favorites and top mood")
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SongRail(
                            songs = uiState.forYou,
                            onPlay = { index ->
                                viewModel.playForYou(index)
                                onNavigateToPlayer()
                            },
                            onMoreClick = { song -> selectedSongForAction = song }
                        )
                    }
                }
                if (uiState.recentlyPlayed.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(title = "Recently Played", subtitle = null)
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SongRail(
                            songs = uiState.recentlyPlayed,
                            onPlay = { index ->
                                viewModel.playRecentlyPlayed(index)
                                onNavigateToPlayer()
                            },
                            onMoreClick = { song -> selectedSongForAction = song }
                        )
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(title = "Moods", subtitle = "Pick how you feel and we'll match the music")
                }
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

        // ─── Bottom Navigation Dock ─────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            // Mini Player if active
            AnimatedVisibility(
                visible = uiState.currentSong != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
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
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            // Bottom Navigation Bar with "Songs" button
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                NavigationBarItem(
                    selected = true,
                    onClick = { /* Already on Home */ },
                    icon = { Icon(Icons.Rounded.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToLibrary,
                    icon = { Icon(Icons.Rounded.MusicNote, contentDescription = "Songs") },
                    label = { Text("Songs") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToHistory,
                    icon = { Icon(Icons.Rounded.BarChart, contentDescription = "Stats") },
                    label = { Text("Stats") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToSettings,
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    }
}

// ─── Section Header ──────────────────────────────────────────────────────────
@Composable
private fun SectionHeader(title: String, subtitle: String?) {
    Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Horizontal Song Rail ────────────────────────────────────────────────────
@Composable
private fun SongRail(
    songs: List<Song>,
    onPlay: (Int) -> Unit,
    onMoreClick: (Song) -> Unit = {}
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
    ) {
        itemsIndexed(songs, key = { index, song -> "rail_${song.id}_$index" }) { index, song ->
            RailCard(
                song = song,
                onClick = { onPlay(index) },
                onLongClick = { onMoreClick(song) }
            )
        }
    }
}

// ─── Compact Rail Card ───────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RailCard(
    song: Song,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .width(148.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (song.albumArtUri != null) {
                    AsyncImage(
                        model = song.albumArtUri,
                        contentDescription = "Album art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                }
                // Play overlay
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.dp)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = mood.emoji,
                        style = MaterialTheme.typography.displaySmall
                    )
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp
                        )
                    } else if (songCount != null) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.25f)
                        ) {
                            Text(
                                text = "$songCount songs",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Column {
                    Text(
                        text = mood.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Text(
                        text = mood.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
