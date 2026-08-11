package com.moodtunes.app.presentation.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodtunes.app.data.local.preferences.AudioSourceMode
import com.moodtunes.app.data.local.preferences.DarkModeOption
import com.moodtunes.app.data.local.preferences.StreamQuality
import com.moodtunes.app.data.local.preferences.StreamingProvider
import com.moodtunes.app.presentation.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = uiState.userSettings
    val context = LocalContext.current

    var isQualityDropdownExpanded by remember { mutableStateOf(false) }

    var listenBrainzToken by remember(settings.listenBrainzToken) { mutableStateOf(settings.listenBrainzToken) }
    var listenBrainzUser by remember(settings.listenBrainzUsername) { mutableStateOf(settings.listenBrainzUsername) }

    var navidromeUrl by remember(settings.navidromeServerUrl) { mutableStateOf(settings.navidromeServerUrl) }
    var navidromeUser by remember(settings.navidromeUsername) { mutableStateOf(settings.navidromeUsername) }
    var navidromePass by remember(settings.navidromePassword) { mutableStateOf(settings.navidromePassword) }

    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let(viewModel::exportBackup)
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let(viewModel::importBackup)
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top background gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
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
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "Dark Mode",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
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
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Material You Dynamic Colors", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("Adapt system colors on Android 12+", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = settings.useDynamicColors,
                                onCheckedChange = viewModel::onDynamicColorsChanged,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }

                // ─── SECTION 2: Audio Source & Streaming Providers ────────────
                SettingsSectionHeader(title = "Audio Source & Streaming Providers", icon = Icons.Rounded.Tune)

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "Audio Source Mode",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Switch between local storage audio files, online streams, or both combined.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AudioSourceMode.entries.forEach { mode ->
                                val isSelected = settings.audioSourceMode == mode
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.onAudioSourceModeChanged(mode) },
                                    label = { Text(mode.displayName, style = MaterialTheme.typography.labelMedium) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        if (settings.audioSourceMode != AudioSourceMode.LOCAL_ONLY) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            Text(
                                text = "Online Streaming Provider",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Select whether to fetch streams from JioSaavn regional/traditional, Audius, iTunes & Deezer previews, Jamendo, Global Internet Radio, or all combined.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                StreamingProvider.entries.forEach { provider ->
                                    val isSelected = settings.streamingProvider == provider
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.onStreamingProviderChanged(provider) },
                                        label = { Text(provider.displayName, style = MaterialTheme.typography.labelMedium) },
                                        leadingIcon = if (isSelected) {
                                            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        } else null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Text(
                            text = "Music Language Preferences (Multi-Select)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Select one or multiple languages. Your online streams and mood playlists will be personalized to your selection.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            com.moodtunes.app.data.local.preferences.MusicLanguage.entries.forEach { language ->
                                val isSelected = settings.preferredLanguages.contains(language)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.onTogglePreferredLanguage(language) },
                                    label = { Text("${language.flagEmoji} ${language.displayName}") },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }

                // ─── SECTION 3: Audio & Stream Quality ──────────────────────────
                SettingsSectionHeader(title = "Audio & Stream Quality", icon = Icons.Rounded.GraphicEq)

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Audio Stream Quality", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                Text(settings.streamQuality.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box {
                                Button(
                                    onClick = { isQualityDropdownExpanded = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
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

                // ─── SECTION 4: Network & Downloads ──────────────────────────────
                SettingsSectionHeader(title = "Network & Downloads", icon = Icons.Rounded.Wifi)

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Stream on Wi-Fi Only", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("Prevent online streaming on mobile cellular data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = settings.wifiOnlyStreaming,
                                onCheckedChange = viewModel::onWifiOnlyStreamingChanged,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Download on Wi-Fi Only", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("Save offline tracks over Wi-Fi only", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = settings.wifiOnlyDownloads,
                                onCheckedChange = viewModel::onWifiOnlyDownloadsChanged,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("High Quality over Mobile Data", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("Allow Lossless FLAC streams on 4G/5G data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = settings.mobileDataHighQuality,
                                onCheckedChange = viewModel::onMobileDataHighQualityChanged,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }

                // ─── SECTION 5: 🧠 ListenBrainz Scrobbler & Stats ──────────────────
                SettingsSectionHeader(title = "ListenBrainz Scrobbler", icon = Icons.Rounded.GraphicEq)

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Scrobble Music", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("Automatically log played tracks to your open-source ListenBrainz profile", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = settings.isListenBrainzScrobblingEnabled,
                                onCheckedChange = viewModel::onToggleListenBrainzScrobbling,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        OutlinedTextField(
                            value = listenBrainzToken,
                            onValueChange = { listenBrainzToken = it },
                            label = { Text("User Token") },
                            placeholder = { Text("Paste your ListenBrainz User Token") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = listenBrainzUser,
                            onValueChange = { listenBrainzUser = it },
                            label = { Text("Username") },
                            placeholder = { Text("Your ListenBrainz handle") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://listenbrainz.org/settings/"))
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Get Token", style = MaterialTheme.typography.labelMedium)
                            }

                            Button(
                                onClick = {
                                    viewModel.onSaveListenBrainz(
                                        token = listenBrainzToken,
                                        username = listenBrainzUser,
                                        enabled = settings.isListenBrainzScrobblingEnabled || listenBrainzToken.isNotBlank()
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save Token", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                // ─── SECTION 6: 🏠 Self-Hosted Server (Navidrome / Subsonic) ────────
                SettingsSectionHeader(title = "Self-Hosted Music Server", icon = Icons.Rounded.CloudQueue)

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Enable Personal Server", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("Stream your legally owned FLAC/ALAC lossless library (Navidrome, Airsonic, Gonic, Jellyfin)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = settings.isNavidromeEnabled,
                                onCheckedChange = viewModel::onToggleNavidrome,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        OutlinedTextField(
                            value = navidromeUrl,
                            onValueChange = { navidromeUrl = it },
                            label = { Text("Server URL") },
                            placeholder = { Text("https://music.yourdomain.com") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = navidromeUser,
                            onValueChange = { navidromeUser = it },
                            label = { Text("Username") },
                            placeholder = { Text("Server username") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = navidromePass,
                            onValueChange = { navidromePass = it },
                            label = { Text("Password or App Token") },
                            placeholder = { Text("Server password") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (uiState.navidromeTestStatus != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (uiState.navidromeTestStatus?.contains("✅") == true)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = uiState.navidromeTestStatus ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (uiState.navidromeTestStatus?.contains("✅") == true)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.testNavidromeConnection(
                                        serverUrl = navidromeUrl,
                                        username = navidromeUser,
                                        password = navidromePass
                                    )
                                },
                                enabled = !uiState.isTestingNavidrome && navidromeUrl.isNotBlank() && navidromeUser.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (uiState.isTestingNavidrome) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Rounded.WifiTethering, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(6.dp))
                                Text("Test Server", style = MaterialTheme.typography.labelMedium)
                            }

                            Button(
                                onClick = {
                                    viewModel.onSaveNavidrome(
                                        serverUrl = navidromeUrl,
                                        username = navidromeUser,
                                        password = navidromePass,
                                        enabled = true
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save Server", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                // ─── SECTION 7: Backup & Restore ────────────────────────────────
                SettingsSectionHeader(title = "Backup & Restore", icon = Icons.Rounded.Backup)

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Export your favorites and playlists as a JSON file, or restore them from a previous backup.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                                    exportLauncher.launch("MoodTunes-Backup-$date.json")
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Export Backup")
                            }
                            Button(
                                onClick = { importLauncher.launch(arrayOf("application/json")) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Import Backup")
                            }
                        }
                    }
                }

                // ─── SECTION 6: In-App Update & App Version ─────────────────────
                SettingsSectionHeader(title = "App Updates & About", icon = Icons.Rounded.SystemUpdate)

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("MoodTunes Player", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text("MoodTunes Player", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text("Current Version: v${com.moodtunes.app.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Button(
                                onClick = viewModel::checkForUpdates,
                                enabled = !uiState.isCheckingUpdate,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (uiState.isCheckingUpdate) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
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
                                color = if (result.isUpdateAvailable) Color(0xFF2E7D32).copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
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
                                        tint = if (result.isUpdateAvailable) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (result.isUpdateAvailable) "Update Available! ${result.latestVersion}" else "MoodTunes is Up to Date",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = result.releaseNotes,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (result.isUpdateAvailable) {
                                        Button(
                                            onClick = { viewModel.startInAppUpdate(context) },
                                            enabled = !uiState.isDownloading,
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(if (uiState.isDownloading) "${uiState.downloadProgress}%" else "Update")
                                        }
                                    }
                                }
                            }
                        }

                        // ─── In-App Update Prompt Dialog ──────────────────────────────
                        if (uiState.showUpdateDialog && uiState.updateResult != null) {
                            val result = uiState.updateResult!!
                            AlertDialog(
                                onDismissRequest = {
                                    if (!uiState.isDownloading) viewModel.dismissUpdateDialog()
                                },
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Rounded.DownloadForOffline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = "Update Found (${result.latestVersion})",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(
                                            text = "A new update for MoodTunes is ready. Would you like to download and install it now?",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (result.releaseNotes.isNotEmpty()) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text(
                                                        text = "Changelog:",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(
                                                        text = result.releaseNotes,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        if (uiState.isDownloading) {
                                            Spacer(Modifier.height(6.dp))
                                            LinearProgressIndicator(
                                                progress = { uiState.downloadProgress / 100f },
                                                modifier = Modifier.fillMaxWidth(),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "Downloading update... ${uiState.downloadProgress}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.align(Alignment.End)
                                            )
                                        }
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = { viewModel.startInAppUpdate(context) },
                                        enabled = !uiState.isDownloading,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(if (uiState.isDownloading) "Downloading..." else "Update")
                                    }
                                },
                                dismissButton = {
                                    if (!uiState.isDownloading) {
                                        OutlinedButton(
                                            onClick = viewModel::dismissUpdateDialog,
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Cancel")
                                        }
                                    }
                                }
                            )
                        }

                        // ─── Post-Update "What's New" Dialog ───────────────────────
                        if (uiState.whatsNewVersion != null) {
                            AlertDialog(
                                onDismissRequest = viewModel::dismissWhatsNewDialog,
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF4CAF50),
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = "Welcome to MoodTunes v${com.moodtunes.app.BuildConfig.VERSION_NAME}!",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "App successfully updated! Here is what's new in this version:",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = "✨ Version ${com.moodtunes.app.BuildConfig.VERSION_NAME} Highlights:",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = "• In-App Update Engine: Auto-downloads and installs updates directly within MoodTunes\n• Post-Update Welcome Window displaying changelog and version info\n• JioSaavn Integration: 16+ Indian regional languages, classical ragas & Bollywood\n• ListenBrainz Scrobbler & Navidrome Self-Hosted Lossless Music Server\n• 24/7 Global Internet Radio Streams (35,000+ stations)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = viewModel::dismissWhatsNewDialog,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("OK")
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(30.dp))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
