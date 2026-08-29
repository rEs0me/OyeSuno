package com.oyesuno.feature.recorder

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oyesuno.core.audio.recorder.RecordingEngineImpl
import com.oyesuno.core.audio.waveform.WaveformGenerator
import com.oyesuno.core.database.OyeSunoDatabase
import com.oyesuno.core.database.repository.RecordingRepository
import com.oyesuno.core.model.AudioFormat
import com.oyesuno.core.model.Bookmark
import com.oyesuno.core.model.QualityPreset
import com.oyesuno.core.model.Recording
import com.oyesuno.core.model.RecordingState
import com.oyesuno.core.model.RecordingTriggerType
import com.oyesuno.core.storage.FileManager
import com.oyesuno.core.storage.StorageManager
import com.oyesuno.core.util.FileNameGenerator
import com.oyesuno.feature.recorder.service.RecordingService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel managing the recording screen state and recording lifecycle.
 * Binds directly to the shared singleton [RecordingEngineImpl] so background recordings
 * (Wake Word, Call Recording, Scheduled) seamlessly show their live progress, timer,
 * and waveform in the UI.
 */
class RecorderViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val storageManager = StorageManager(context)
    private val fileManager = FileManager(context, storageManager)
    private val database = OyeSunoDatabase.getInstance(context)
    private val repository = RecordingRepository(database)
    private val waveformGenerator = WaveformGenerator()
    private val recordingEngine = RecordingEngineImpl.getInstance(context)

    // --- UI State ---

    data class RecorderUiState(
        val recordingState: RecordingState = RecordingState.Idle,
        val elapsedTimeMs: Long = 0L,
        val maxDurationMs: Long = 0L, // 0 = unlimited
        val amplitudes: List<Float> = emptyList(),
        val bookmarkCount: Int = 0,
        val format: AudioFormat = AudioFormat.M4A_AAC,
        val quality: QualityPreset = QualityPreset.STANDARD,
        val savedRecording: Recording? = null,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(RecorderUiState())
    val uiState: StateFlow<RecorderUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RecorderEvent>()
    val events: SharedFlow<RecorderEvent> = _events.asSharedFlow()

    sealed class RecorderEvent {
        data class RecordingSaved(val recording: Recording) : RecorderEvent()
        data class Error(val message: String) : RecorderEvent()
    }

    private var timerJob: Job? = null
    private var amplitudeCollectorJob: Job? = null
    private val collectedAmplitudes = mutableListOf<Float>()
    private var currentFilePath: String = ""
    private val collectedBookmarks = mutableListOf<Pair<Long, String>>()

    init {
        // Initial state sync with shared recording engine
        val currentState = recordingEngine.state.value
        _uiState.value = _uiState.value.copy(
            recordingState = currentState,
            elapsedTimeMs = recordingEngine.getElapsedTimeMs()
        )
        if (currentState is RecordingState.Recording) {
            startTimerUpdates()
            startAmplitudeCollection()
        }

        // Observe recording engine state transitions in real time
        viewModelScope.launch {
            recordingEngine.state.collect { state ->
                _uiState.value = _uiState.value.copy(
                    recordingState = state,
                    elapsedTimeMs = recordingEngine.getElapsedTimeMs()
                )
                when (state) {
                    is RecordingState.Recording -> {
                        if (timerJob == null || timerJob?.isActive == false) {
                            startTimerUpdates()
                        }
                        if (amplitudeCollectorJob == null || amplitudeCollectorJob?.isActive == false) {
                            startAmplitudeCollection()
                        }
                    }
                    is RecordingState.Paused -> {
                        timerJob?.cancel()
                    }
                    is RecordingState.Idle -> {
                        timerJob?.cancel()
                        amplitudeCollectorJob?.cancel()
                    }
                }
            }
        }
    }

    fun startRecording(
        format: AudioFormat = AudioFormat.M4A_AAC,
        quality: QualityPreset = QualityPreset.STANDARD,
        maxDurationMs: Long = 0L
    ) {
        if (_uiState.value.recordingState !is RecordingState.Idle) {
            // Already recording or paused
            return
        }

        viewModelScope.launch {
            try {
                // Check storage
                if (storageManager.isLowStorage(100)) {
                    _events.emit(RecorderEvent.Error("There's not enough storage space."))
                    return@launch
                }

                val file = fileManager.createRecordingFile(format)
                currentFilePath = file.absolutePath
                collectedAmplitudes.clear()
                collectedBookmarks.clear()

                _uiState.value = _uiState.value.copy(
                    format = format,
                    quality = quality,
                    maxDurationMs = maxDurationMs,
                    amplitudes = emptyList(),
                    bookmarkCount = 0,
                    savedRecording = null,
                    error = null
                )

                // Start Foreground Service so recording survives screen lock / minimize
                val serviceIntent = RecordingService.createStartIntent(
                    context = context,
                    outputPath = currentFilePath,
                    format = format,
                    quality = quality,
                    maxDurationMs = maxDurationMs
                )
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }

                startTimerUpdates()
                startAmplitudeCollection()

                if (maxDurationMs > 0) {
                    startAutoStopTimer(maxDurationMs)
                }
            } catch (e: Exception) {
                android.util.Log.e("OyeSuno", "Failed to start recording", e)
                _events.emit(RecorderEvent.Error("Couldn't start recording. ${e.message ?: ""}"))
            }
        }
    }

    fun pauseRecording() {
        try {
            val pauseIntent = Intent(context, RecordingService::class.java).apply {
                action = RecordingService.ACTION_PAUSE
            }
            context.startService(pauseIntent)
        } catch (e: Exception) {
            recordingEngine.pause()
        }
        timerJob?.cancel()
    }

    fun resumeRecording() {
        try {
            val resumeIntent = Intent(context, RecordingService::class.java).apply {
                action = RecordingService.ACTION_RESUME
            }
            context.startService(resumeIntent)
        } catch (e: Exception) {
            recordingEngine.resume()
        }
        startTimerUpdates()
    }

    fun stopRecording() {
        viewModelScope.launch {
            try {
                timerJob?.cancel()
                amplitudeCollectorJob?.cancel()

                // Tell Foreground Service to stop and save cleanly
                try {
                    val stopIntent = Intent(context, RecordingService::class.java).apply {
                        action = RecordingService.ACTION_STOP
                    }
                    context.startService(stopIntent)
                } catch (e: Exception) {
                    // Fallback to direct engine stop
                    val filePath = recordingEngine.stop()
                    val elapsedMs = _uiState.value.elapsedTimeMs.coerceAtLeast(1000L)
                    val fileSize = fileManager.getFileSize(filePath)
                    val title = FileNameGenerator.generateDefaultName()

                    val recording = Recording(
                        title = title,
                        filePath = filePath,
                        durationMs = elapsedMs,
                        format = _uiState.value.format,
                        sampleRate = _uiState.value.quality.sampleRate,
                        bitrate = _uiState.value.quality.bitrate,
                        channels = 1,
                        triggerType = RecordingTriggerType.MANUAL,
                        fileSize = fileSize
                    )
                    val recId = repository.insertRecording(recording)
                    val waveform = waveformGenerator.generateWaveform(filePath, 100)
                    repository.saveWaveformAmplitudes(recId, waveform)
                    val saved = recording.copy(id = recId)
                    _uiState.value = _uiState.value.copy(
                        recordingState = RecordingState.Idle,
                        savedRecording = saved
                    )
                    _events.emit(RecorderEvent.RecordingSaved(saved))
                }

                _uiState.value = _uiState.value.copy(recordingState = RecordingState.Idle)
            } catch (e: Exception) {
                _events.emit(RecorderEvent.Error("Couldn't stop recording: ${e.message ?: ""}"))
            }
        }
    }

    fun addBookmark(label: String = "") {
        recordingEngine.addBookmark(label)
        val elapsed = _uiState.value.elapsedTimeMs
        collectedBookmarks.add(Pair(elapsed, label))
        _uiState.value = _uiState.value.copy(bookmarkCount = collectedBookmarks.size)
    }

    fun setTimedDuration(durationMs: Long) {
        _uiState.value = _uiState.value.copy(maxDurationMs = durationMs)
    }

    fun setFormat(format: AudioFormat) {
        _uiState.value = _uiState.value.copy(format = format)
    }

    fun setQuality(quality: QualityPreset) {
        _uiState.value = _uiState.value.copy(quality = quality)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSavedRecording() {
        _uiState.value = _uiState.value.copy(savedRecording = null)
    }

    private fun startTimerUpdates() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                val elapsed = recordingEngine.getElapsedTimeMs()
                _uiState.value = _uiState.value.copy(elapsedTimeMs = elapsed)
                delay(100) // Update 10x/sec for smooth timer UI
            }
        }
    }

    private fun startAmplitudeCollection() {
        amplitudeCollectorJob?.cancel()
        amplitudeCollectorJob = viewModelScope.launch {
            recordingEngine.amplitudes.collect { amplitude ->
                collectedAmplitudes.add(amplitude)
                if (collectedAmplitudes.size > 200) {
                    collectedAmplitudes.removeAt(0)
                }
                _uiState.value = _uiState.value.copy(amplitudes = collectedAmplitudes.toList())
            }
        }
    }

    private fun startAutoStopTimer(maxDurationMs: Long) {
        viewModelScope.launch {
            while (isActive) {
                val elapsed = recordingEngine.getElapsedTimeMs()
                if (elapsed >= maxDurationMs) {
                    stopRecording()
                    break
                }
                delay(500)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        amplitudeCollectorJob?.cancel()
    }
}
