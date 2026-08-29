package com.oyesuno.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oyesuno.core.database.OyeSunoDatabase
import com.oyesuno.core.database.repository.RecordingRepository
import com.oyesuno.core.model.Recording
import com.oyesuno.core.storage.AudioImporter
import com.oyesuno.core.storage.StorageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val database = OyeSunoDatabase.getInstance(application)
    private val repository = RecordingRepository(database)
    private val storageManager = StorageManager(context)
    private val audioImporter = AudioImporter(context, storageManager)

    data class HomeUiState(
        val recentRecordings: List<Recording> = emptyList(),
        val filteredRecordings: List<Recording> = emptyList(),
        val searchQuery: String = "",
        val totalCount: Int = 0,
        val isLoading: Boolean = true,
        val isSyncing: Boolean = false
    )

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var allRecordingsList: List<Recording> = emptyList()

    init {
        viewModelScope.launch {
            repository.getAllRecordings().collect { recordings ->
                allRecordingsList = recordings
                filterRecordings(_uiState.value.searchQuery)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        filterRecordings(query)
    }

    private fun filterRecordings(query: String) {
        val filtered = if (query.isBlank()) {
            allRecordingsList
        } else {
            allRecordingsList.filter { it.title.contains(query, ignoreCase = true) }
        }
        _uiState.value = _uiState.value.copy(
            recentRecordings = allRecordingsList.take(5),
            filteredRecordings = filtered,
            totalCount = allRecordingsList.size,
            isLoading = false
        )
    }

    fun toggleFavorite(recordingId: Long) {
        viewModelScope.launch {
            repository.toggleFavorite(recordingId)
        }
    }

    fun renameRecording(recordingId: Long, newTitle: String) {
        viewModelScope.launch {
            if (newTitle.isNotBlank()) {
                repository.renameRecording(recordingId, newTitle.trim())
            }
        }
    }

    fun deleteRecording(recordingId: Long) {
        viewModelScope.launch {
            repository.deleteRecordingById(recordingId)
        }
    }

    fun importDeviceRecordings(onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true)
            val importedList = audioImporter.scanDeviceRecordings()
            for (info in importedList) {
                repository.insertRecording(
                    Recording(
                        title = info.title,
                        filePath = info.filePath,
                        durationMs = info.durationMs,
                        fileSize = info.fileSize,
                        format = info.format,
                        sampleRate = info.sampleRate,
                        bitrate = info.bitrate,
                        createdAt = info.createdAt
                    )
                )
            }
            _uiState.value = _uiState.value.copy(isSyncing = false)
            onComplete(importedList.size)
        }
    }
}
