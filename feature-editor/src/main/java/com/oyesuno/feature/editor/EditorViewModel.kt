package com.oyesuno.feature.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oyesuno.core.audio.player.AudioPlayerImpl
import com.oyesuno.core.database.OyeSunoDatabase
import com.oyesuno.core.database.repository.RecordingRepository
import com.oyesuno.core.engine.PlaybackState
import com.oyesuno.core.model.Recording
import com.oyesuno.core.util.FileNameGenerator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val database = OyeSunoDatabase.getInstance(context)
    private val repository = RecordingRepository(database)
    private val audioPlayer = AudioPlayerImpl(context)

    data class EditorUiState(
        val recording: Recording? = null,
        val waveformAmplitudes: List<Float> = emptyList(),
        val durationMs: Long = 0L,
        val selectionStartMs: Long = 0L,
        val selectionEndMs: Long = 0L,
        val currentPositionMs: Long = 0L,
        val playbackState: PlaybackState = PlaybackState.Idle,
        val isLoading: Boolean = true,
        val clipCount: Int = 0
    )

    sealed class EditorEvent {
        data class TrimSaved(val recording: Recording) : EditorEvent()
        data class ClipCreated(val recording: Recording) : EditorEvent()
        data class Error(val message: String) : EditorEvent()
    }

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<EditorEvent>()
    val events: SharedFlow<EditorEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            audioPlayer.currentPositionMs.collect { pos ->
                _uiState.value = _uiState.value.copy(currentPositionMs = pos)
            }
        }
        viewModelScope.launch {
            audioPlayer.playbackState.collect { state ->
                _uiState.value = _uiState.value.copy(playbackState = state)
            }
        }
    }

    fun loadRecording(recordingId: Long) {
        viewModelScope.launch {
            val recording = repository.getRecordingById(recordingId) ?: return@launch
            val waveform = repository.getWaveformAmplitudes(recordingId) ?: emptyList()

            _uiState.value = _uiState.value.copy(
                recording = recording,
                waveformAmplitudes = waveform,
                durationMs = recording.durationMs,
                selectionStartMs = 0,
                selectionEndMs = recording.durationMs,
                isLoading = false
            )

            audioPlayer.load(recording.filePath)
        }
    }

    fun setSelectionStart(progress: Float) {
        val ms = (progress * _uiState.value.durationMs).toLong()
        _uiState.value = _uiState.value.copy(
            selectionStartMs = ms.coerceAtMost(_uiState.value.selectionEndMs - 1000)
        )
    }

    fun setSelectionEnd(progress: Float) {
        val ms = (progress * _uiState.value.durationMs).toLong()
        _uiState.value = _uiState.value.copy(
            selectionEndMs = ms.coerceAtLeast(_uiState.value.selectionStartMs + 1000)
        )
    }

    private var previewJob: kotlinx.coroutines.Job? = null

    fun previewSelection() {
        val startMs = _uiState.value.selectionStartMs
        val endMs = _uiState.value.selectionEndMs

        if (endMs <= startMs) return

        previewJob?.cancel()
        audioPlayer.seekTo(startMs)
        audioPlayer.play()

        previewJob = viewModelScope.launch {
            delay(100)
            while (isActive) {
                val currentPos = audioPlayer.currentPositionMs.value
                if (currentPos >= endMs || (currentPos < startMs - 500 && currentPos > 0)) {
                    audioPlayer.pause()
                    audioPlayer.seekTo(startMs)
                    break
                }
                delay(30)
            }
        }
    }

    fun pause() {
        previewJob?.cancel()
        audioPlayer.pause()
    }

    fun trim() {
        viewModelScope.launch {
            val recording = _uiState.value.recording ?: return@launch
            val startMs = _uiState.value.selectionStartMs
            val endMs = _uiState.value.selectionEndMs
            val trimmedDuration = (endMs - startMs).coerceAtLeast(100)

            // Generate output file for trimmed audio
            val file = java.io.File(recording.filePath)
            val trimmedFile = java.io.File(file.parentFile, "trimmed_${System.currentTimeMillis()}_${file.name}")

            val success = com.oyesuno.core.audio.trimmer.AudioTrimmer.trimAudio(
                sourcePath = recording.filePath,
                outputPath = trimmedFile.absolutePath,
                startMs = startMs,
                endMs = endMs
            )

            val finalPath = if (success) trimmedFile.absolutePath else recording.filePath

            val updated = recording.copy(
                filePath = finalPath,
                durationMs = trimmedDuration,
                fileSize = trimmedFile.length(),
                updatedAt = System.currentTimeMillis()
            )
            repository.updateRecording(updated)

            // Regenerate waveform
            val waveform = com.oyesuno.core.audio.waveform.WaveformGenerator().generateWaveform(finalPath, 150)
            repository.saveWaveformAmplitudes(recording.id, waveform)

            _events.emit(EditorEvent.TrimSaved(updated))
        }
    }

    fun createClip() {
        viewModelScope.launch {
            val recording = _uiState.value.recording ?: return@launch
            val clipNumber = _uiState.value.clipCount + 1
            val startMs = _uiState.value.selectionStartMs
            val endMs = _uiState.value.selectionEndMs
            val clipDuration = (endMs - startMs).coerceAtLeast(100)

            val file = java.io.File(recording.filePath)
            val clipFile = java.io.File(file.parentFile, "clip_${clipNumber}_${System.currentTimeMillis()}_${file.name}")

            val success = com.oyesuno.core.audio.trimmer.AudioTrimmer.trimAudio(
                sourcePath = recording.filePath,
                outputPath = clipFile.absolutePath,
                startMs = startMs,
                endMs = endMs
            )

            val finalPath = if (success) clipFile.absolutePath else recording.filePath

            val clip = Recording(
                title = FileNameGenerator.generateClipName(recording.title, clipNumber),
                filePath = finalPath,
                durationMs = clipDuration,
                format = recording.format,
                sampleRate = recording.sampleRate,
                bitrate = recording.bitrate,
                channels = recording.channels,
                categoryId = recording.categoryId,
                fileSize = clipFile.length()
            )

            val clipId = repository.insertRecording(clip)
            val waveform = com.oyesuno.core.audio.waveform.WaveformGenerator().generateWaveform(finalPath, 150)
            repository.saveWaveformAmplitudes(clipId, waveform)

            _uiState.value = _uiState.value.copy(clipCount = clipNumber)
            _events.emit(EditorEvent.ClipCreated(clip.copy(id = clipId)))
        }
    }

    override fun onCleared() {
        super.onCleared()
        previewJob?.cancel()
        audioPlayer.release()
    }
}
