package com.oyesuno.feature.editor

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oyesuno.core.engine.PlaybackState
import com.oyesuno.core.ui.components.WaveformView
import com.oyesuno.core.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    recordingId: Long,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(recordingId) {
        viewModel.loadRecording(recordingId)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is EditorViewModel.EditorEvent.TrimSaved -> {
                    snackbarHostState.showSnackbar("Trimmed successfully")
                    onNavigateBack()
                }
                is EditorViewModel.EditorEvent.ClipCreated -> {
                    snackbarHostState.showSnackbar("Clip created: ${event.recording.title}")
                }
                is EditorViewModel.EditorEvent.Error -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Recording title
            uiState.recording?.let {
                Text(
                    text = it.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Duration: ${TimeUtils.formatDuration(it.durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Waveform with selection overlay
            val progress = if (uiState.durationMs > 0) {
                (uiState.currentPositionMs.toFloat() / uiState.durationMs).coerceIn(0f, 1f)
            } else 0f

            WaveformView(
                amplitudes = uiState.waveformAmplitudes.ifEmpty { List(100) { 0.3f } },
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Range slider for selection
            val startProgress = if (uiState.durationMs > 0)
                uiState.selectionStartMs.toFloat() / uiState.durationMs else 0f
            val endProgress = if (uiState.durationMs > 0)
                uiState.selectionEndMs.toFloat() / uiState.durationMs else 1f

            RangeSlider(
                value = startProgress..endProgress,
                onValueChange = { range ->
                    viewModel.setSelectionStart(range.start)
                    viewModel.setSelectionEnd(range.endInclusive)
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Selection time labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Start: ${TimeUtils.formatDuration(uiState.selectionStartMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Selected: ${TimeUtils.formatDuration(uiState.selectionEndMs - uiState.selectionStartMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "End: ${TimeUtils.formatDuration(uiState.selectionEndMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Preview button
            val isPlaying = uiState.playbackState is PlaybackState.Playing
            OutlinedButton(
                onClick = { if (isPlaying) viewModel.pause() else viewModel.previewSelection() }
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isPlaying) "Pause" else "Preview selection")
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { viewModel.trim() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.ContentCut, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Trim")
                }
                Button(
                    onClick = { viewModel.createClip() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Layers, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clip")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
