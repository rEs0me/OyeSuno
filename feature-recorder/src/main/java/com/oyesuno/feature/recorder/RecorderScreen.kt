package com.oyesuno.feature.recorder

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oyesuno.core.model.RecordingState
import com.oyesuno.core.permissions.PermissionHelper
import com.oyesuno.core.ui.components.WaveformView
import com.oyesuno.core.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecorderScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecorderViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showTimerPicker by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showPermissionRationale by remember { mutableStateOf(false) }

    fun openAppSettings() {
        try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Could not open settings", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showPermissionRationale = false
            viewModel.startRecording(
                format = uiState.format,
                quality = uiState.quality,
                maxDurationMs = uiState.maxDurationMs
            )
        } else {
            showPermissionRationale = true
        }
    }

    // Auto-start recording immediately on 1 single tap when entering screen (only if not already recording)
    LaunchedEffect(Unit) {
        if (uiState.recordingState is RecordingState.Idle && !com.oyesuno.core.audio.recorder.RecordingEngineImpl.isAnyRecordingActive) {
            if (PermissionHelper.hasRecordAudioPermission(context)) {
                viewModel.startRecording(
                    format = uiState.format,
                    quality = uiState.quality,
                    maxDurationMs = uiState.maxDurationMs
                )
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RecorderViewModel.RecorderEvent.RecordingSaved -> {
                    showSaveDialog = true
                }
                is RecorderViewModel.RecorderEvent.Error -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    val isRecording = uiState.recordingState is RecordingState.Recording
    val isPaused = uiState.recordingState is RecordingState.Paused
    val isIdle = uiState.recordingState is RecordingState.Idle

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (!isIdle) {
                        // Pulsing recording indicator
                        RecordingIndicator(isPaused = isPaused)
                    }
                },
                navigationIcon = {
                    if (isIdle) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                },
                actions = {
                    if (isIdle) {
                        // Timer chip
                        FilterChip(
                            selected = uiState.maxDurationMs > 0,
                            onClick = { showTimerPicker = true },
                            label = {
                                if (uiState.maxDurationMs > 0) {
                                    Text(TimeUtils.formatDuration(uiState.maxDurationMs))
                                } else {
                                    Text("Timer")
                                }
                            },
                            leadingIcon = { Icon(Icons.Outlined.Timer, null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Timer display
            AnimatedContent(
                targetState = uiState.elapsedTimeMs,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                label = "timer"
            ) { elapsed ->
                Text(
                    text = TimeUtils.formatDuration(elapsed),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Light,
                    color = if (isPaused) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                           else MaterialTheme.colorScheme.onSurface
                )
            }

            // Timer progress bar (timed recording)
            if (uiState.maxDurationMs > 0 && !isIdle) {
                Spacer(modifier = Modifier.height(12.dp))
                val progress = (uiState.elapsedTimeMs.toFloat() / uiState.maxDurationMs).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = "${TimeUtils.formatDuration(uiState.elapsedTimeMs)} / ${TimeUtils.formatDuration(uiState.maxDurationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Waveform
            WaveformView(
                amplitudes = if (uiState.amplitudes.isNotEmpty()) uiState.amplitudes
                            else List(60) { 0.1f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                activeColor = if (isPaused) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                             else MaterialTheme.colorScheme.primary,
                inactiveColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Bookmark indicator
            if (uiState.bookmarkCount > 0) {
                Text(
                    text = "${uiState.bookmarkCount} bookmark${if (uiState.bookmarkCount > 1) "s" else ""}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isIdle) {
                    // Before recording: record button + clear label
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RecordFab(onClick = {
                            if (PermissionHelper.hasRecordAudioPermission(context)) {
                                viewModel.startRecording(
                                    format = uiState.format,
                                    quality = uiState.quality,
                                    maxDurationMs = uiState.maxDurationMs
                                )
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        })
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Tap to start recording",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${uiState.format.name} · Studio Quality",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // During recording: bookmark, pause/resume, stop
                    BadgedBox(
                        badge = {
                            if (uiState.bookmarkCount > 0) {
                                Badge { Text("${uiState.bookmarkCount}") }
                            }
                        }
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                viewModel.addBookmark()
                                android.widget.Toast.makeText(
                                    context,
                                    "Bookmark pinned at ${TimeUtils.formatDuration(uiState.elapsedTimeMs)}",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Filled.Bookmark, "Add bookmark")
                        }
                    }

                    // Pause/Resume
                    FilledIconButton(
                        onClick = {
                            if (isPaused) viewModel.resumeRecording()
                            else viewModel.pauseRecording()
                        },
                        modifier = Modifier.size(72.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (isPaused) "Resume" else "Pause",
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Stop
                    FilledIconButton(
                        onClick = { viewModel.stopRecording() },
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Filled.Stop,
                            "Stop recording",
                            tint = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // Timer picker dialog
    if (showTimerPicker) {
        TimerPickerDialog(
            currentDurationMs = uiState.maxDurationMs,
            onDurationSelected = { durationMs ->
                viewModel.setTimedDuration(durationMs)
                showTimerPicker = false
            },
            onDismiss = { showTimerPicker = false }
        )
    }

    // Save dialog
    if (showSaveDialog && uiState.savedRecording != null) {
        SaveRecordingDialog(
            defaultTitle = uiState.savedRecording!!.title,
            onSave = { title ->
                showSaveDialog = false
                viewModel.clearSavedRecording()
                onNavigateBack()
            },
            onDismiss = {
                showSaveDialog = false
                viewModel.clearSavedRecording()
                onNavigateBack()
            }
        )
    }

    // Permission Rationale Dialog
    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text("Microphone Access Required") },
            text = {
                Text(
                    "OyeSuno needs microphone access to record crystal-clear audio. Please grant permission, or enable it from App Settings if previously denied."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionRationale = false
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { openAppSettings() }) {
                        Text("Open Settings")
                    }
                    TextButton(onClick = { showPermissionRationale = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

@Composable
private fun RecordingIndicator(isPaused: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPaused) 0.3f else 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(
                    if (isPaused) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.error.copy(alpha = alpha)
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isPaused) "Paused" else "Recording",
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun RecordFab(onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(80.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(
            Icons.Filled.Mic,
            contentDescription = "Start recording",
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
private fun TimerPickerDialog(
    currentDurationMs: Long,
    onDurationSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val timerOptions = listOf(
        "No limit" to 0L,
        "1 minute" to 60_000L,
        "5 minutes" to 300_000L,
        "10 minutes" to 600_000L,
        "15 minutes" to 900_000L,
        "30 minutes" to 1_800_000L,
        "1 hour" to 3_600_000L,
        "2 hours" to 7_200_000L
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recording timer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                timerOptions.forEach { (label, durationMs) ->
                    FilterChip(
                        selected = currentDurationMs == durationMs,
                        onClick = { onDurationSelected(durationMs) },
                        label = { Text(label, modifier = Modifier.fillMaxWidth()) },
                        leadingIcon = {
                            if (currentDurationMs == durationMs) {
                                Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SaveRecordingDialog(
    defaultTitle: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(defaultTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Text("Recording saved!", textAlign = TextAlign.Center)
        },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Recording name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(title) }) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Discard")
            }
        }
    )
}
