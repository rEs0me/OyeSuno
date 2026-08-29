package com.oyesuno.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oyesuno.core.model.FilterType
import com.oyesuno.core.model.SortOrder
import com.oyesuno.core.ui.components.EmptyState
import com.oyesuno.core.ui.components.OyeSunoTopBar
import com.oyesuno.core.ui.components.RecordingCard
import com.oyesuno.core.util.TimeUtils

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.FileDownload

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onRecordingClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showSearch by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importAudioFiles(uris)
            Toast.makeText(context, "Imported ${uris.size} audio files", Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Top bar
        OyeSunoTopBar(
            title = "Recordings",
            actions = {
                // 1-Tap Auto-Scan & Fetch System Audio
                IconButton(
                    onClick = {
                        viewModel.scanAndMigrateAllDeviceRecordings()
                        Toast.makeText(context, "Scanning phone for voice & call recordings...", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Outlined.Sync, "Fetch all device recordings")
                }
                IconButton(onClick = { importPicker.launch(arrayOf("audio/*")) }) {
                    Icon(Icons.Outlined.FileDownload, "Import external audio files")
                }
                IconButton(onClick = { showSearch = !showSearch }) {
                    Icon(
                        if (showSearch) Icons.Filled.Close else Icons.Outlined.Search,
                        "Search"
                    )
                }
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.AutoMirrored.Outlined.Sort, "Sort")
                    }
                    SortDropdownMenu(
                        expanded = showSortMenu,
                        currentSort = uiState.sortOrder,
                        onSortSelected = { viewModel.setSortOrder(it) },
                        onDismiss = { showSortMenu = false }
                    )
                }
            }
        )

        // Search bar
        AnimatedVisibility(
            visible = showSearch,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search recordings...") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // Filter chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = FilterType.entries
            items(filters) { filter ->
                FilterChip(
                    selected = uiState.filterType == filter,
                    onClick = { viewModel.setFilterType(filter) },
                    label = { Text(formatFilterLabel(filter)) },
                    leadingIcon = {
                        if (uiState.filterType == filter) {
                            Icon(Icons.Filled.Check, null, modifier = Modifier)
                        }
                    }
                )
            }
        }

        // Count label
        Text(
            text = "${uiState.filteredRecordings.size} recording${if (uiState.filteredRecordings.size != 1) "s" else ""}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // Recording list
        if (uiState.filteredRecordings.isEmpty() && !uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Outlined.LibraryMusic,
                    title = if (uiState.searchQuery.isNotBlank()) "No results"
                           else "Nothing here yet.",
                    subtitle = if (uiState.searchQuery.isNotBlank()) "Try a different search."
                              else "Start recording to build your library."
                )
            }
        } else {
            val grouped = uiState.filteredRecordings.groupBy { TimeUtils.formatRelativeDate(it.createdAt) }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                grouped.forEach { (dateHeader, itemsInGroup) ->
                    item(key = "header_$dateHeader") {
                        Text(
                            text = dateHeader,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                        )
                    }
                    items(
                        items = itemsInGroup,
                        key = { it.id }
                    ) { recording ->
                        RecordingCard(
                            title = recording.title,
                            dateText = TimeUtils.formatTimestamp(recording.createdAt),
                            durationText = TimeUtils.formatDuration(recording.durationMs),
                            waveformAmplitudes = List(30) { kotlin.random.Random.nextFloat() * 0.7f + 0.1f },
                            isFavorite = recording.isFavorite,
                            categoryName = recording.categoryName,
                            onClick = { onRecordingClick(recording.id) },
                            onFavoriteClick = { viewModel.toggleFavorite(recording.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortDropdownMenu(
    expanded: Boolean,
    currentSort: SortOrder,
    onSortSelected: (SortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        SortOrder.entries.forEach { sort ->
            DropdownMenuItem(
                text = { Text(formatSortLabel(sort)) },
                onClick = {
                    onSortSelected(sort)
                    onDismiss()
                },
                leadingIcon = {
                    if (currentSort == sort) {
                        Icon(Icons.Filled.Check, null)
                    }
                }
            )
        }
    }
}

private fun formatFilterLabel(filter: FilterType): String = when (filter) {
    FilterType.ALL -> "All"
    FilterType.FAVORITES -> "Favorites"
    FilterType.TODAY -> "Today"
    FilterType.THIS_WEEK -> "This Week"
    FilterType.SCHEDULED -> "Scheduled"
    FilterType.WAKE_WORD_TRIGGERED -> "Wake Word"
    FilterType.MANUAL -> "Manual"
    FilterType.TIMED -> "Timed"
}

private fun formatSortLabel(sort: SortOrder): String = when (sort) {
    SortOrder.NEWEST -> "Newest first"
    SortOrder.OLDEST -> "Oldest first"
    SortOrder.LONGEST -> "Longest first"
    SortOrder.SHORTEST -> "Shortest first"
    SortOrder.NAME_AZ -> "Name A→Z"
    SortOrder.NAME_ZA -> "Name Z→A"
}
