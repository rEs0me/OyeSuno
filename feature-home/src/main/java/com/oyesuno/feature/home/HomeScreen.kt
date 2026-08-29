package com.oyesuno.feature.home

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oyesuno.core.audio.recorder.RecordingEngineImpl
import com.oyesuno.core.model.Recording
import com.oyesuno.core.ui.components.EmptyState
import com.oyesuno.core.ui.components.RecordingCard
import com.oyesuno.core.util.TimeUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartRecording: () -> Unit,
    onRecordingClick: (Long) -> Unit,
    onNavigateToTriggers: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var selectedRecordingForOptions by remember { mutableStateOf<Recording?>(null) }
    var recordingToRename by remember { mutableStateOf<Recording?>(null) }
    var recordingToDelete by remember { mutableStateOf<Recording?>(null) }
    var renameInputText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // App Title & Tagline
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "OyeSuno",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Ready when you are · Offline & Secure",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (uiState.isSyncing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
            }
        }

        // Prominent Live Recording Banner (shown whenever a background recording is active)
        val isRecordingActive by RecordingEngineImpl.isRecordingActiveFlow.collectAsState()

        if (isRecordingActive) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                onClick = onStartRecording,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(MaterialTheme.colorScheme.error, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "🎙️ Live Recording in Progress",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "Tap here to view waveforms & stop",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                    FilledIconButton(
                        onClick = onStartRecording,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Filled.Stop,
                            contentDescription = "Stop recording",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search Bar
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            placeholder = { Text("Search recordings...", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                focusedBorderColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Quick Action Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                QuickActionChip(icon = Icons.Outlined.Timer, label = "Timed", onClick = onStartRecording)
            }
            item {
                QuickActionChip(icon = Icons.Outlined.Mic, label = "Wake Word", onClick = onNavigateToTriggers)
            }
            item {
                QuickActionChip(icon = Icons.Outlined.Schedule, label = "Schedule", onClick = onNavigateToTriggers)
            }
            item {
                QuickActionChip(
                    icon = Icons.Outlined.FileDownload,
                    label = if (uiState.isSyncing) "Syncing..." else "Sync",
                    onClick = {
                        if (!uiState.isSyncing) {
                            viewModel.importDeviceRecordings { count ->
                                Toast.makeText(
                                    context,
                                    if (count > 0) "Synced $count recordings" else "No new recordings found",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val displayList = if (uiState.searchQuery.isNotBlank()) uiState.filteredRecordings else uiState.recentRecordings

        if (displayList.isEmpty() && !uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = if (uiState.searchQuery.isNotBlank()) Icons.Filled.Search else Icons.Outlined.Mic,
                    title = if (uiState.searchQuery.isNotBlank()) "No recordings found" else "Oye, suno.",
                    subtitle = if (uiState.searchQuery.isNotBlank()) "Try a different search term" else "Tap record to capture your first memo.",
                    actionLabel = if (uiState.searchQuery.isBlank()) "Start Recording" else null,
                    onAction = onStartRecording
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (uiState.searchQuery.isNotBlank()) "Results (${displayList.size})" else "Recent Recordings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (uiState.totalCount > 0 && uiState.searchQuery.isBlank()) {
                    Text(
                        text = "${uiState.totalCount} total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(
                    items = displayList,
                    key = { it.id }
                ) { recording ->
                    RecordingCard(
                        title = recording.title,
                        dateText = TimeUtils.formatRelativeDate(recording.createdAt),
                        durationText = TimeUtils.formatDuration(recording.durationMs),
                        waveformAmplitudes = emptyList(),
                        isFavorite = recording.isFavorite,
                        categoryName = recording.categoryName,
                        onClick = { onRecordingClick(recording.id) },
                        onLongClick = { selectedRecordingForOptions = recording },
                        onFavoriteClick = { viewModel.toggleFavorite(recording.id) }
                    )
                }
            }
        }
    }

    // Long Press Options Bottom Sheet
    if (selectedRecordingForOptions != null) {
        val recording = selectedRecordingForOptions!!
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { selectedRecordingForOptions = null },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(modifier = Modifier.padding(bottom = 28.dp)) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)) {
                    Text(
                        text = recording.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${TimeUtils.formatDuration(recording.durationMs)} · ${TimeUtils.formatTimestamp(recording.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 1. Rename
                ListItem(
                    headlineContent = { Text("Rename") },
                    leadingContent = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    modifier = Modifier.clickable {
                        selectedRecordingForOptions = null
                        renameInputText = recording.title
                        recordingToRename = recording
                    }
                )

                // 2. Toggle Favorite
                ListItem(
                    headlineContent = { Text(if (recording.isFavorite) "Remove from Favorites" else "Add to Favorites") },
                    leadingContent = {
                        Icon(
                            imageVector = if (recording.isFavorite) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                            contentDescription = null,
                            tint = if (recording.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    modifier = Modifier.clickable {
                        viewModel.toggleFavorite(recording.id)
                        selectedRecordingForOptions = null
                    }
                )

                // 3. Share Audio
                ListItem(
                    headlineContent = { Text("Share Audio") },
                    leadingContent = { Icon(Icons.Outlined.Share, contentDescription = null) },
                    modifier = Modifier.clickable {
                        selectedRecordingForOptions = null
                        try {
                            val file = File(recording.filePath)
                            if (file.exists()) {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "com.oyesuno.fileprovider",
                                    file
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = recording.format.mimeType
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    clipData = ClipData.newUri(context.contentResolver, recording.title, uri)
                                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share recording"))
                            } else {
                                Toast.makeText(context, "Audio file not found on storage", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                // 4. Delete
                ListItem(
                    headlineContent = {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    },
                    leadingContent = {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.clickable {
                        selectedRecordingForOptions = null
                        recordingToDelete = recording
                    }
                )
            }
        }
    }

    // Rename Dialog
    if (recordingToRename != null) {
        val rec = recordingToRename!!
        AlertDialog(
            onDismissRequest = { recordingToRename = null },
            title = { Text("Rename Recording") },
            text = {
                OutlinedTextField(
                    value = renameInputText,
                    onValueChange = { renameInputText = it },
                    singleLine = true,
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameInputText.isNotBlank()) {
                            viewModel.renameRecording(rec.id, renameInputText.trim())
                        }
                        recordingToRename = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { recordingToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (recordingToDelete != null) {
        val rec = recordingToDelete!!
        AlertDialog(
            onDismissRequest = { recordingToDelete = null },
            title = { Text("Delete Recording?") },
            text = {
                Text("Are you sure you want to delete \"${rec.title}\"? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRecording(rec.id)
                        recordingToDelete = null
                        Toast.makeText(context, "Recording deleted", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { recordingToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun QuickActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    )
}
