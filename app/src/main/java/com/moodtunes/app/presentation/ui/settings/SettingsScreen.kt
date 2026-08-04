package com.moodtunes.app.presentation.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodtunes.app.data.local.preferences.DarkModeOption
import com.moodtunes.app.data.local.preferences.StreamQuality
import com.moodtunes.app.presentation.ui.theme.*

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = uiState.userSettings
    val context = LocalContext.current

    var isQualityDropdownExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Top background gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF2C1654).copy(alpha = 0.5f),
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
                    text = "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    color = White,
                    modifier = Modifier.weight(1f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // ─── SECTION 1: Theme & Appearance ─────────────────────────────
                SettingsSectionHeader(title = "Theme & Appearance", icon = Icons.Rounded.Palette)

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "Dark Mode Mode",
                            style = MaterialTheme.typography.titleMedium,
                            color = White
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DarkModeOption.entries.forEach { option ->
                                val isSelected = settings.darkModeOption == option
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.onDarkModeChanged(option) },
                                    label = { Text(option.displayName, style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EuphoricAccent,
                                        selectedLabelColor = White,
                                        containerColor = SurfaceVariant,
                                        labelColor = OnSurfaceVariant
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        HorizontalDivider(color = DividerColor)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Material You Dynamic Colors", style = MaterialTheme.typography.titleSmall, color = White)
                                Text("Adapt system colors on Android 12+", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                            }
                            Switch(
                                checked = settings.useDynamicColors,
                                onCheckedChange = viewModel::onDynamicColorsChanged,
                                colors = SwitchDefaults.colors(checkedThumbColor = White, checkedTrackColor = EuphoricAccent)
                            )
                        }
                    }
                }

                // ─── SECTION 2: Audio & Stream Quality ──────────────────────────
                SettingsSectionHeader(title = "Audio & Stream Quality", icon = Icons.Rounded.GraphicEq)

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Audio Stream Quality", style = MaterialTheme.typography.titleSmall, color = White)
                                Text(settings.streamQuality.displayName, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                            }
                            Box {
                                Button(
                                    onClick = { isQualityDropdownExpanded = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = EuphoricAccent.copy(alpha = 0.2f), contentColor = EuphoricAccent),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(settings.streamQuality.badgeText)
                                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                                }

                                DropdownMenu(
                                    expanded = isQualityDropdownExpanded,
                                    onDismissRequest = { isQualityDropdownExpanded = false }
                                ) {
                                    StreamQuality.entries.forEach { quality ->
                                        DropdownMenuItem(
                                            text = { Text(quality.displayName) },
                                            onClick = {
                                                viewModel.onStreamQualityChanged(quality)
                                                isQualityDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ─── SECTION 3: Network & Downloads ──────────────────────────────
                SettingsSectionHeader(title = "Network & Downloads", icon = Icons.Rounded.Wifi)

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Stream on Wi-Fi Only", style = MaterialTheme.typography.titleSmall, color = White)
                                Text("Prevent online streaming on mobile cellular data", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                            }
                            Switch(
                                checked = settings.wifiOnlyStreaming,
                                onCheckedChange = viewModel::onWifiOnlyStreamingChanged,
                                colors = SwitchDefaults.colors(checkedThumbColor = White, checkedTrackColor = EuphoricAccent)
                            )
                        }

                        HorizontalDivider(color = DividerColor)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Download on Wi-Fi Only", style = MaterialTheme.typography.titleSmall, color = White)
                                Text("Save offline tracks over Wi-Fi only", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                            }
                            Switch(
                                checked = settings.wifiOnlyDownloads,
                                onCheckedChange = viewModel::onWifiOnlyDownloadsChanged,
                                colors = SwitchDefaults.colors(checkedThumbColor = White, checkedTrackColor = EuphoricAccent)
                            )
                        }

                        HorizontalDivider(color = DividerColor)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("High Quality over Mobile Data", style = MaterialTheme.typography.titleSmall, color = White)
                                Text("Allow Lossless FLAC streams on 4G/5G data", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                            }
                            Switch(
                                checked = settings.mobileDataHighQuality,
                                onCheckedChange = viewModel::onMobileDataHighQualityChanged,
                                colors = SwitchDefaults.colors(checkedThumbColor = White, checkedTrackColor = EuphoricAccent)
                            )
                        }
                    }
                }

                // ─── SECTION 4: In-App Update & App Version ─────────────────────
                SettingsSectionHeader(title = "App Updates & About", icon = Icons.Rounded.SystemUpdate)

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("MoodTunes Player", style = MaterialTheme.typography.titleMedium, color = White)
                                Text("Current Version: v1.0.0", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                            }

                            Button(
                                onClick = viewModel::checkForUpdates,
                                enabled = !uiState.isCheckingUpdate,
                                colors = ButtonDefaults.buttonColors(containerColor = EuphoricAccent),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (uiState.isCheckingUpdate) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Checking…")
                                } else {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Check for Updates")
                                }
                            }
                        }

                        // Display update check status banner if checked
                        uiState.updateResult?.let { result ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (result.isUpdateAvailable) Color(0xFF2E7D32).copy(alpha = 0.25f) else SurfaceVariant,
                                border = if (result.isUpdateAvailable) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50)) else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (result.isUpdateAvailable) Icons.Rounded.DownloadForOffline else Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = if (result.isUpdateAvailable) Color(0xFF4CAF50) else EuphoricAccent
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (result.isUpdateAvailable) "Update Available! ${result.latestVersion}" else "MoodTunes is Up to Date",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = White
                                        )
                                        Text(
                                            text = result.releaseNotes,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OnSurfaceVariant
                                        )
                                    }
                                    if (result.isUpdateAvailable) {
                                        IconButton(onClick = {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.downloadUrl))
                                            context.startActivity(intent)
                                        }) {
                                            Icon(Icons.Rounded.OpenInNew, contentDescription = "Download", tint = Color(0xFF4CAF50))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = EuphoricAccent, modifier = Modifier.size(22.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = EuphoricAccent
        )
    }
}
