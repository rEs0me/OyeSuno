package com.oyesuno.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oyesuno.core.engine.PlaybackState
import com.oyesuno.core.ui.components.WaveformView
import com.oyesuno.core.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    recordingId: Long,
    onNavigateBack: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(recordingId) {
        viewModel.loadRecording(recordingId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.recording?.title ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            imageVector = if (uiState.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (uiState.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val scrollState = rememberScrollState()

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Metadata Row
            val recording = uiState.recording
            if (recording != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = TimeUtils.formatTimestamp(recording.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${recording.format.name} · ${recording.sampleRate / 1000}kHz · ${recording.bitrate / 1000}kbps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Waveform with dynamic seeking
            val progress = if (uiState.durationMs > 0) {
                (uiState.currentPositionMs.toFloat() / uiState.durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f

            val amplitudes = uiState.waveformAmplitudes.ifEmpty {
                remember { List(80) { (0.15f + (it % 7) * 0.1f).coerceIn(0.1f, 0.9f) } }
            }

            WaveformView(
                amplitudes = amplitudes,
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(horizontal = 16.dp),
                onSeek = { seekProgress ->
                    val targetMs = (seekProgress * uiState.durationMs).toLong()
                    viewModel.seekTo(targetMs)
                }
            )

            // Time labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = TimeUtils.formatDuration(uiState.currentPositionMs),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = TimeUtils.formatDuration(uiState.durationMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Bookmarks Horizontal Strip
            if (uiState.bookmarks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.bookmarks) { bookmark ->
                        val isNear = kotlin.math.abs(uiState.currentPositionMs - bookmark.timestampMs) < 1500
                        Surface(
                            onClick = { viewModel.seekToBookmark(bookmark) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isNear) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = if (isNear) 4.dp else 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Bookmark,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (isNear) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${TimeUtils.formatDuration(bookmark.timestampMs)} ${bookmark.label.ifBlank { "Pin" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isNear) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isNear) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Speed & Booster Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speed Selector
                FilledTonalButton(
                    onClick = { viewModel.cycleSpeed() },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Outlined.Speed,
                        contentDescription = "Speed",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(uiState.speed.label, style = MaterialTheme.typography.labelMedium)
                }

                // Volume & Clarity Booster
                val boostText = when (uiState.audioBoostGainMb) {
                    600 -> "Boost: +6dB"
                    1200 -> "Boost: +12dB"
                    2000 -> "Boost: +20dB Max"
                    else -> "Booster: Normal"
                }
                FilledTonalButton(
                    onClick = { viewModel.cycleAudioBoost() },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Outlined.GraphicEq,
                        contentDescription = "Audio Booster",
                        modifier = Modifier.size(16.dp),
                        tint = if (uiState.audioBoostGainMb > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(boostText, style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main Playback Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.seekToPreviousBookmark() }) {
                    Icon(Icons.Outlined.SkipPrevious, "Previous bookmark")
                }
                IconButton(onClick = { viewModel.seekBackward() }) {
                    Icon(Icons.Outlined.Replay10, "-10s")
                }
                FilledIconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    val isPlaying = uiState.playbackState is PlaybackState.Playing
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                IconButton(onClick = { viewModel.seekForward() }) {
                    Icon(Icons.Outlined.Forward10, "+10s")
                }
                IconButton(onClick = { viewModel.seekToNextBookmark() }) {
                    Icon(Icons.Outlined.SkipNext, "Next bookmark")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action buttons
            val currentRecording = uiState.recording
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionItem(icon = Icons.Outlined.ContentCut, label = "Trim", onClick = onEditClick)
                ActionItem(
                    icon = Icons.Outlined.RecordVoiceOver,
                    label = if (uiState.isDetectingSpeakers) "Splitting..." else "Split Speakers",
                    onClick = { viewModel.detectSpeakers() }
                )
                ActionItem(
                    icon = Icons.Outlined.Share,
                    label = "Share",
                    onClick = {
                        if (currentRecording != null) {
                            try {
                                val file = java.io.File(currentRecording.filePath)
                                val uri = if (file.exists()) {
                                    androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "com.oyesuno.fileprovider",
                                        file
                                    )
                                } else {
                                    android.net.Uri.parse(currentRecording.filePath)
                                }

                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "audio/*"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, currentRecording.title)
                                    putExtra(android.content.Intent.EXTRA_TEXT, "Audio recorded with OyeSuno: ${currentRecording.title}")
                                    clipData = android.content.ClipData.newUri(context.contentResolver, currentRecording.title, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                val chooser = android.content.Intent.createChooser(shareIntent, "Share recording via").apply {
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(chooser)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Could not share file: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }

            // Multi-Speaker Voice Track Mixer Card
            if (uiState.speakerSegments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "🎙️ Multi-Speaker Voice Mixer",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Adjust volume & boost separate speaker tracks independently",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Speaker Timeline Strip
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(uiState.speakerSegments) { segment ->
                                val isSpeaker1 = segment.speakerId == 1
                                val isActive = segment.speakerId == uiState.currentActiveSpeakerId
                                Surface(
                                    onClick = { viewModel.seekTo(segment.startMs) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSpeaker1) {
                                        if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    } else {
                                        if (isActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Outlined.RecordVoiceOver,
                                            null,
                                            modifier = Modifier.size(14.dp),
                                            tint = if (isSpeaker1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${if (isSpeaker1) "Speaker 1" else "Speaker 2"} (${TimeUtils.formatDuration(segment.startMs)})",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Speaker 1 Track Volume Slider & Mute
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Speaker 1 (Near / Host Voice)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "${(uiState.speaker1Volume * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { viewModel.toggleSpeaker1Mute() }) {
                                    Icon(
                                        imageVector = if (uiState.speaker1Muted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                                        contentDescription = "Mute Speaker 1",
                                        tint = if (uiState.speaker1Muted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            Slider(
                                value = uiState.speaker1Volume,
                                onValueChange = { viewModel.setSpeaker1Volume(it) },
                                valueRange = 0.0f..2.5f,
                                enabled = !uiState.speaker1Muted,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Speaker 2 Track Volume Slider & Boost
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Speaker 2 (Far / Guest / Quieter Voice)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "${(uiState.speaker2Volume * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                IconButton(onClick = { viewModel.toggleSpeaker2Mute() }) {
                                    Icon(
                                        imageVector = if (uiState.speaker2Muted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                                        contentDescription = "Mute Speaker 2",
                                        tint = if (uiState.speaker2Muted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            Slider(
                                value = uiState.speaker2Volume,
                                onValueChange = { viewModel.setSpeaker2Volume(it) },
                                valueRange = 0.0f..3.0f,
                                enabled = !uiState.speaker2Muted,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete recording?") },
            text = { Text("This action can't be undone. The recording file will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecording()
                    showDeleteDialog = false
                    onNavigateBack()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        FilledTonalIconButton(onClick = onClick) {
            Icon(icon, label)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
