package com.moodtunes.app.presentation.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class CoverGradientPreset(
    val title: String,
    val startColor: Color,
    val endColor: Color
) {
    PRISM_AURORA("Prism Aurora", Color(0xFF6C5CE7), Color(0xFF00CEC9)),
    SUNSET_GLOW("Sunset Glow", Color(0xFFFF7675), Color(0xFFFD79A8)),
    NEON_CYBER("Neon Cyber", Color(0xFF0984E3), Color(0xFFE84393)),
    DEEP_FOREST("Deep Forest", Color(0xFF00B894), Color(0xFF2D3436)),
    COSMIC_PURPLE("Cosmic Purple", Color(0xFF8E44AD), Color(0xFF2C3E50)),
    GOLDEN_HOUR("Golden Hour", Color(0xFFF39C12), Color(0xFFD35400)),
    MIDNIGHT_DARK("Midnight Dark", Color(0xFF2C3E50), Color(0xFF1E272C)),
    CHERRY_BLOSSOM("Cherry", Color(0xFFFF9FF3), Color(0xFFFECA57))
}

enum class CoverPattern(val displayName: String) {
    NONE("Clean"),
    SPLIT_PRISM("Split Light"),
    CONCENTRIC("Concentric Rings"),
    MINIMAL_WAVES("Waveforms"),
    DOT_GRID("Dot Matrix")
}

/**
 * In-app Procedural Playlist Cover Designer.
 * Inspired by Prism's cover maker:
 * - Dynamic color gradient pairings
 * - Geometric prism refraction effects & patterns
 * - Live Compose preview
 * - High-resolution 800x800 bitmap rendering saved to local storage
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistCoverDesignerDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onCoverGenerated: (Uri) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var title by remember { mutableStateOf(initialTitle.ifBlank { "My Playlist" }) }
    var subtitle by remember { mutableStateOf("Curated by MoodTunes") }
    var selectedGradient by remember { mutableStateOf(CoverGradientPreset.PRISM_AURORA) }
    var selectedPattern by remember { mutableStateOf(CoverPattern.SPLIT_PRISM) }
    var isSaving by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Design Cover",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ─── Live Preview Card ───────────────────────────────────────
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .shadow(12.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(selectedGradient.startColor, selectedGradient.endColor)
                            )
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Pattern overlay
                    CoverPatternCanvas(
                        pattern = selectedPattern,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Text overlay
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp,
                                fontSize = 18.sp
                            ),
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = subtitle.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.5.sp,
                                fontSize = 10.sp
                            ),
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ─── Title & Subtitle Inputs ─────────────────────────────────
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Cover Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("Subtitle / Tagline") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(16.dp))

                // ─── Gradient Presets ────────────────────────────────────────
                Text(
                    text = "Gradient Palette",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CoverGradientPreset.entries.forEach { preset ->
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(preset.startColor, preset.endColor))
                                )
                                .border(
                                    width = if (selectedGradient == preset) 3.dp else 1.dp,
                                    color = if (selectedGradient == preset) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedGradient = preset },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedGradient == preset) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ─── Pattern Style ───────────────────────────────────────────
                Text(
                    text = "Prism Pattern",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CoverPattern.entries.forEach { pattern ->
                        FilterChip(
                            selected = selectedPattern == pattern,
                            onClick = { selectedPattern = pattern },
                            label = { Text(pattern.displayName) }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ─── Save Button ─────────────────────────────────────────────
                Button(
                    onClick = {
                        isSaving = true
                        coroutineScope.launch {
                            val uri = generateAndSaveCoverBitmap(
                                context = context,
                                title = title,
                                subtitle = subtitle,
                                gradient = selectedGradient,
                                pattern = selectedPattern
                            )
                            isSaving = false
                            if (uri != null) {
                                onCoverGenerated(uri)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !isSaving && title.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Apply Cover to Playlist", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverPatternCanvas(
    pattern: CoverPattern,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        when (pattern) {
            CoverPattern.NONE -> {}
            CoverPattern.SPLIT_PRISM -> {
                // Diagonal split light beam
                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = androidx.compose.ui.geometry.Offset(0f, h * 0.2f),
                    end = androidx.compose.ui.geometry.Offset(w, h * 0.8f),
                    strokeWidth = 4f
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.12f),
                    start = androidx.compose.ui.geometry.Offset(0f, h * 0.15f),
                    end = androidx.compose.ui.geometry.Offset(w, h * 0.75f),
                    strokeWidth = 14f
                )
            }
            CoverPattern.CONCENTRIC -> {
                drawCircle(
                    color = Color.White.copy(alpha = 0.12f),
                    radius = w * 0.35f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = w * 0.48f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                )
            }
            CoverPattern.MINIMAL_WAVES -> {
                for (i in 0..6) {
                    val y = h * (0.2f + i * 0.1f)
                    drawLine(
                        color = Color.White.copy(alpha = 0.12f),
                        start = androidx.compose.ui.geometry.Offset(w * 0.15f, y),
                        end = androidx.compose.ui.geometry.Offset(w * 0.85f, y),
                        strokeWidth = 1.5f
                    )
                }
            }
            CoverPattern.DOT_GRID -> {
                val step = w / 8f
                for (x in 1..7) {
                    for (y in 1..7) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.14f),
                            radius = 2f,
                            center = androidx.compose.ui.geometry.Offset(x * step, y * step)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Procedurally generates an 800x800 high-resolution bitmap and saves it to app internal storage.
 */
private suspend fun generateAndSaveCoverBitmap(
    context: Context,
    title: String,
    subtitle: String,
    gradient: CoverGradientPreset,
    pattern: CoverPattern
): Uri? = withContext(Dispatchers.IO) {
    try {
        val size = 800
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Draw LinearGradient
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                gradient.startColor.toArgb(), gradient.endColor.toArgb(),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        // 2. Draw Pattern
        val patternPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.argb(45, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        when (pattern) {
            CoverPattern.SPLIT_PRISM -> {
                patternPaint.strokeWidth = 10f
                patternPaint.color = android.graphics.Color.argb(70, 255, 255, 255)
                canvas.drawLine(0f, size * 0.2f, size.toFloat(), size * 0.8f, patternPaint)
            }
            CoverPattern.CONCENTRIC -> {
                patternPaint.strokeWidth = 5f
                canvas.drawCircle(size / 2f, size / 2f, size * 0.35f, patternPaint)
                canvas.drawCircle(size / 2f, size / 2f, size * 0.46f, patternPaint)
            }
            CoverPattern.MINIMAL_WAVES -> {
                for (i in 0..6) {
                    val y = size * (0.2f + i * 0.1f)
                    canvas.drawLine(size * 0.15f, y, size * 0.85f, y, patternPaint)
                }
            }
            CoverPattern.DOT_GRID -> {
                patternPaint.style = Paint.Style.FILL
                val step = size / 8f
                for (x in 1..7) {
                    for (y in 1..7) {
                        canvas.drawCircle(x * step, y * step, 5f, patternPaint)
                    }
                }
            }
            CoverPattern.NONE -> {}
        }

        // 3. Draw Typography
        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            textSize = 58f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            setShadowLayer(8f, 0f, 4f, android.graphics.Color.argb(120, 0, 0, 0))
        }

        val subtitlePaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.argb(220, 255, 255, 255)
            textSize = 28f
            letterSpacing = 0.15f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }

        canvas.drawText(title, size / 2f, (size / 2f) - 10f, titlePaint)
        canvas.drawText(subtitle.uppercase(), size / 2f, (size / 2f) + 45f, subtitlePaint)

        // 4. Save to app cache / files dir
        val coversDir = File(context.filesDir, "playlist_covers").apply { mkdirs() }
        val outputFile = File(coversDir, "cover_${System.currentTimeMillis()}.png")

        FileOutputStream(outputFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, fos)
        }
        bitmap.recycle()

        Uri.fromFile(outputFile)
    } catch (e: Exception) {
        timber.log.Timber.e(e, "Failed to render custom playlist cover")
        null
    }
}
