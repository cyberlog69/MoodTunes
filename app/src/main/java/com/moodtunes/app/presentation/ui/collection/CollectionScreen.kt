package com.moodtunes.app.presentation.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
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
import com.moodtunes.app.domain.model.Playlist
import com.moodtunes.app.domain.model.YouTubeItem
import com.moodtunes.app.presentation.ui.components.MiniPlayer
import com.moodtunes.app.presentation.ui.components.SongItem

@Composable
fun CollectionScreen(
    viewModel: CollectionViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onOpenYouTubePage: (browseId: String, params: String?) -> Unit,
    onNavigateToPlaylist: (Long) -> Unit
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
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.Rounded.ArrowBackIosNew,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Your Library",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (uiState.isSignedIn) {
                            uiState.accountName.ifBlank { "Signed in to YouTube Music" }
                        } else {
                            "Your YouTube Music library"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onNavigateToAccount) {
                    Icon(
                        Icons.Rounded.AccountCircle,
                        contentDescription = "YouTube account",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = viewModel::refresh) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!uiState.isSignedIn) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Favorites and playlists work without an account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onNavigateToAccount) { Text("Sign in") }
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(CollectionTab.entries) { tab ->
                    FilterChip(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        label = { Text(tab.label) },
                        shape = RoundedCornerShape(18.dp)
                    )
                }
            }

            val bottomPadding = if (uiState.currentSong != null) 96.dp else 24.dp
            if (uiState.selectedTab.requiresYouTube && !uiState.isSignedIn) {
                SignInPrompt(onNavigateToAccount)
            } else {
                when {
                    uiState.isLoading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }

                    uiState.error != null -> Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = uiState.error.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = viewModel::refresh) { Text("Try again") }
                        }
                    }

                    uiState.selectedTab == CollectionTab.PLAYLISTS -> {
                        if (uiState.localPlaylists.isEmpty()) {
                            EmptyCollectionMessage("No playlists yet. Save a song to a playlist from its menu.")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)
                            ) {
                                items(uiState.localPlaylists, key = { "playlist_${it.id}" }) { playlist ->
                                    PlaylistRow(
                                        playlist = playlist,
                                        onClick = { onNavigateToPlaylist(playlist.id) }
                                    )
                                }
                            }
                        }
                    }

                    uiState.songs.isNotEmpty() -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)
                    ) {
                        itemsIndexed(
                            uiState.songs,
                            key = { index, song -> "lib_song_${song.videoId ?: song.id}_$index" }
                        ) { index, song ->
                            SongItem(
                                song = song,
                                isPlaying = uiState.currentSong?.id == song.id,
                                onClick = {
                                    viewModel.playSong(uiState.songs, index)
                                    onNavigateToPlayer()
                                },
                                onFavoriteClick = { viewModel.toggleFavorite(song) },
                                onMoreClick = null
                            )
                        }
                    }

                    else -> {
                        val catalogue = uiState.shelves.catalogueItems()
                        if (catalogue.isEmpty()) {
                            EmptyCollectionMessage(
                                if (uiState.selectedTab == CollectionTab.FAVORITES) {
                                    "No favorites yet. Tap the heart on any song to save it here."
                                } else {
                                    "Nothing here yet. Save songs, albums or playlists on YouTube Music and pull to refresh."
                                }
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)
                            ) {
                                items(catalogue, key = { it.id }) { item ->
                                    CatalogueRow(
                                        item = item,
                                        onClick = {
                                            when (item) {
                                                is YouTubeItem.Category ->
                                                    onOpenYouTubePage(item.browseId, item.params)
                                                else -> onOpenYouTubePage(item.id, null)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

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
                    onPlayPauseClick = viewModel::playPause,
                    onExpandClick = onNavigateToPlayer
                )
            }
        }
    }
}

@Composable
private fun EmptyCollectionMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlaylistRow(playlist: Playlist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (playlist.coverUri != null) {
                AsyncImage(
                    model = playlist.coverUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${playlist.songCount} songs",
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

@Composable
private fun SignInPrompt(onNavigateToAccount: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.AccountCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Bring your YouTube Music library into MoodTunes",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Sign in to see liked songs, albums, artists and playlists from your YouTube Music account.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onNavigateToAccount) { Text("Connect YouTube Music") }
    }
}

@Composable
private fun CatalogueRow(item: YouTubeItem, onClick: () -> Unit) {
    val icon = when (item) {
        is YouTubeItem.Artist -> Icons.Rounded.Person
        is YouTubeItem.Album -> Icons.Rounded.Album
        is YouTubeItem.Playlist -> Icons.AutoMirrored.Rounded.QueueMusic
        is YouTubeItem.Track -> Icons.Rounded.PlayArrow
        is YouTubeItem.Category -> Icons.Rounded.Refresh
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(if (item is YouTubeItem.Artist) 26.dp else 10.dp))
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
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
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
        Icon(
            Icons.Rounded.ArrowBackIosNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}
