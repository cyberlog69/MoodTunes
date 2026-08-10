package com.moodtunes.app.presentation.ui.search

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.presentation.ui.components.MiniPlayer
import com.moodtunes.app.presentation.ui.components.SongActionBottomSheet
import com.moodtunes.app.presentation.ui.components.SongItem
import com.moodtunes.app.presentation.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    onNavigateToPlaylist: (Long) -> Unit
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
                viewModel.playSong(listOf(song), 0)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ─── Search Bar Header ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.Rounded.ArrowBackIosNew,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                TextField(
                    value = uiState.query,
                    onValueChange = viewModel::onQueryChanged,
                    placeholder = {
                        Text(
                            "Search songs, artists, albums, streams...",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (uiState.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChanged("") }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
            }

            // ─── Filter Chips Row ────────────────────────────────────────────
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SearchFilter.entries) { filter ->
                    FilterChip(
                        selected = uiState.selectedFilter == filter,
                        onClick = { viewModel.onFilterSelected(filter) },
                        label = { Text(filter.displayName) },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            // ─── Content Area ────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (uiState.query.isBlank()) {
                    // Empty Query State: Recent Searches & Trending Suggestions
                    EmptyQueryContent(
                        recentSearches = uiState.recentSearches,
                        onRecentClick = viewModel::onQueryChanged,
                        onDeleteRecent = viewModel::removeRecentSearch,
                        onClearAllRecent = viewModel::clearRecentSearches,
                        onSuggestedClick = viewModel::onQueryChanged
                    )
                } else if (uiState.isEmpty) {
                    // No Results State
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.SearchOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "No results found for \"${uiState.query}\"",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Check the spelling or try searching for another song, artist, or genre.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    // Search Results List
                    SearchResultsList(
                        uiState = uiState,
                        onSongClick = { songs, index ->
                            viewModel.playSong(songs, index)
                            onNavigateToPlayer()
                        },
                        onFavoriteClick = viewModel::toggleFavorite,
                        onMoreClick = { song -> selectedSongForAction = song },
                        onArtistClick = { artistName -> viewModel.onQueryChanged(artistName) },
                        onAlbumClick = { albumName -> viewModel.onQueryChanged(albumName) },
                        onPlaylistClick = onNavigateToPlaylist
                    )
                }
            }
        }

        // ─── MiniPlayer Dock ─────────────────────────────────────────────────
        uiState.currentSong?.let { song ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                MiniPlayer(
                    song = song,
                    isPlaying = uiState.isPlaying,
                    mood = null,
                    onPlayPauseClick = viewModel::playPause,
                    onExpandClick = onNavigateToPlayer
                )
            }
        }
    }
}

@Composable
private fun SearchResultsList(
    uiState: SearchUiState,
    onSongClick: (List<Song>, Int) -> Unit,
    onFavoriteClick: (Song) -> Unit,
    onMoreClick: (Song) -> Unit,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onPlaylistClick: (Long) -> Unit
) {
    val bottomPadding = if (uiState.currentSong != null) 90.dp else 24.dp

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)
    ) {
        // Loading indicator for online search
        if (uiState.isSearchingOnline) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Searching online music streams...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ─── 1. Local Songs ──────────────────────────────────────────────────
        if (uiState.selectedFilter == SearchFilter.ALL || uiState.selectedFilter == SearchFilter.SONGS) {
            if (uiState.localSongs.isNotEmpty()) {
                item {
                    SectionTitle(
                        icon = Icons.Rounded.PhoneAndroid,
                        title = "Local Songs",
                        count = uiState.localSongs.size
                    )
                }
                itemsIndexed(uiState.localSongs, key = { _, song -> "local_${song.id}" }) { index, song ->
                    SongItem(
                        song = song,
                        isPlaying = uiState.currentSong?.id == song.id,
                        onClick = { onSongClick(uiState.localSongs, index) },
                        onFavoriteClick = { onFavoriteClick(song) },
                        onMoreClick = { onMoreClick(song) }
                    )
                }
            }
        }

        // ─── 2. Online Streams ───────────────────────────────────────────────
        if (uiState.selectedFilter == SearchFilter.ALL || uiState.selectedFilter == SearchFilter.ONLINE) {
            if (uiState.onlineSongs.isNotEmpty()) {
                item {
                    SectionTitle(
                        icon = Icons.Rounded.Cloud,
                        title = "Online Music & Streams",
                        count = uiState.onlineSongs.size
                    )
                }
                itemsIndexed(uiState.onlineSongs, key = { index, song -> "online_${song.id}_$index" }) { index, song ->
                    SongItem(
                        song = song,
                        isPlaying = uiState.currentSong?.id == song.id,
                        onClick = { onSongClick(uiState.onlineSongs, index) },
                        onFavoriteClick = { onFavoriteClick(song) },
                        onMoreClick = { onMoreClick(song) }
                    )
                }
            }
        }

        // ─── 3. Artists ──────────────────────────────────────────────────────
        if (uiState.selectedFilter == SearchFilter.ALL || uiState.selectedFilter == SearchFilter.ARTISTS) {
            if (uiState.artists.isNotEmpty()) {
                item {
                    SectionTitle(
                        icon = Icons.Rounded.Person,
                        title = "Artists",
                        count = uiState.artists.size
                    )
                }
                items(uiState.artists, key = { "artist_${it.artistName}" }) { artist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onArtistClick(artist.artistName) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = artist.artistName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${artist.songs.size} tracks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ─── 4. Albums ───────────────────────────────────────────────────────
        if (uiState.selectedFilter == SearchFilter.ALL || uiState.selectedFilter == SearchFilter.ALBUMS) {
            if (uiState.albums.isNotEmpty()) {
                item {
                    SectionTitle(
                        icon = Icons.Rounded.Album,
                        title = "Albums",
                        count = uiState.albums.size
                    )
                }
                items(uiState.albums, key = { "album_${it.albumName}_${it.artistName}" }) { album ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAlbumClick(album.albumName) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            val art = album.songs.firstOrNull()?.albumArtUri
                            AsyncImage(
                                model = art,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            if (art == null) {
                                Icon(
                                    Icons.Rounded.Album,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = album.albumName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${album.artistName} • ${album.songs.size} tracks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ─── 5. Playlists ────────────────────────────────────────────────────
        if (uiState.selectedFilter == SearchFilter.ALL || uiState.selectedFilter == SearchFilter.PLAYLISTS) {
            if (uiState.matchedPlaylists.isNotEmpty()) {
                item {
                    SectionTitle(
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        title = "Playlists",
                        count = uiState.matchedPlaylists.size
                    )
                }
                items(uiState.matchedPlaylists, key = { "playlist_${it.id}" }) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlaylistClick(playlist.id) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${playlist.songCount} tracks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(6.dp))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyQueryContent(
    recentSearches: List<String>,
    onRecentClick: (String) -> Unit,
    onDeleteRecent: (String) -> Unit,
    onClearAllRecent: () -> Unit,
    onSuggestedClick: (String) -> Unit
) {
    val trendingVibes = listOf(
        "Bollywood", "Lo-Fi Beats", "Acoustic Chill", "Punjabi Pop",
        "Tamil Hits", "Telugu Beats", "Rock Anthem", "EDM Dance",
        "Deep Sleep", "Workout Energy", "Instrumental Piano", "Global Radio"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Recent Searches
        if (recentSearches.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Searches",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = onClearAllRecent) {
                    Text("Clear All", color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(4.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                recentSearches.forEach { query ->
                    InputChip(
                        selected = false,
                        onClick = { onRecentClick(query) },
                        label = { Text(query) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.History,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        trailingIcon = {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Delete",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onDeleteRecent(query) }
                            )
                        },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        // Trending & Explore Suggestions
        Text(
            text = "Explore & Trending Vibes",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Tap any vibe to discover live online streams & local tracks",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            trendingVibes.forEach { vibe ->
                SuggestionChip(
                    onClick = { onSuggestedClick(vibe) },
                    label = { Text(vibe) },
                    icon = {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                )
            }
        }
    }
}
