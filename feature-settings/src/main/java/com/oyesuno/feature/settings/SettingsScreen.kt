package com.oyesuno.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Payment
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oyesuno.core.model.AudioFormat
import com.oyesuno.core.model.QualityPreset
import com.oyesuno.core.ui.components.OyeSunoTopBar
import com.oyesuno.core.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var showFormatDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showAudioSourceDialog by remember { mutableStateOf(false) }
    var showDurationDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
        }
    }

    fun sendEmail(email: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$email")
                putExtra(Intent.EXTRA_SUBJECT, "OyeSuno Feedback & Support")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No email client found", Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        OyeSunoTopBar(title = "Settings")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Audio Recording Section
            item { SettingsSectionHeader("Recording") }

            item {
                SettingsItem(
                    icon = Icons.Outlined.Mic,
                    title = "Audio Input Source",
                    subtitle = "${uiState.audioSource.label} · ${uiState.audioSource.description}",
                    onClick = { showAudioSourceDialog = true }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Outlined.Mic,
                    title = "Default format",
                    subtitle = when (uiState.audioFormat) {
                        AudioFormat.M4A_AAC -> "M4A / AAC (Lossless studio clarity)"
                        AudioFormat.OPUS -> "OPUS (High compression, voice)"
                        AudioFormat.WAV -> "WAV (Uncompressed PCM)"
                    },
                    onClick = { showFormatDialog = true }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Outlined.HighQuality,
                    title = "Audio quality preset",
                    subtitle = "${uiState.quality.label} · ${uiState.quality.description}",
                    onClick = { showQualityDialog = true }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Outlined.Timer,
                    title = "Default timer limit",
                    subtitle = if (uiState.defaultDurationMs > 0)
                        TimeUtils.formatDuration(uiState.defaultDurationMs)
                    else "Unlimited",
                    onClick = { showDurationDialog = true }
                )
            }

            // Notifications Section
            item { SettingsSectionHeader("Notifications & Controls") }
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Notifications, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Recording Notification", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                if (uiState.notificationsEnabled) "Shows live timer & controls in status bar"
                                else "Notifications disabled while recording",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.notificationsEnabled,
                            onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                        )
                    }
                }
            }

            // Calls & WhatsApp Recording Section
            item { SettingsSectionHeader("Auto Call & VoIP Recording") }
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.PhoneInTalk, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Record Calls & WhatsApp", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                if (uiState.autoRecordCalls) "Automatically records when Phone or WhatsApp calls connect"
                                else "Call auto-recording disabled",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.autoRecordCalls,
                            onCheckedChange = { viewModel.setAutoRecordCalls(it) }
                        )
                    }
                }
            }

            // Storage Section
            item { SettingsSectionHeader("Storage & Library") }
            item {
                val usedMb = uiState.storageUsedBytes / (1024 * 1024)
                val freeGb = String.format("%.1f", uiState.availableBytes / (1024f * 1024f * 1024f))
                SettingsItem(
                    icon = Icons.Outlined.Folder,
                    title = "Audio Storage",
                    subtitle = "${uiState.recordingCount} recordings (${usedMb} MB used · ${freeGb} GB free)"
                )
            }
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Sync, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Sync Device Audio", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Automatically includes phone recordings in your library",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.autoSyncDeviceRecordings,
                            onCheckedChange = { viewModel.setAutoSyncDeviceRecordings(it) }
                        )
                    }
                }
            }

            // Appearance Section
            item { SettingsSectionHeader("Appearance") }
            item {
                SettingsItem(
                    icon = Icons.Outlined.DarkMode,
                    title = "Theme",
                    subtitle = when (uiState.themeMode) {
                        "DARK" -> "Dark mode"
                        "LIGHT" -> "Light mode"
                        else -> "Follow system default"
                    },
                    onClick = { showThemeDialog = true }
                )
            }

            // Privacy Section
            item { SettingsSectionHeader("Privacy & Security") }
            item {
                SettingsItem(
                    icon = Icons.Outlined.Shield,
                    title = "On-Device Processing",
                    subtitle = "100% offline. Recordings and wake word detection never leave your device."
                )
            }

            // Support Creator Section
            item { SettingsSectionHeader("Support the Creator") }

            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(MaterialTheme.colorScheme.tertiary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.VolunteerActivism,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Support Indie Engineering",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Fuel free & open-source software",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Support the creator for more projects like this! Even ₹1 can help someone and fuel new open-source audio tools ❤️",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // UPI ID Chip with Copy & Pay Intent
                        Surface(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("UPI ID", "anupambuilds@fam")
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "UPI ID copied: anupambuilds@fam", Toast.LENGTH_SHORT).show()

                                try {
                                    val upiIntent = Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay?pa=anupambuilds@fam&pn=Anupam%20Jha&cu=INR")).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(upiIntent)
                                } catch (e: Exception) { /* ignore */ }
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.Payment,
                                        null,
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "anupambuilds@fam",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Tap to Pay via any UPI App / Copy",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Icon(
                                    Icons.Outlined.ContentCopy,
                                    null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Developer & About Section
            item { SettingsSectionHeader("About the Creator") }

            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        // Header with Avatar & Name
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(
                                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.secondary
                                            )
                                        ),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "AJ",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = androidx.compose.ui.graphics.Color.White
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Text(
                                    text = "Anupam Jha",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Lead Developer & System Architect",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "OyeSuno v1.0.0 · 100% Offline",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Social Links
                        CreatorTile(
                            iconContent = {
                                Icon(
                                    painter = painterResource(com.oyesuno.core.ui.R.drawable.ic_instagram),
                                    contentDescription = "Instagram",
                                    tint = androidx.compose.ui.graphics.Color(0xFFE1306C),
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = "Instagram",
                            value = "@tech.anupam",
                            onClick = { openUrl("https://instagram.com/tech.anupam") }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        CreatorTile(
                            iconContent = {
                                Icon(
                                    Icons.Outlined.Language,
                                    contentDescription = "Portfolio",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = "Portfolio",
                            value = "anupambuilds.store",
                            onClick = { openUrl("https://anupambuilds.store") }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        CreatorTile(
                            iconContent = {
                                Icon(
                                    painter = painterResource(com.oyesuno.core.ui.R.drawable.ic_github),
                                    contentDescription = "GitHub",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = "GitHub",
                            value = "tech-anupam",
                            onClick = { openUrl("https://github.com/tech-anupam") }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        CreatorTile(
                            iconContent = {
                                Icon(
                                    Icons.Outlined.Email,
                                    contentDescription = "Email",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = "Support Email",
                            value = "killerboy99126@gmail.com",
                            onClick = { sendEmail("killerboy99126@gmail.com") }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // --- Dialogs ---

    if (showFormatDialog) {
        FormatSelectionDialog(
            currentFormat = uiState.audioFormat,
            onSelect = {
                viewModel.setAudioFormat(it)
                showFormatDialog = false
            },
            onDismiss = { showFormatDialog = false }
        )
    }

    if (showQualityDialog) {
        QualitySelectionDialog(
            currentQuality = uiState.quality,
            onSelect = {
                viewModel.setQuality(it)
                showQualityDialog = false
            },
            onDismiss = { showQualityDialog = false }
        )
    }

    if (showDurationDialog) {
        DurationSelectionDialog(
            currentDurationMs = uiState.defaultDurationMs,
            onSelect = {
                viewModel.setDefaultDuration(it)
                showDurationDialog = false
            },
            onDismiss = { showDurationDialog = false }
        )
    }

    if (showAudioSourceDialog) {
        AudioSourceSelectionDialog(
            currentSource = uiState.audioSource,
            onSelect = {
                viewModel.setAudioSource(it)
                showAudioSourceDialog = false
            },
            onDismiss = { showAudioSourceDialog = false }
        )
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = uiState.themeMode,
            onSelect = {
                viewModel.setThemeMode(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onClick != null) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CreatorTile(
    iconContent: @Composable () -> Unit,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                iconContent()
                Spacer(modifier = Modifier.width(12.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    Icons.AutoMirrored.Outlined.OpenInNew,
                    null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun FormatSelectionDialog(
    currentFormat: AudioFormat,
    onSelect: (AudioFormat) -> Unit,
    onDismiss: () -> Unit
) {
    val formats = listOf(
        AudioFormat.M4A_AAC to "M4A (AAC) · Recommended crystal clear",
        AudioFormat.OPUS to "OPUS · Compact Voice compression",
        AudioFormat.WAV to "WAV · Uncompressed Studio Master"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default Audio Format") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                formats.forEach { (format, label) ->
                    FilterChip(
                        selected = currentFormat == format,
                        onClick = { onSelect(format) },
                        label = { Text(label) },
                        leadingIcon = {
                            if (currentFormat == format) {
                                Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun QualitySelectionDialog(
    currentQuality: QualityPreset,
    onSelect: (QualityPreset) -> Unit,
    onDismiss: () -> Unit
) {
    val presets = QualityPreset.entries.map { it to "${it.label} · ${it.description}" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio Quality Preset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { (preset, label) ->
                    FilterChip(
                        selected = currentQuality == preset,
                        onClick = { onSelect(preset) },
                        label = { Text(label) },
                        leadingIcon = {
                            if (currentQuality == preset) {
                                Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun DurationSelectionDialog(
    currentDurationMs: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val durations = listOf(
        0L to "Unlimited (Manual stop)",
        60_000L to "1 Minute",
        300_000L to "5 Minutes",
        600_000L to "10 Minutes",
        1_800_000L to "30 Minutes",
        3_600_000L to "1 Hour"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default Timer Limit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                durations.forEach { (durationMs, label) ->
                    FilterChip(
                        selected = currentDurationMs == durationMs,
                        onClick = { onSelect(durationMs) },
                        label = { Text(label) },
                        leadingIcon = {
                            if (currentDurationMs == durationMs) {
                                Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun ThemeSelectionDialog(
    currentTheme: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val themes = listOf(
        "SYSTEM" to "System Default",
        "DARK" to "Dark Mode",
        "LIGHT" to "Light Mode"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App Theme") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                themes.forEach { (themeKey, label) ->
                    FilterChip(
                        selected = currentTheme == themeKey,
                        onClick = { onSelect(themeKey) },
                        label = { Text(label) },
                        leadingIcon = {
                            if (currentTheme == themeKey) {
                                Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun AudioSourceSelectionDialog(
    currentSource: com.oyesuno.core.model.AudioSourceOption,
    onSelect: (com.oyesuno.core.model.AudioSourceOption) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio Input Source") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                com.oyesuno.core.model.AudioSourceOption.entries.forEach { option ->
                    FilterChip(
                        selected = currentSource == option,
                        onClick = { onSelect(option) },
                        label = {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(option.label, fontWeight = FontWeight.Medium)
                                Text(
                                    option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        leadingIcon = {
                            if (currentSource == option) {
                                Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
