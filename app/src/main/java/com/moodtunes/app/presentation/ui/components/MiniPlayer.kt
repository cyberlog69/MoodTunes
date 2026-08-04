package com.moodtunes.app.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moodtunes.app.domain.model.MoodType
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.presentation.ui.theme.*

/**
 * Persistent mini-player bar shown at the bottom of the home screen
 * when a song is active.
 */
@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    mood: MoodType?,
    onPlayPauseClick: () -> Unit,
    onSkipPreviousClick: () -> Unit = {},
    onSkipNextClick: () -> Unit = {},
    onExpandClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = mood?.gradientStart ?: EuphoricGradientStart
    val animatedAccent by animateColorAsState(
        targetValue = accentColor,
        animationSpec = tween(600),
        label = "accent"
    )

    Card(
        modifier = modifier.clickable(onClick = onExpandClick),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            SurfaceVariant,
                            animatedAccent.copy(alpha = 0.3f)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Album Art
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = song.albumArtUri,
                        contentDescription = "Album art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onError = {}
                    )
                    // Fallback icon if no art
                    if (song.albumArtUri == null) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Song Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Controls
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onSkipPreviousClick, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous",
                            tint = OnSurface
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(animatedAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = onPlayPauseClick) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    IconButton(onClick = onSkipNextClick, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Rounded.SkipNext,
                            contentDescription = "Next",
                            tint = OnSurface
                        )
                    }
                }
            }
        }
    }
}
