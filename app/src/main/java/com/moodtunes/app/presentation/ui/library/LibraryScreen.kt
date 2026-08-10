package com.moodtunes.app.presentation.ui.library

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodtunes.app.domain.model.Playlist
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.presentation.ui.components.SongActionBottomSheet
import com.moodtunes.app.presentation.ui.components.SongItem
import com.moodtunes.app.presentation.ui.theme.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    onNavigateToPlaylist: (Long) -> Unit,
    onNavigateToSearch: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedSongForAction by remember { mutableStateOf<Song?>(null) }

    val displayedSongs = when (uiState.selectedTab) {
        LibraryTab.LOCAL -> uiState.filteredSongs
        LibraryTab.ONLINE_STREAM -> uiState.onlineStreamSongs
        LibraryTab.FAVORITES -> uiState.favoriteSongs
        else -> emptyList()
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    // Song Context Menu Sheet
    SongActionBottomSheet(
        song = selectedSongForAction,
        playlists = uiState.playlists,
        onDismiss = { selectedSongForAction = null },
        onPlay = {
            selectedSongForAction?.let { song ->
                viewModel.onSongSelected(song)
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
                viewModel.onToggleFavorite(song.id)
            }
        },
        onAddToPlaylist = { playlistId ->
            selectedSongForAction?.let { song ->
                viewModel.onAddToPlaylist(playlistId, song)
            }
        },
        onCreatePlaylist = { name ->
            selectedSongForAction?.let { song ->
                viewModel.onCreatePlaylist(name, song)
            }
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Subtle gradient background header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ─── Top Header Bar ─────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.Rounded.ArrowBackIosNew,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = "Songs Hub",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onNavigateToSearch) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    Text(
                        text = when (uiState.selectedTab) {
                            LibraryTab.LOCAL -> "${uiState.allSongs.size} offline"
                            LibraryTab.ONLINE_STREAM -> "${uiState.onlineStreamSongs.size} online"
                            LibraryTab.FAVORITES -> "${uiState.favoriteSongs.size} saved"
                            LibraryTab.PLAYLISTS -> "${uiState.playlists.size} lists"
                            LibraryTab.TOP_TRACKS -> "${uiState.mostPlayed.size} tracks"
                            LibraryTab.ALBUMS -> "${uiState.albums.size} albums"
                            LibraryTab.ARTISTS -> "${uiState.artists.size} artists"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // ─── Search Bar (hidden when browsing albums/artists) ────────────
            if (uiState.selectedTab == LibraryTab.LOCAL ||
                uiState.selectedTab == LibraryTab.FAVORITES ||
                uiState.selectedTab == LibraryTab.ALBUMS ||
                uiState.selectedTab == LibraryTab.ARTISTS
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    placeholder = {
                        Text("Search songs, artists, albums…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    singleLine = true
                )
            }

            // ─── Tabs ────────────────────────────────────────────────────────
            PrimaryScrollableTabRow(
                selectedTabIndex = LibraryTab.entries.indexOf(uiState.selectedTab),
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
                edgePadding = 8.dp,
                divider = {}
            ) {
                LibraryTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.onTabSelected(tab) },
                        text = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            when (uiState.selectedTab) {
                LibraryTab.ONLINE_STREAM -> OnlineStreamsContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    displayedSongs = displayedSongs,
                    onNavigateToPlayer = onNavigateToPlayer,
                    onMoreClick = { song -> selectedSongForAction = song }
                )
                LibraryTab.PLAYLISTS -> PlaylistsContent(
                    playlists = uiState.playlists,
                    onCreateClick = { newPlaylistName = ""; showCreateDialog = true },
                    onPlaylistClick = onNavigateToPlaylist
                )
                LibraryTab.ALBUMS -> AlbumsContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    onNavigateToPlayer = onNavigateToPlayer,
                    onMoreClick = { song -> selectedSongForAction = song }
                )
                LibraryTab.ARTISTS -> ArtistsContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    onNavigateToPlayer = onNavigateToPlayer,
                    onMoreClick = { song -> selectedSongForAction = song }
                )
                LibraryTab.LOCAL, LibraryTab.FAVORITES -> SongListContent(
                    uiState = uiState,
                    displayedSongs = displayedSongs,
                    onToggleFavorite = viewModel::onToggleFavorite,
                    onSongSelected = { song ->
                        viewModel.onSongSelected(song)
                        onNavigateToPlayer()
                    },
                    onMoreClick = { song -> selectedSongForAction = song }
                )
                LibraryTab.TOP_TRACKS -> SongListContent(
                    uiState = uiState,
                    displayedSongs = uiState.mostPlayed,
                    onToggleFavorite = viewModel::onToggleFavorite,
                    onSongSelected = { song ->
                        viewModel.onSongSelected(song)
                        onNavigateToPlayer()
                    },
                    onMoreClick = { song -> selectedSongForAction = song }
                )
            }
        }
    }

    // â”€â”€â”€ Create Playlist Dialog â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            viewModel.onCreatePlaylist(newPlaylistName.trim())
                            showCreateDialog = false
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// â”€â”€â”€ Playlists tab â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun PlaylistsContent(
    playlists: List<Playlist>,
    onCreateClick: () -> Unit,
    onPlaylistClick: (Long) -> Unit
) {
    Column {
        Button(
            onClick = onCreateClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Create New Playlist")
        }
        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "No playlists yet.\nCreate one to organize your music.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(playlists, key = { _, p -> p.id }) { _, playlist ->
                    Card(
                        onClick = { onPlaylistClick(playlist.id) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                modifier = Modifier.size(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.QueueMusic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${playlist.songCount} songs â€¢ ${playlist.formattedDuration}",
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
}

// ─── Albums tab ──────────────────────────────────────────────────────────────

@Composable
private fun AlbumsContent(
    uiState: LibraryUiState,
    viewModel: LibraryViewModel,
    onNavigateToPlayer: () -> Unit,
    onMoreClick: (Song) -> Unit = {}
) {
    val selectedAlbum = uiState.selectedAlbum
    if (selectedAlbum != null) {
        GroupDetailHeader(
            title = selectedAlbum.name,
            subtitle = selectedAlbum.artist,
            count = selectedAlbum.songs.size,
            onBack = viewModel::clearGroupSelection
        )
        LazyColumn(
            contentPadding = PaddingValues(bottom = 120.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(selectedAlbum.songs, key = { index, song -> "album_${song.id}_$index" }) { _, song ->
                SongItem(
                    song = song,
                    isPlaying = uiState.currentSongId == song.id && uiState.isPlaying,
                    onClick = {
                        viewModel.onSongSelected(song)
                        onNavigateToPlayer()
                    },
                    onFavoriteClick = { viewModel.onToggleFavorite(song.id) },
                    onMoreClick = { onMoreClick(song) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    } else {
        if (uiState.albums.isEmpty()) {
            EmptyLibraryMessage(text = "No albums found.")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(uiState.albums, key = { index, album -> "albumgroup_${album.name}_$index" }) { _, album ->
                    AlbumArtistCard(
                        title = album.name,
                        subtitle = "${album.artist} • ${album.songs.size} songs",
                        artUri = album.songs.firstOrNull()?.albumArtUri,
                        icon = Icons.Rounded.Album,
                        onClick = { viewModel.onAlbumSelected(album) }
                    )
                }
            }
        }
    }
}

// ─── Artists tab ─────────────────────────────────────────────────────────────

@Composable
private fun ArtistsContent(
    uiState: LibraryUiState,
    viewModel: LibraryViewModel,
    onNavigateToPlayer: () -> Unit,
    onMoreClick: (Song) -> Unit = {}
) {
    val selectedArtist = uiState.selectedArtist
    if (selectedArtist != null) {
        val artistSongs = uiState.filteredSongs.filter { it.artist == selectedArtist }
        GroupDetailHeader(
            title = selectedArtist,
            subtitle = "${artistSongs.size} songs",
            count = artistSongs.size,
            onBack = viewModel::clearGroupSelection
        )
        LazyColumn(
            contentPadding = PaddingValues(bottom = 120.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(artistSongs, key = { index, song -> "artist_${song.id}_$index" }) { _, song ->
                SongItem(
                    song = song,
                    isPlaying = uiState.currentSongId == song.id && uiState.isPlaying,
                    onClick = {
                        viewModel.onSongSelected(song)
                        onNavigateToPlayer()
                    },
                    onFavoriteClick = { viewModel.onToggleFavorite(song.id) },
                    onMoreClick = { onMoreClick(song) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    } else {
        if (uiState.artists.isEmpty()) {
            EmptyLibraryMessage(text = "No artists found.")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(uiState.artists, key = { _, artist -> artist }) { _, artist ->
                    val songs = uiState.filteredSongs.filter { it.artist == artist }
                    AlbumArtistCard(
                        title = artist,
                        subtitle = "${songs.size} songs",
                        artUri = songs.firstOrNull()?.albumArtUri,
                        icon = Icons.Rounded.Person,
                        onClick = { viewModel.onArtistSelected(artist) }
                    )
                }
            }
        }
    }
}

// ─── Local / Favorites list ──────────────────────────────────────────────────

@Composable
private fun SongListContent(
    uiState: LibraryUiState,
    displayedSongs: List<Song>,
    onToggleFavorite: (Long) -> Unit,
    onSongSelected: (Song) -> Unit,
    onMoreClick: (Song) -> Unit = {}
) {
    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    } else if (displayedSongs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (uiState.selectedTab == LibraryTab.FAVORITES)
                        Icons.Rounded.FavoriteBorder else Icons.Rounded.MusicOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (uiState.selectedTab == LibraryTab.FAVORITES)
                        "No favorites yet.\nHeart a song to add it here."
                    else "No local songs found on device.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 120.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(displayedSongs, key = { index, song -> "local_${song.id}_$index" }) { _, song ->
                SongItem(
                    song = song,
                    isPlaying = uiState.currentSongId == song.id && uiState.isPlaying,
                    onClick = { onSongSelected(song) },
                    onFavoriteClick = { onToggleFavorite(song.id) },
                    onMoreClick = { onMoreClick(song) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

// ─── Online streams ──────────────────────────────────────────────────────────

@Composable
private fun OnlineStreamsContent(
    uiState: LibraryUiState,
    viewModel: LibraryViewModel,
    displayedSongs: List<Song>,
    onNavigateToPlayer: () -> Unit,
    onMoreClick: (Song) -> Unit = {}
) {
    if (uiState.isLoadingOnline) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Loading online streams based on your settings...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else if (displayedSongs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "No online streams available.\nCheck your internet connection or settings.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.loadOnlineStreamSongs() }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Retry Stream")
                }
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 120.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Online Streaming",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Curated online streams based on your language preferences set in Settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        IconButton(onClick = { viewModel.loadOnlineStreamSongs() }) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = "Refresh",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item {
                val categories = listOf("Top Hits", "📻 Live Radio", "Trending Pop", "Acoustic & Chill", "Dance Party", "Lo-Fi Beats", "Rock Hits")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    items(categories) { cat ->
                        val isSelected = uiState.selectedCategory == cat
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.loadOnlineStreamSongs(cat) },
                            label = { Text(cat) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔥 ${uiState.selectedCategory}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${displayedSongs.size} tracks",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            itemsIndexed(displayedSongs, key = { index, song -> "online_${song.id}_$index" }) { _, song ->
                SongItem(
                    song = song,
                    isPlaying = uiState.currentSongId == song.id && uiState.isPlaying,
                    onClick = {
                        viewModel.onSongSelected(song)
                        onNavigateToPlayer()
                    },
                    onFavoriteClick = { viewModel.onToggleFavorite(song.id) },
                    onMoreClick = { onMoreClick(song) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

// â”€â”€â”€ Shared UI helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun GroupDetailHeader(
    title: String,
    subtitle: String,
    count: Int,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$subtitle â€¢ $count tracks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AlbumArtistCard(
    title: String,
    subtitle: String,
    artUri: android.net.Uri?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                if (artUri != null) {
                    coil.compose.AsyncImage(
                        model = artUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        onError = {}
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
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

@Composable
private fun EmptyLibraryMessage(text: String) {
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
