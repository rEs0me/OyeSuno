package com.oyesuno.feature.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oyesuno.core.database.OyeSunoDatabase
import com.oyesuno.core.database.repository.RecordingRepository
import com.oyesuno.core.model.FilterType
import com.oyesuno.core.model.Recording
import com.oyesuno.core.model.SortOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val database = OyeSunoDatabase.getInstance(application)
    private val repository = RecordingRepository(database)

    data class LibraryUiState(
        val recordings: List<Recording> = emptyList(),
        val filteredRecordings: List<Recording> = emptyList(),
        val searchQuery: String = "",
        val sortOrder: SortOrder = SortOrder.NEWEST,
        val filterType: FilterType = FilterType.ALL,
        val isSearchActive: Boolean = false,
        val totalCount: Int = 0,
        val isLoading: Boolean = true
    )

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST)
    private val _filterType = MutableStateFlow(FilterType.ALL)

    init {
        viewModelScope.launch {
            combine(
                repository.getAllRecordings(),
                _searchQuery,
                _sortOrder,
                _filterType
            ) { recordings, query, sort, filter ->
                val filtered = recordings
                    .let { list -> applyFilter(list, filter) }
                    .let { list -> applySearch(list, query) }
                    .let { list -> applySort(list, sort) }

                LibraryUiState(
                    recordings = recordings,
                    filteredRecordings = filtered,
                    searchQuery = query,
                    sortOrder = sort,
                    filterType = filter,
                    isSearchActive = query.isNotBlank(),
                    totalCount = recordings.size,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun setFilterType(filter: FilterType) {
        _filterType.value = filter
    }

    fun toggleFavorite(recordingId: Long) {
        viewModelScope.launch {
            repository.toggleFavorite(recordingId)
        }
    }

    private val storageManager = com.oyesuno.core.storage.StorageManager(application.applicationContext)
    private val audioImporter = com.oyesuno.core.storage.AudioImporter(application.applicationContext, storageManager)
    private val waveformGenerator = com.oyesuno.core.audio.waveform.WaveformGenerator()

    fun importAudioFiles(uris: List<android.net.Uri>) {
        viewModelScope.launch {
            for (uri in uris) {
                val info = audioImporter.importFromUri(uri) ?: continue
                val recording = Recording(
                    title = info.title,
                    filePath = info.filePath,
                    durationMs = info.durationMs,
                    format = info.format,
                    sampleRate = info.sampleRate,
                    bitrate = info.bitrate,
                    channels = 1,
                    fileSize = info.fileSize,
                    createdAt = info.createdAt
                )
                val id = repository.insertRecording(recording)
                val waveform = waveformGenerator.generateWaveform(info.filePath, 120)
                repository.saveWaveformAmplitudes(id, waveform)
            }
        }
    }

    fun scanAndMigrateAllDeviceRecordings() {
        viewModelScope.launch {
            val list = audioImporter.scanDeviceRecordings()
            for (info in list) {
                val recording = Recording(
                    title = info.title,
                    filePath = info.filePath,
                    durationMs = info.durationMs,
                    format = info.format,
                    sampleRate = info.sampleRate,
                    bitrate = info.bitrate,
                    channels = 1,
                    fileSize = info.fileSize,
                    createdAt = info.createdAt
                )
                val id = repository.insertRecording(recording)
                val waveform = waveformGenerator.generateWaveform(info.filePath, 120)
                repository.saveWaveformAmplitudes(id, waveform)
            }
        }
    }

    fun deleteRecording(recordingId: Long) {
        viewModelScope.launch {
            repository.deleteRecordingById(recordingId)
        }
    }

    private fun applyFilter(recordings: List<Recording>, filter: FilterType): List<Recording> {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()

        return when (filter) {
            FilterType.ALL -> recordings
            FilterType.FAVORITES -> recordings.filter { it.isFavorite }
            FilterType.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                recordings.filter { it.createdAt >= calendar.timeInMillis }
            }
            FilterType.THIS_WEEK -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                recordings.filter { it.createdAt >= calendar.timeInMillis }
            }
            FilterType.SCHEDULED -> recordings.filter { it.triggerType == com.oyesuno.core.model.RecordingTriggerType.SCHEDULE }
            FilterType.WAKE_WORD_TRIGGERED -> recordings.filter { it.triggerType == com.oyesuno.core.model.RecordingTriggerType.WAKE_WORD }
            FilterType.MANUAL -> recordings.filter { it.triggerType == com.oyesuno.core.model.RecordingTriggerType.MANUAL }
            FilterType.TIMED -> recordings.filter { it.triggerType == com.oyesuno.core.model.RecordingTriggerType.TIMER }
        }
    }

    private fun applySearch(recordings: List<Recording>, query: String): List<Recording> {
        if (query.isBlank()) return recordings
        val lowerQuery = query.lowercase()
        return recordings.filter {
            it.title.lowercase().contains(lowerQuery) ||
            it.categoryName?.lowercase()?.contains(lowerQuery) == true
        }
    }

    private fun applySort(recordings: List<Recording>, sort: SortOrder): List<Recording> {
        return when (sort) {
            SortOrder.NEWEST -> recordings.sortedByDescending { it.createdAt }
            SortOrder.OLDEST -> recordings.sortedBy { it.createdAt }
            SortOrder.LONGEST -> recordings.sortedByDescending { it.durationMs }
            SortOrder.SHORTEST -> recordings.sortedBy { it.durationMs }
            SortOrder.NAME_AZ -> recordings.sortedBy { it.title.lowercase() }
            SortOrder.NAME_ZA -> recordings.sortedByDescending { it.title.lowercase() }
        }
    }
}
