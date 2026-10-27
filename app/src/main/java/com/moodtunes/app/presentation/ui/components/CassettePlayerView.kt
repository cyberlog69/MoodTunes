package com.moodtunes.app.presentation.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moodtunes.app.domain.model.Song
import kotlin.math.cos
import kotlin.math.sin

/**
 * Authentic Vintage Cassette Tape Player View.
 * Inspired by Prism's retro cassette tape layout:
 * - Dual spinning reels with realistic 6-tooth spools that rotate during playback
 * - Dynamic tape winding physics: left spool unwinds, right spool winds up based on progress
 * - Smoked acrylic tape window showing magnetic tape ribbon path
 * - Vintage analog cassette label with handwritten song title and embedded album art
 * - Chiseled plastic body with corner screws and bottom head trapezoid
 */
@Composable
fun CassettePlayerView(
    song: Song?,
    isPlaying: Boolean,
    progressFraction: Float, // 0.0f to 1.0f
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cassetteReels")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing)
        ),
        label = "reelRotation"
    )

    val currentRotation = if (isPlaying) rotation else 0f

    // Clamp progress between 0 and 1
    val progress = progressFraction.coerceIn(0f, 1f)
    // Left spool tape thickness shrinks as song plays
    val leftSpoolRadius = 38.dp - (16.dp * progress)
    // Right spool tape thickness grows as song plays
    val rightSpoolRadius = 22.dp + (16.dp * progress)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .shadow(16.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF282830),
                        Color(0xFF181820),
                        Color(0xFF101016)
                    )
                )
            )
            .border(1.5.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        // Cassette Corner Screws
        CornerScrew(Modifier.align(Alignment.TopStart))
        CornerScrew(Modifier.align(Alignment.TopEnd))
        CornerScrew(Modifier.align(Alignment.BottomStart))
        CornerScrew(Modifier.align(Alignment.BottomEnd))

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ─── Cassette Paper Label ────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(82.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF2EFE9))
                    .border(1.dp, Color(0xFFD0CBC0), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                // Top Color Stripe
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(accentColor, accentColor.copy(alpha = 0.6f), Color(0xFFFF6B6B))
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mini album art thumbnail
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF2A2A3A)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = song?.albumArtUri,
                            contentDescription = "Cassette Art",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        if (song?.albumArtUri == null) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = Color(0xFF9E9E9E),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SIDE A • STEREO",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = Color(0xFF6E695F)
                            )
                            Text(
                                text = "CrO2 / TYPE II",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color(0xFF8E897F)
                            )
                        }

                        Text(
                            text = song?.title ?: "Cassette Tape",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = Color(0xFF1E1E24),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song?.artist ?: "Unknown Artist",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = Color(0xFF4A4A52),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ─── Clear Smoked Acrylic Tape Window ────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .height(68.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xEE0D0D12))
                    .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Background tape ribbon path
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Magnetic tape strip behind spools
                    drawRect(
                        color = Color(0xFF2C1E18),
                        topLeft = Offset(w * 0.18f, h * 0.35f),
                        size = Size(w * 0.64f, h * 0.32f)
                    )

                    // Tape ticks ruler marks in window center
                    val tickSpacing = w * 0.035f
                    for (i in -4..4) {
                        val x = (w / 2f) + (i * tickSpacing)
                        val tickHeight = if (i == 0) h * 0.4f else if (i % 2 == 0) h * 0.28f else h * 0.18f
                        drawLine(
                            color = Color(0x66FFFFFF),
                            start = Offset(x, (h - tickHeight) / 2f),
                            end = Offset(x, (h + tickHeight) / 2f),
                            strokeWidth = 1.2f
                        )
                    }
                }

                // Dual Tape Spools
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Spool (Unwinding)
                    TapeSpool(
                        tapeRadius = leftSpoolRadius,
                        rotation = currentRotation
                    )

                    // Right Spool (Winding up)
                    TapeSpool(
                        tapeRadius = rightSpoolRadius,
                        rotation = currentRotation
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ─── Bottom Trapezoid Head opening ───────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Guide roller left
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFB8860B)))
                // Hole
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF000000)))
                // Center playhead notch
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF2A2A35))
                        .border(0.8.dp, Color(0xFF4A4A58), RoundedCornerShape(2.dp))
                )
                // Hole
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF000000)))
                // Guide roller right
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFB8860B)))
            }
        }
    }
}

@Composable
private fun TapeSpool(
    tapeRadius: androidx.compose.ui.unit.Dp,
    rotation: Float
) {
    Box(
        modifier = Modifier.size(54.dp),
        contentAlignment = Alignment.Center
    ) {
        // Brown wound magnetic tape outer circle
        Box(
            modifier = Modifier
                .size(tapeRadius * 2)
                .clip(CircleShape)
                .background(Color(0xFF382319))
                .border(0.5.dp, Color(0xFF5A3D2A), CircleShape)
        )

        // White 6-tooth reel hub
        Box(
            modifier = Modifier
                .size(34.dp)
                .rotate(rotation),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val outerRadius = size.width / 2f
                val innerRadius = outerRadius * 0.62f

                // Hub base disc
                drawCircle(
                    color = Color(0xFFEEEEEE),
                    radius = outerRadius,
                    center = center
                )

                // 6 reel teeth
                for (i in 0 until 6) {
                    val angle = (i * 60f) * (Math.PI.toFloat() / 180f)
                    val toothCenter = Offset(
                        center.x + (outerRadius * 0.76f) * cos(angle),
                        center.y + (outerRadius * 0.76f) * sin(angle)
                    )
                    drawCircle(
                        color = Color(0xFF1E1E24),
                        radius = 2.4f.dp.toPx(),
                        center = toothCenter
                    )
                }

                // Center spindle hole
                drawCircle(
                    color = Color(0xFF101016),
                    radius = innerRadius * 0.65f,
                    center = center
                )
            }
        }
    }
}

@Composable
private fun CornerScrew(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(4.dp)
            .size(7.dp)
            .clip(CircleShape)
            .background(Color(0xFF3E3E48))
            .border(0.5.dp, Color(0xFF5A5A68), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(0.8.dp)
                .background(Color(0xFF1E1E24))
        )
    }
}
