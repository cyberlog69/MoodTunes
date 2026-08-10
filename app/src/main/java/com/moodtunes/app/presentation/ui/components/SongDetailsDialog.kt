package com.moodtunes.app.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.moodtunes.app.domain.model.Song
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SongDetailsDialog(
    song: Song,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Track Information",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(20.dp))

                // Detail Rows
                DetailItem(Icons.Rounded.Title, "Title", song.title)
                DetailItem(Icons.Rounded.Person, "Artist", song.artist)
                if (song.album.isNotBlank()) {
                    DetailItem(Icons.Rounded.Album, "Album", song.album)
                }
                DetailItem(Icons.Rounded.Timer, "Duration", song.formattedDuration)
                DetailItem(Icons.Rounded.HighQuality, "Audio Format", "${song.audioFormat.displayName} (${if (song.audioFormat.isLossless) "Lossless" else "Compressed"})")
                if (!song.genre.isNullOrBlank()) {
                    DetailItem(Icons.Rounded.Category, "Genre", song.genre)
                }
                DetailItem(
                    Icons.Rounded.Cloud,
                    "Source",
                    if (song.isStream) "Online Stream" else "Local Storage"
                )
                if (song.playCount > 0) {
                    DetailItem(Icons.Rounded.PlayCircle, "Plays", "${song.playCount} times")
                }
                if (song.lastPlayedAt > 0) {
                    val dateStr = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
                        .format(Date(song.lastPlayedAt))
                    DetailItem(Icons.Rounded.History, "Last Played", dateStr)
                }
                if (song.moodTags.isNotEmpty()) {
                    DetailItem(
                        Icons.Rounded.Mood,
                        "Mood Tags",
                        song.moodTags.joinToString(", ") { "${it.emoji} ${it.displayName}" }
                    )
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun DetailItem(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(20.dp)
                .padding(top = 2.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
