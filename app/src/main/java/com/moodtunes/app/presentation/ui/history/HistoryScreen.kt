package com.moodtunes.app.presentation.ui.history

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodtunes.app.domain.model.MoodEntry
import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.presentation.ui.theme.*

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Background accent
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            Color.Transparent
                        )
                    )
                )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // ─── Top Bar ────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Rounded.ArrowBackIosNew,
                            "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = "Mood History",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // ─── Stats Cards ─────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Total Time Card
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = "⏱️",
                        label = "Listening Time",
                        value = uiState.formattedTotalTime,
                        gradientColors = listOf(Color(0xFF1A237E), Color(0xFF311B92))
                    )

                    // Total Sessions Card
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = "🎵",
                        label = "Sessions",
                        value = "${uiState.totalSessions}",
                        gradientColors = listOf(Color(0xFF004D40), Color(0xFF006064))
                    )
                }
            }

            // Top Mood Card
            uiState.topMood?.let { topMood ->
                item {
                    TopMoodCard(
                        mood = topMood,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            // ─── Mood Distribution ───────────────────────────────────────────
            if (uiState.moodStats.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Your Vibe Stats",
                        subtitle = "Last 30 days"
                    )
                }
                items(uiState.moodStats) { stat ->
                    MoodStatRow(stat = stat)
                }
            }

            // ─── Recent History ──────────────────────────────────────────────
            item {
                SectionHeader(
                    title = "Recent Sessions",
                    subtitle = "${uiState.recentEntries.size} sessions"
                )
            }

            if (uiState.recentEntries.isEmpty() && !uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎧", style = MaterialTheme.typography.displayMedium)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No history yet.\nSelect a mood on the home screen to start!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(uiState.recentEntries, key = { it.id }) { entry ->
                    MoodHistoryItem(entry = entry)
                }
            }
        }
    }
}

// ─── Sub-components ──────────────────────────────────────────────────────────

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: String,
    label: String,
    value: String,
    gradientColors: List<Color>
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradientColors))
                .padding(16.dp)
        ) {
            Column {
                Text(icon, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(value, style = MaterialTheme.typography.headlineSmall, color = White)
                Text(label, style = MaterialTheme.typography.bodySmall, color = White.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun TopMoodCard(mood: MoodType, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(mood.gradientStart, mood.gradientEnd)
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(mood.emoji, style = MaterialTheme.typography.displaySmall)
                Column {
                    Text(
                        "Your Top Mood",
                        style = MaterialTheme.typography.labelMedium,
                        color = White.copy(alpha = 0.9f)
                    )
                    Text(
                        mood.displayName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = White
                    )
                    Text(
                        mood.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MoodStatRow(stat: MoodStat) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val animatedWidth by animateFloatAsState(
        targetValue = if (started) stat.percentage else 0f,
        animationSpec = tween(800),
        label = "barWidth"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stat.mood.emoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(36.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stat.mood.displayName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Text("${stat.count} sessions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(stat.mood.gradientStart, stat.mood.gradientEnd)
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MoodHistoryItem(entry: MoodEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(entry.moodType.gradientStart, entry.moodType.gradientEnd)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(entry.moodType.emoji, style = MaterialTheme.typography.titleLarge)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(entry.moodType.displayName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "${entry.formattedDate} • ${entry.songCount} songs • ${entry.formattedDuration}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
