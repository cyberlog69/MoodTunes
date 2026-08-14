package com.moodtunes.app.presentation.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodtunes.app.presentation.ui.theme.*

/**
 * Modern Material Design 3 Emblem for MoodTunes.
 * Renders the multi-layered flowing soundwave, equalizer harmonic bars,
 * and play-pulse core with animated aura glow.
 */
@Composable
fun MoodTunesLogoBadge(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    animated: Boolean = true,
    primaryGradient: List<Color> = listOf(Color(0xFF7C4DFF), Color(0xFF00D2D3), Color(0xFFFF7675))
) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_pulse")
    val pulseScale by if (animated) {
        infiniteTransition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_scale"
        )
    } else {
        rememberUpdatedState(1f)
    }

    Box(
        modifier = modifier
            .size(size)
            .scale(pulseScale),
        contentAlignment = Alignment.Center
    ) {
        // Outer Ambient Glow Ring
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(size * 0.32f))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            primaryGradient.first().copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Core Squircle Container
        Surface(
            modifier = Modifier
                .size(size * 0.88f)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(size * 0.28f)),
            shape = RoundedCornerShape(size * 0.28f),
            color = Color(0xFF130F24)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 1.2.dp,
                        brush = Brush.linearGradient(primaryGradient),
                        shape = RoundedCornerShape(size * 0.28f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Background subtle gradient fill
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF261947),
                                    Color(0xFF0E0B1A)
                                )
                            )
                        )
                )

                // Interconnected Wave + Equalizer + Note Icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(size * 0.12f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = primaryGradient[1],
                        modifier = Modifier.size(size * 0.38f)
                    )
                    Spacer(Modifier.width(size * 0.04f))
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = primaryGradient[0],
                        modifier = Modifier.size(size * 0.44f)
                    )
                }
            }
        }
    }
}

/**
 * Top-bar branded header with Logo Badge + "MoodTunes" title and subtitle.
 */
@Composable
fun MoodTunesBrandedHeader(
    modifier: Modifier = Modifier,
    subtitle: String = "How are you feeling today?",
    badgeSize: Dp = 42.dp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MoodTunesLogoBadge(size = badgeSize)
        Column {
            Text(
                text = "MoodTunes",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Hero Card with large Logo Emblem, version tag, and gradient aura for About / Settings screen.
 */
@Composable
fun MoodTunesHeroCard(
    versionName: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            Color.Transparent
                        ),
                        radius = 500f
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MoodTunesLogoBadge(size = 56.dp)

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "MoodTunes",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "v$versionName",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Emotion-driven Music Player with Audiophile DSP, Live FFT Visuals & Lossless Streaming",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
