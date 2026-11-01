package com.moodtunes.app.presentation.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A third-party component and the license text that must accompany it. */
private data class LicenseEntry(
    val name: String,
    val license: String,
    val assetPath: String
)

private val LICENSE_CATALOG = listOf(
    LicenseEntry("MoodTunes", "GPL-3.0", "licenses/gpl-3.0.txt"),
    LicenseEntry("AndroidX · Core, Lifecycle, Activity, Compose, Navigation", "Apache-2.0", "licenses/apache-2.0.txt"),
    LicenseEntry("AndroidX Media3 / ExoPlayer", "Apache-2.0", "licenses/apache-2.0.txt"),
    LicenseEntry("Hilt / Dagger", "Apache-2.0", "licenses/apache-2.0.txt"),
    LicenseEntry("Room", "Apache-2.0", "licenses/apache-2.0.txt"),
    LicenseEntry("DataStore", "Apache-2.0", "licenses/apache-2.0.txt"),
    LicenseEntry("Coil", "Apache-2.0", "licenses/apache-2.0.txt"),
    LicenseEntry("OkHttp", "Apache-2.0", "licenses/apache-2.0.txt"),
    LicenseEntry("Lottie", "Apache-2.0", "licenses/apache-2.0.txt"),
    LicenseEntry("Timber", "Apache-2.0", "licenses/apache-2.0.txt"),
    LicenseEntry("Accompanist", "Apache-2.0", "licenses/apache-2.0.txt"),
    LicenseEntry("NewPipe Extractor", "GPL-3.0", "licenses/gpl-3.0.txt"),
    LicenseEntry("yt-dlp EJS solver", "Unlicense", "licenses/ejs-unlicense.txt"),
    LicenseEntry("meriyah (bundled by EJS)", "ISC", "licenses/meriyah-isc.txt"),
    LicenseEntry("astring (bundled by EJS)", "MIT", "licenses/astring-mit.txt")
)

/**
 * Full-screen list of bundled third-party components with their license texts.
 * Text is shipped in `assets/licenses/` so attribution travels with the APK.
 */
@Composable
fun OpenSourceLicensesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<LicenseEntry?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = 640.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            val entry = selected
            if (entry == null) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Open-source licenses",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "MoodTunes is built on the following open-source projects.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(LICENSE_CATALOG) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selected = item }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = item.license,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("Close") }
                    }
                }
            } else {
                val licenseText by produceState("Loading…", entry.assetPath) {
                    value = withContext(Dispatchers.IO) {
                        runCatching {
                            context.assets.open(entry.assetPath)
                                .bufferedReader()
                                .use { it.readText() }
                        }.getOrDefault("License text unavailable.")
                    }
                }

                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selected = null }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back to license list"
                            )
                        }
                        Column {
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = entry.license,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = licenseText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .heightIn(max = 460.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("Close") }
                    }
                }
            }
        }
    }
}
