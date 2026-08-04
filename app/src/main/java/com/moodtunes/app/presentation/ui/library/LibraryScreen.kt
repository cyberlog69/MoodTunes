package com.moodtunes.app.presentation.ui.library

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodtunes.app.presentation.ui.components.SongItem
import com.moodtunes.app.presentation.ui.theme.*

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val displayedSongs = when (uiState.selectedTab) {
        LibraryTab.ALL_SONGS -> uiState.filteredSongs
        LibraryTab.FAVORITES -> uiState.favoriteSongs
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Subtle gradient top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A0A35).copy(alpha = 0.6f),
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
            // ─── Top Bar ────────────────────────────────────────────────────
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
                        tint = White
                    )
                }
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.headlineSmall,
                    color = White,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${uiState.allSongs.size} songs",
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSurfaceVariant,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }

            // ─── Search Bar ─────────────────────────────────────────────────
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = {
                    Text("Search songs, artists…", color = OnSurfaceVariant)
                },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = OnSurfaceVariant)
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = OnSurfaceVariant)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EuphoricAccent,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = White,
                    unfocusedTextColor = White,
                    cursorColor = EuphoricAccent,
                    focusedContainerColor = SurfaceVariant,
                    unfocusedContainerColor = SurfaceVariant
                ),
                singleLine = true
            )

            // ─── Tabs ───────────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = LibraryTab.entries.indexOf(uiState.selectedTab),
                containerColor = Color.Transparent,
                contentColor = White,
                indicator = { tabPositions ->
                    val index = LibraryTab.entries.indexOf(uiState.selectedTab)
                    if (index in tabPositions.indices) {
                        Box(
                            Modifier
                                .tabIndicatorOffset(tabPositions[index])
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(EuphoricAccent)
                        )
                    }
                },
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                LibraryTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.onTabSelected(tab) },
                        text = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.titleSmall
                            )
                        },
                        selectedContentColor = White,
                        unselectedContentColor = OnSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = DividerColor)

            // ─── Song List ──────────────────────────────────────────────────
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = EuphoricAccent)
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
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = if (uiState.selectedTab == LibraryTab.FAVORITES)
                                "No favorites yet.\nHeart a song to add it here."
                            else "No songs found.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = OnSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 0.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(displayedSongs, key = { index, song -> "${song.id}_$index" }) { _, song ->
                        SongItem(
                            song = song,
                            isPlaying = uiState.currentSongId == song.id && uiState.isPlaying,
                            onClick = {
                                viewModel.onSongSelected(song)
                                onNavigateToPlayer()
                            },
                            onFavoriteClick = { viewModel.onToggleFavorite(song.id) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = DividerColor
                        )
                    }
                }
            }
        }
    }
}
