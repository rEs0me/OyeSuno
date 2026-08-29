package com.oyesuno.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oyesuno.core.database.OyeSunoDatabase
import com.oyesuno.core.database.repository.RecordingRepository
import com.oyesuno.core.model.AudioFormat
import com.oyesuno.core.model.QualityPreset
import com.oyesuno.core.storage.StorageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val prefs = SettingsPreferences(context)
    private val storageManager = StorageManager(context)
    private val database = OyeSunoDatabase.getInstance(context)
    private val repository = RecordingRepository(database)

    data class SettingsUiState(
        val audioFormat: AudioFormat = AudioFormat.M4A_AAC,
        val quality: QualityPreset = QualityPreset.HIGH,
        val audioSource: com.oyesuno.core.model.AudioSourceOption = com.oyesuno.core.model.AudioSourceOption.VOICE_RECOGNITION,
        val defaultDurationMs: Long = 0L,
        val wakeWordEnabled: Boolean = false,
        val themeMode: String = "SYSTEM",
        val notificationsEnabled: Boolean = true,
        val autoRecordCalls: Boolean = false,
        val autoSyncDeviceRecordings: Boolean = true,
        val storageUsedBytes: Long = 0L,
        val availableBytes: Long = 0L,
        val recordingCount: Int = 0
    )

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            audioFormat = prefs.audioFormat,
            quality = prefs.qualityPreset,
            audioSource = prefs.audioSource,
            defaultDurationMs = prefs.defaultDurationMs,
            wakeWordEnabled = prefs.wakeWordEnabled,
            themeMode = prefs.themeMode,
            notificationsEnabled = prefs.notificationsEnabled,
            autoRecordCalls = prefs.autoRecordCalls,
            autoSyncDeviceRecordings = prefs.autoSyncDeviceRecordings,
            storageUsedBytes = storageManager.getTotalAudioSize(),
            availableBytes = storageManager.getAvailableSpace()
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getRecordingCount().collect { count: Int ->
                _uiState.value = _uiState.value.copy(
                    recordingCount = count,
                    storageUsedBytes = storageManager.getTotalAudioSize(),
                    availableBytes = storageManager.getAvailableSpace()
                )
            }
        }
    }

    fun setAudioFormat(format: AudioFormat) {
        prefs.audioFormat = format
        _uiState.value = _uiState.value.copy(audioFormat = format)
    }

    fun setAudioSource(source: com.oyesuno.core.model.AudioSourceOption) {
        prefs.audioSource = source
        _uiState.value = _uiState.value.copy(audioSource = source)
    }

    fun setQuality(quality: QualityPreset) {
        prefs.qualityPreset = quality
        _uiState.value = _uiState.value.copy(quality = quality)
    }

    fun setDefaultDuration(durationMs: Long) {
        prefs.defaultDurationMs = durationMs
        _uiState.value = _uiState.value.copy(defaultDurationMs = durationMs)
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        prefs.wakeWordEnabled = enabled
        _uiState.value = _uiState.value.copy(wakeWordEnabled = enabled)
    }

    fun setThemeMode(mode: String) {
        prefs.themeMode = mode
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.notificationsEnabled = enabled
        _uiState.value = _uiState.value.copy(notificationsEnabled = enabled)
    }

    fun setAutoRecordCalls(enabled: Boolean) {
        prefs.autoRecordCalls = enabled
        _uiState.value = _uiState.value.copy(autoRecordCalls = enabled)
    }

    fun setAutoSyncDeviceRecordings(enabled: Boolean) {
        prefs.autoSyncDeviceRecordings = enabled
        _uiState.value = _uiState.value.copy(autoSyncDeviceRecordings = enabled)
    }
}
