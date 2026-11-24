package com.moodtunes.app.presentation.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.moodtunes.app.domain.model.YouTubeItem
import com.moodtunes.app.domain.model.YouTubeShelf
import com.moodtunes.app.presentation.ui.components.MiniPlayer

@Composable
fun DiscoverScreen(
    viewModel: DiscoverViewModel = hiltViewModel(),
    isRoot: Boolean = false,
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToPlayer: () -> Unit,
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onOpenYouTubePage: (browseId: String, params: String?) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isRoot && onNavigateBack != null) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Rounded.ArrowBackIosNew,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MoodTunes",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "YouTube Music",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onNavigateToSearch) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(onClick = viewModel::refresh) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            when {
                uiState.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                uiState.isEmpty -> Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = uiState.error ?: "Nothing to discover right now.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = viewModel::refresh) { Text("Try again") }
                    }
                }

                else -> {
                    val bottomPadding = when {
                        isRoot -> 176.dp
                        uiState.currentSong != null -> 96.dp
                        else -> 24.dp
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)
                    ) {
                        discoverSection(
                            title = "Made for you",
                            shelves = uiState.homeShelves,
                            viewModel = viewModel,
                            onOpenYouTubePage = onOpenYouTubePage
                        )
                        discoverSection(
                            title = "Charts",
                            shelves = uiState.chartShelves,
                            viewModel = viewModel,
                            onOpenYouTubePage = onOpenYouTubePage
                        )
                        discoverSection(
                            title = "Moods & genres",
                            shelves = uiState.moodShelves,
                            viewModel = viewModel,
                            onOpenYouTubePage = onOpenYouTubePage
                        )
                        discoverSection(
                            title = "New releases",
                            shelves = uiState.releaseShelves,
                            viewModel = viewModel,
                            onOpenYouTubePage = onOpenYouTubePage
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            uiState.currentSong?.let { song ->
                MiniPlayer(
                    song = song,
                    isPlaying = uiState.isPlaying,
                    onPlayPauseClick = viewModel::playPause,
                    onExpandClick = onNavigateToPlayer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            if (isRoot) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        selected = true,
                        onClick = {},
                        icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onNavigateToLibrary,
                        icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
                        label = { Text("Library") }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onNavigateToSearch,
                        icon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        label = { Text("Search") }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onNavigateToSettings,
                        icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                        label = { Text("Settings") }
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.discoverSection(
    title: String,
    shelves: List<YouTubeShelf>,
    viewModel: DiscoverViewModel,
    onOpenYouTubePage: (String, String?) -> Unit
) {
    if (shelves.isEmpty()) return
    item(key = "header_$title") {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp)
        )
    }
    items(shelves, key = { "shelf_${title}_${it.title}_${it.items.firstOrNull()?.id}" }) { shelf ->
        YouTubeShelfRow(
            shelf = shelf,
            onPlay = { index -> viewModel.playShelf(shelf, index) },
            onOpenYouTubePage = onOpenYouTubePage
        )
    }
}

@Composable
private fun YouTubeShelfRow(
    shelf: YouTubeShelf,
    onPlay: (Int) -> Unit,
    onOpenYouTubePage: (String, String?) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = shelf.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (shelf.playableSongs.isNotEmpty()) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Play shelf",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { onPlay(0) }
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(
                shelf.items,
                key = { index, item -> "${item.id}_$index" }
            ) { _, item ->
                YouTubeItemCard(
                    item = item,
                    onClick = {
                        when (item) {
                            is YouTubeItem.Track -> {
                                // playableSongs drops non-track items, so map the
                                // tapped card to its position there, not in items.
                                val index = shelf.playableSongs.indexOfFirst {
                                    it.videoId == item.song.videoId
                                }
                                onPlay(index.coerceAtLeast(0))
                            }
                            is YouTubeItem.Category -> onOpenYouTubePage(item.browseId, item.params)
                            else -> onOpenYouTubePage(item.id, null)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun YouTubeItemCard(
    item: YouTubeItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(142.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (item is YouTubeItem.Artist) 1f else 1.15f)
                .clip(RoundedCornerShape(if (item is YouTubeItem.Artist) 70.dp else 12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = item.thumbnail,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (item.thumbnail == null) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (item.subtitle.isNotBlank()) {
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
