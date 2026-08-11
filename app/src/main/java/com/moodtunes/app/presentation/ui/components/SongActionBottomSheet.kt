package com.moodtunes.app.presentation.ui.components

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moodtunes.app.R
import com.moodtunes.app.domain.model.Playlist
import com.moodtunes.app.domain.model.Song
import com.moodtunes.app.presentation.ui.theme.FavoriteRed
import com.moodtunes.app.presentation.ui.theme.White

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionBottomSheet(
    song: Song?,
    playlists: List<Playlist> = emptyList(),
    onDismiss: () -> Unit,
    onPlay: () -> Unit = {},
    onPlayNext: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onAddToPlaylist: (playlistId: Long) -> Unit = {},
    onCreatePlaylist: (name: String) -> Unit = {},
    onSaveTags: ((Song, String, String, String, String?, Uri?) -> Unit)? = null
) {
    if (song == null) return

    val context = LocalContext.current
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showTagEditorDialog by remember { mutableStateOf(false) }

    if (showTagEditorDialog && onSaveTags != null) {
        TagEditorDialog(
            song = song,
            onSave = { title, artist, album, genre, artUri ->
                onSaveTags(song, title, artist, album, genre, artUri)
                showTagEditorDialog = false
                onDismiss()
            },
            onDismiss = { showTagEditorDialog = false }
        )
    }

    if (showDetailsDialog) {
        SongDetailsDialog(song = song, onDismiss = { showDetailsDialog = false })
    }

    if (showPlaylistDialog) {
        AddToPlaylistDialog(
            song = song,
            playlists = playlists,
            onAddToPlaylist = { id ->
                onAddToPlaylist(id)
                showPlaylistDialog = false
                onDismiss()
                Toast.makeText(context, "Added to playlist", Toast.LENGTH_SHORT).show()
            },
            onCreatePlaylist = { name ->
                onCreatePlaylist(name)
            },
            onDismiss = { showPlaylistDialog = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ─── Header: Song Info ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = song.albumArtUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    if (song.albumArtUri == null) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${song.artist} • ${song.formattedDuration}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(song.audioFormat.badgeColorHex).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = song.audioFormat.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(song.audioFormat.badgeColorHex),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Quick Favorite button
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (song.isFavorite) FavoriteRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // ─── Actions List ───────────────────────────────────────────────
            ActionItem(
                icon = Icons.Rounded.PlayArrow,
                title = "Play Now",
                subtitle = "Start playing this track immediately",
                onClick = {
                    onPlay()
                    onDismiss()
                }
            )

            ActionItem(
                icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                title = "Play Next",
                subtitle = "Insert right after the current song in queue",
                onClick = {
                    onPlayNext()
                    onDismiss()
                    Toast.makeText(context, "Playing next: ${song.title}", Toast.LENGTH_SHORT).show()
                }
            )

            ActionItem(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                title = "Add to Queue",
                subtitle = "Append to the end of upcoming tracks",
                onClick = {
                    onAddToQueue()
                    onDismiss()
                    Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                }
            )

            ActionItem(
                icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                title = "Add to Playlist",
                subtitle = "Save to your custom playlists",
                onClick = {
                    showPlaylistDialog = true
                }
            )

            ActionItem(
                icon = Icons.Rounded.Share,
                title = "Share Song",
                subtitle = "Send track details or link to friends",
                onClick = {
                    shareSong(context, song)
                    onDismiss()
                }
            )

            if (!song.isStream) {
                if (onSaveTags != null) {
                    ActionItem(
                        icon = Icons.Rounded.EditNote,
                        title = "Edit Tags & Artwork",
                        subtitle = "Modify title, artist, album & cover art",
                        onClick = {
                            showTagEditorDialog = true
                        }
                    )
                }

                ActionItem(
                    icon = Icons.Rounded.NotificationsActive,
                    title = "Set as Ringtone",
                    subtitle = "Use this track as default phone ringtone",
                    onClick = {
                        setAsRingtone(context, song)
                        onDismiss()
                    }
                )
            }

            ActionItem(
                icon = Icons.Rounded.Info,
                title = "Track Details",
                subtitle = "View format, audio quality, and file info",
                onClick = {
                    showDetailsDialog = true
                }
            )
        }
    }
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun shareSong(context: Context, song: Song) {
    val text = buildString {
        append("🎵 Listening to \"${song.title}\" by ${song.artist}")
        if (song.album.isNotBlank()) append(" (${song.album})")
        append(" on MoodTunes")
        if (song.isStream) append("\n${song.uri}")
    }
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, song.title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(
        Intent.createChooser(sendIntent, "Share Track")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private fun setAsRingtone(context: Context, song: Song) {
    try {
        RingtoneManager.setActualDefaultRingtoneUri(
            context,
            RingtoneManager.TYPE_RINGTONE,
            song.uri
        )
        Toast.makeText(context, "Ringtone set to ${song.title}", Toast.LENGTH_SHORT).show()
    } catch (e: SecurityException) {
        // Direct ringtone write requires WRITE_SETTINGS permission
        try {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Toast.makeText(context, "Please allow Modify System Settings to set ringtones", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(context, "Could not set ringtone on this device", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to set ringtone", Toast.LENGTH_SHORT).show()
    }
}
