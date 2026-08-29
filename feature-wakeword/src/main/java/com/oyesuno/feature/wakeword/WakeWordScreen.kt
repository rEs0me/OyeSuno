package com.oyesuno.feature.wakeword

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oyesuno.core.permissions.PermissionHelper

@Composable
fun WakeWordScreen(
    modifier: Modifier = Modifier,
    onStartRecording: () -> Unit = {},
    viewModel: WakeWordViewModel = viewModel()
) {
    val context = LocalContext.current
    val isEnabled by viewModel.isEnabled.collectAsState()
    val customKeyword by viewModel.customKeyword.collectAsState()

    var showEditWordDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleListening(true)
        } else {
            android.widget.Toast.makeText(
                context,
                "Microphone permission is required for Wake Word detection",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.onTriggered.collect {
            android.widget.Toast.makeText(context, "\"$customKeyword\" detected! Starting recording...", android.widget.Toast.LENGTH_SHORT).show()
            onStartRecording()
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Wake Word Detection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Start recording hands-free with voice",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = { checked ->
                    if (checked) {
                        if (PermissionHelper.hasRecordAudioPermission(context)) {
                            viewModel.toggleListening(true)
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    } else {
                        viewModel.toggleListening(false)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Status card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isEnabled)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isEnabled) Icons.Filled.Mic else Icons.Filled.MicOff,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = if (isEnabled) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isEnabled) "Active · Listening for \"$customKeyword\"" else "Wake Word Inactive",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isEnabled) MaterialTheme.colorScheme.onPrimaryContainer
                               else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isEnabled) "Say \"$customKeyword\" or whisper clearly near your phone."
                              else "Toggle on to start recording hands-free anytime.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isEnabled) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showEditWordDialog = true }) {
                    Icon(Icons.Outlined.Edit, "Edit trigger word", modifier = Modifier.size(20.dp))
                }
            }
        }

        // Settings (only visible when enabled)
        AnimatedVisibility(visible = isEnabled) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                // Trigger word chips
                Text("Preset trigger words", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Suno", "Oye Suno", "Jarvis", "Record").forEach { word ->
                        AssistChip(
                            onClick = { viewModel.setCustomKeyword(word) },
                            label = { Text(word) },
                            leadingIcon = if (customKeyword.equals(word, ignoreCase = true)) {
                                { Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Info card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "100% On-Device & Offline: Audio is analyzed locally with zero latency, zero cloud transfer, and zero beep sounds.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }

    // Edit word dialog
    if (showEditWordDialog) {
        var newWord by remember { mutableStateOf(customKeyword) }
        AlertDialog(
            onDismissRequest = { showEditWordDialog = false },
            title = { Text("Custom Trigger Word") },
            text = {
                Column {
                    Text(
                        "Enter any word or phrase you want to use as your wake word:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newWord,
                        onValueChange = { newWord = it },
                        label = { Text("Trigger Word") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newWord.isNotBlank()) {
                            viewModel.setCustomKeyword(newWord.trim())
                        }
                        showEditWordDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditWordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
