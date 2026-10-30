package com.moodtunes.app.presentation.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import com.moodtunes.app.data.remote.recommendation.DayTimeSlot
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.presentation.ui.components.CircularGlassButton
import com.moodtunes.app.presentation.ui.components.GlassCard
import com.moodtunes.app.presentation.ui.components.MiniPlayer
import com.moodtunes.app.presentation.ui.components.MoodSelectorBottomSheet
import com.moodtunes.app.presentation.ui.components.SongActionBottomSheet
import com.moodtunes.app.presentation.ui.components.SpeedDialTile
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
    var showMoodSheet by remember { mutableStateOf(false) }

    // Mood Selector Bottom Sheet
    if (showMoodSheet) {
        MoodSelectorBottomSheet(
            selectedMood = uiState.selectedMood,
            onDismiss = { showMoodSheet = false },
            onClearMood = { viewModel.clearMood() },
            onMoodSelected = { mood ->
                viewModel.onMoodSelected(mood)
                onNavigateToPlayer()
            }
        )
    }

    // Song Context Menu Sheet
    SongActionBottomSheet(
        song = selectedSongForAction,
        playlists = uiState.userPlaylists,
        onDismiss = { selectedSongForAction = null },
        onPlay = {
            selectedSongForAction?.let { song ->
                viewModel.playSong(song)
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
        },
        onDownload = { song ->
            viewModel.downloadSong(song)
        },
        isDownloaded = selectedSongForAction?.let { viewModel.isDownloaded(it.id) } ?: false
    )

    // Triggered by app shortcut deep links (moodtunes://mood/<MOOD>).
    LaunchedEffect(deepLinkMood) {
        if (deepLinkMood != null) {
            viewModel.onMoodSelected(deepLinkMood)
            onNavigateToPlayer()
        }
    }

    // Dynamic greeting & date calculation
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = remember(hour) {
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val todayDate = remember {
        val locale = Locale.getDefault()
        SimpleDateFormat("EEEE d MMMM", locale).format(Date()).uppercase(locale)
    }
    val quickPicks = remember(uiState.forYou, uiState.recentlyPlayed) {
        (uiState.forYou.ifEmpty { uiState.recentlyPlayed }).take(12)
    }
    val extraRecent = remember(uiState.recentlyPlayed, quickPicks) {
        uiState.recentlyPlayed.filter { it.id !in quickPicks.map { p -> p.id } }.take(10)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF131018))
    ) {
        // Soft ambient warm backlight glow at top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFE55D6C).copy(alpha = 0.18f),
                            Color(0xFF7B2CBF).copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = androidx.compose.ui.geometry.Offset(450f, 150f),
                        radius = 850f
                    )
                )
        )

        // Main Scrollable Content
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = if (uiState.currentSong != null) 160.dp else 100.dp
            )
        ) {
            // ─── 1. Top Header ──────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = todayDate,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                fontSize = 11.sp
                            ),
                            color = Color.White.copy(alpha = 0.55f)
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 32.sp
                            ),
                            color = Color.White
                        )
                    }

                    // Top Action Buttons: Moods, Search, Settings
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularGlassButton(
                            icon = Icons.Rounded.AutoAwesome,
                            contentDescription = "Moods",
                            onClick = { showMoodSheet = true },
                            tint = Color(0xFFFFD166),
                            backgroundColor = Color(0xFF261D2B).copy(alpha = 0.7f)
                        )
                        CircularGlassButton(
                            icon = Icons.Rounded.Search,
                            contentDescription = "Search",
                            onClick = onNavigateToSearch,
                            tint = Color.White.copy(alpha = 0.9f),
                            backgroundColor = Color(0xFF261D2B).copy(alpha = 0.7f)
                        )
                        CircularGlassButton(
                            icon = Icons.Rounded.Person,
                            contentDescription = "Settings",
                            onClick = onNavigateToSettings,
                            tint = Color.White.copy(alpha = 0.9f),
                            backgroundColor = Color(0xFF261D2B).copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // ─── 2. Hero Replay Card ────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .height(160.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFFF2A6D), // Vibrant red-pink
                                    Color(0xFF9130F4), // Purple violet
                                    Color(0xFF00B4D8)  // Bright cyan-blue
                                )
                            )
                        )
                        .clickable(onClick = onNavigateToHistory)
                        .padding(22.dp)
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.BottomStart)
                    ) {
                        Text(
                            text = "REPLAY 2026",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.1.sp
                            ),
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = uiState.totalListeningTimeFormatted,
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 36.sp
                            ),
                            color = Color.White
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = uiState.topMood?.let { "Top mood: ${it.displayName} • See all stats" }
                                ?: "See your top songs, artists and genres",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                    }

                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.TopEnd)
                    )
                }
            }

            // ─── 3. Speed Dial 2-Column Grid (6 Items) ──────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Row 1: Liked songs | Downloads
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SpeedDialTile(
                            title = "Liked songs",
                            icon = Icons.Rounded.Favorite,
                            iconTint = Color(0xFF0E1A34),
                            iconContainerColor = Color(0xFF8EB9FF),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (uiState.favoriteSongs.isNotEmpty()) {
                                    viewModel.playFavoriteSongs()
                                    onNavigateToPlayer()
                                } else {
                                    onNavigateToLibrary()
                                }
                            }
                        )
                        SpeedDialTile(
                            title = "Downloads",
                            icon = Icons.Rounded.FileDownloadDone,
                            iconTint = Color(0xFF0E1A34),
                            iconContainerColor = Color(0xFF8EB9FF),
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToLibrary
                        )
                    }

                    // Row 2: Moods | Replay
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SpeedDialTile(
                            title = uiState.selectedMood?.displayName ?: "Moods",
                            icon = Icons.Rounded.AutoAwesome,
                            iconTint = Color(0xFF2C1038),
                            iconContainerColor = Color(0xFFE898FF),
                            modifier = Modifier.weight(1f),
                            onClick = { showMoodSheet = true }
                        )
                        SpeedDialTile(
                            title = "Replay",
                            icon = Icons.Rounded.History,
                            iconTint = Color(0xFF231B3A),
                            iconContainerColor = Color(0xFFC7B8FF),
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToHistory
                        )
                    }

                    // Row 3: Dynamic recent songs / albums
                    val recentSong1 = uiState.recentlyPlayed.getOrNull(0)
                        ?: uiState.forYou.getOrNull(0)
                    val recentSong2 = uiState.recentlyPlayed.getOrNull(1)
                        ?: uiState.forYou.getOrNull(1)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SpeedDialTile(
                            title = recentSong1?.title ?: "Chill Mix",
                            thumbnailUri = recentSong1?.albumArtUri,
                            icon = if (recentSong1?.albumArtUri == null) Icons.Rounded.MusicNote else null,
                            iconTint = Color.White,
                            iconContainerColor = Color(0xFF382F42),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                recentSong1?.let {
                                    viewModel.playSong(it)
                                    onNavigateToPlayer()
                                } ?: onNavigateToLibrary()
                            }
                        )
                        SpeedDialTile(
                            title = recentSong2?.title ?: "Top Hits",
                            thumbnailUri = recentSong2?.albumArtUri,
                            icon = if (recentSong2?.albumArtUri == null) Icons.Rounded.MusicNote else null,
                            iconTint = Color.White,
                            iconContainerColor = Color(0xFF382F42),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                recentSong2?.let {
                                    viewModel.playSong(it)
                                    onNavigateToPlayer()
                                } ?: onNavigateToLibrary()
                            }
                        )
                    }
                }
            }

            // ─── 4. Quick picks Section ─────────────────────────────────────
            if (quickPicks.isNotEmpty()) {
                item {
                    Text(
                        text = "Quick picks",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = Color.White,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp)
                    )
                }

                itemsIndexed(
                    quickPicks,
                    key = { index, song -> "pick_${song.id}_$index" }
                ) { index, song ->
                    QuickPickRow(
                        song = song,
                        isDownloaded = viewModel.isDownloaded(song.id),
                        onClick = {
                            viewModel.playSong(song)
                            onNavigateToPlayer()
                        },
                        onMoreClick = { selectedSongForAction = song }
                    )
                }
            }

            // ─── 4b. Contextual Time-of-Day Adaptive Rail ────────────────────
            if (uiState.timeOfDaySongs.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = when (DayTimeSlot.currentSlot()) {
                                    DayTimeSlot.MORNING -> Icons.Rounded.WbSunny
                                    DayTimeSlot.AFTERNOON -> Icons.Rounded.Bolt
                                    DayTimeSlot.EVENING_NIGHT -> Icons.Rounded.NightlightRound
                                },
                                contentDescription = null,
                                tint = when (DayTimeSlot.currentSlot()) {
                                    DayTimeSlot.MORNING -> Color(0xFFFFB703)
                                    DayTimeSlot.AFTERNOON -> Color(0xFF00F5D4)
                                    DayTimeSlot.EVENING_NIGHT -> Color(0xFF9D4EDD)
                                },
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = uiState.timeOfDayTitle,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                ),
                                color = Color.White
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = uiState.timeOfDaySubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                }

                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(uiState.timeOfDaySongs, key = { index, song -> "tod_${song.id}_$index" }) { index, song ->
                            TimeOfDaySongCard(
                                song = song,
                                onClick = {
                                    viewModel.playTimeOfDay(index)
                                    onNavigateToPlayer()
                                },
                                onMoreClick = { selectedSongForAction = song }
                            )
                        }
                    }
                }
            }

            // ─── 5. Recently Played (if distinct) ───────────────────────────
            if (extraRecent.isNotEmpty()) {
                item {
                    Text(
                        text = "Recently played",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = Color.White,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp)
                    )
                }
                itemsIndexed(
                    extraRecent,
                    key = { index, song -> "recent_${song.id}_$index" }
                ) { index, song ->
                    QuickPickRow(
                        song = song,
                        isDownloaded = viewModel.isDownloaded(song.id),
                        onClick = {
                            viewModel.playSong(song)
                            onNavigateToPlayer()
                        },
                        onMoreClick = { selectedSongForAction = song }
                    )
                }
            }
        }

        // ─── Bottom Floating Player & Navigation Bar ────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            // Floating MiniPlayer
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
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            // Frosted Glass Bottom Navigation Bar matching Screenshot 2
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F0D14).copy(alpha = 0.95f))
                    .border(
                        width = 0.6.dp,
                        brush = Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)
                        ),
                        shape = androidx.compose.ui.graphics.RectangleShape
                    )
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Home (Selected Tab with pill)
                    FrostedNavItem(
                        icon = Icons.Rounded.Home,
                        label = "Home",
                        isSelected = true,
                        onClick = { /* Already on Home */ }
                    )

                    // Library
                    FrostedNavItem(
                        icon = Icons.Rounded.MusicNote,
                        label = "Library",
                        isSelected = false,
                        onClick = onNavigateToLibrary
                    )

                    // Search
                    FrostedNavItem(
                        icon = Icons.Rounded.Search,
                        label = "Search",
                        isSelected = false,
                        onClick = onNavigateToSearch
                    )

                    // Replay / Stats
                    FrostedNavItem(
                        icon = Icons.Rounded.AutoAwesome,
                        label = "Replay",
                        isSelected = false,
                        onClick = onNavigateToHistory
                    )
                }
            }
        }
    }
}

/**
 * Individual Quick Pick song row matching Screenshot 2.
 */
@Composable
private fun QuickPickRow(
    song: Song,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Thumbnail Artwork
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = {}
            )
            if (song.albumArtUri == null) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Title and Subtitle
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isDownloaded) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Downloaded",
                        tint = Color(0xFF4EE49B),
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    text = "${song.artist} • ${song.album.ifBlank { "Single" }}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = Color.White.copy(alpha = 0.60f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Context menu button (three dots)
        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "More actions",
                tint = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Modern frosted navigation tab item with pill background when selected.
 */
@Composable
private fun FrostedNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.16f))
                    .padding(horizontal = 18.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 10.sp
            ),
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.55f)
        )
    }
}

@Composable
private fun TimeOfDaySongCard(
    song: Song,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(148.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1E1A29).copy(alpha = 0.70f))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF2C243B)),
            contentAlignment = Alignment.Center
        ) {
            if (song.albumArtUri != null) {
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.40f),
                    modifier = Modifier.size(36.dp)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            ),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp
            ),
            color = Color.White.copy(alpha = 0.65f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
