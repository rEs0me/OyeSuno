package com.oyesuno.feature.player

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oyesuno.core.database.OyeSunoDatabase
import com.oyesuno.core.database.repository.RecordingRepository
import com.oyesuno.core.engine.PlaybackState
import com.oyesuno.core.model.Bookmark
import com.oyesuno.core.model.PlaybackSpeed
import com.oyesuno.core.model.Recording
import com.oyesuno.feature.player.service.PlaybackService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val audioPlayer = PlaybackService.getOrCreatePlayer(context)
    private val database = OyeSunoDatabase.getInstance(context)
    private val repository = RecordingRepository(database)

    data class PlayerUiState(
        val recording: Recording? = null,
        val playbackState: PlaybackState = PlaybackState.Idle,
        val currentPositionMs: Long = 0L,
        val durationMs: Long = 0L,
        val speed: PlaybackSpeed = PlaybackSpeed.X1_0,
        val bookmarks: List<Bookmark> = emptyList(),
        val waveformAmplitudes: List<Float> = emptyList(),
        val isFavorite: Boolean = false,
        val audioBoostGainMb: Int = 0,
        val speakerSegments: List<com.oyesuno.core.audio.speaker.SpeakerSegment> = emptyList(),
        val isDetectingSpeakers: Boolean = false,
        val isSpeakerMixerActive: Boolean = false,
        val speaker1Volume: Float = 1.0f,
        val speaker2Volume: Float = 1.0f,
        val speaker1Muted: Boolean = false,
        val speaker2Muted: Boolean = false,
        val currentActiveSpeakerId: Int = 1,
        val isLoading: Boolean = true
    )

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            audioPlayer.playbackState.collect { state ->
                _uiState.value = _uiState.value.copy(playbackState = state)
            }
        }
        viewModelScope.launch {
            audioPlayer.currentPositionMs.collect { pos ->
                val state = _uiState.value
                val segments = state.speakerSegments
                if (segments.isNotEmpty() && state.isSpeakerMixerActive) {
                    val activeSegment = segments.firstOrNull { pos in it.startMs..it.endMs }
                    val activeSpeakerId = activeSegment?.speakerId ?: 1
                    if (activeSpeakerId != state.currentActiveSpeakerId) {
                        _uiState.value = _uiState.value.copy(
                            currentPositionMs = pos,
                            currentActiveSpeakerId = activeSpeakerId
                        )
                        applyPerSpeakerDynamicGain(activeSpeakerId)
                    } else {
                        _uiState.value = _uiState.value.copy(currentPositionMs = pos)
                    }
                } else {
                    _uiState.value = _uiState.value.copy(currentPositionMs = pos)
                }
            }
        }
        viewModelScope.launch {
            audioPlayer.durationMs.collect { dur ->
                _uiState.value = _uiState.value.copy(durationMs = dur)
            }
        }
    }

    fun loadRecording(recordingId: Long) {
        viewModelScope.launch {
            val recording = repository.getRecordingById(recordingId) ?: return@launch
            val bookmarks = repository.getBookmarksForRecording(recordingId).first()
            val waveform = repository.getWaveformAmplitudes(recordingId) ?: emptyList()

            _uiState.value = _uiState.value.copy(
                recording = recording,
                bookmarks = bookmarks,
                waveformAmplitudes = waveform,
                isFavorite = recording.isFavorite,
                isLoading = false
            )

            audioPlayer.load(recording.filePath)
        }
    }

    fun play() {
        val recording = _uiState.value.recording
        if (recording != null) {
            startPlaybackService(recording.title, recording.filePath)
        } else {
            audioPlayer.play()
        }
    }

    fun pause() {
        audioPlayer.pause()
    }

    private fun startPlaybackService(title: String, filePath: String) {
        try {
            val serviceIntent = Intent(context, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_START
                putExtra(PlaybackService.EXTRA_TITLE, title)
                putExtra(PlaybackService.EXTRA_FILE_PATH, filePath)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            audioPlayer.play()
        }
    }

    fun togglePlayPause() {
        val state = _uiState.value.playbackState
        val currentPos = _uiState.value.currentPositionMs
        val duration = _uiState.value.durationMs

        if (state is PlaybackState.Playing) {
            pause()
        } else {
            if (state is PlaybackState.Completed || (duration > 0 && currentPos >= duration - 500)) {
                seekTo(0L)
            }
            play()
        }
    }

    fun seekTo(positionMs: Long) = audioPlayer.seekTo(positionMs)

    fun seekForward(ms: Long = 10_000) {
        val newPos = (_uiState.value.currentPositionMs + ms)
            .coerceAtMost(_uiState.value.durationMs)
        seekTo(newPos)
    }

    fun seekBackward(ms: Long = 10_000) {
        val newPos = (_uiState.value.currentPositionMs - ms).coerceAtLeast(0)
        seekTo(newPos)
    }

    fun setSpeed(speed: PlaybackSpeed) {
        audioPlayer.setSpeed(speed.speed)
        _uiState.value = _uiState.value.copy(speed = speed)
    }

    fun cycleSpeed() {
        val speeds = PlaybackSpeed.entries
        val currentIndex = speeds.indexOf(_uiState.value.speed)
        val nextSpeed = speeds[(currentIndex + 1) % speeds.size]
        setSpeed(nextSpeed)
    }

    fun setAudioBoost(gainMb: Int) {
        audioPlayer.setAudioBoost(gainMb)
        _uiState.value = _uiState.value.copy(audioBoostGainMb = gainMb)
    }

    fun cycleAudioBoost() {
        val nextBoost = when (_uiState.value.audioBoostGainMb) {
            0 -> 600
            600 -> 1200
            1200 -> 2000
            else -> 0
        }
        setAudioBoost(nextBoost)
    }

    fun setSpeaker1Volume(volume: Float) {
        _uiState.value = _uiState.value.copy(speaker1Volume = volume, isSpeakerMixerActive = true)
        applyPerSpeakerDynamicGain(_uiState.value.currentActiveSpeakerId)
    }

    fun setSpeaker2Volume(volume: Float) {
        _uiState.value = _uiState.value.copy(speaker2Volume = volume, isSpeakerMixerActive = true)
        applyPerSpeakerDynamicGain(_uiState.value.currentActiveSpeakerId)
    }

    fun toggleSpeaker1Mute() {
        val newMute = !_uiState.value.speaker1Muted
        _uiState.value = _uiState.value.copy(speaker1Muted = newMute, isSpeakerMixerActive = true)
        applyPerSpeakerDynamicGain(_uiState.value.currentActiveSpeakerId)
    }

    fun toggleSpeaker2Mute() {
        val newMute = !_uiState.value.speaker2Muted
        _uiState.value = _uiState.value.copy(speaker2Muted = newMute, isSpeakerMixerActive = true)
        applyPerSpeakerDynamicGain(_uiState.value.currentActiveSpeakerId)
    }

    private fun applyPerSpeakerDynamicGain(speakerId: Int) {
        val state = _uiState.value
        if (!state.isSpeakerMixerActive || state.speakerSegments.isEmpty()) return
        val isMuted = if (speakerId == 1) state.speaker1Muted else state.speaker2Muted
        val volume = if (speakerId == 1) state.speaker1Volume else state.speaker2Volume

        if (isMuted) {
            audioPlayer.setAudioBoost(0)
        } else {
            val gainMb = ((volume - 1.0f) * 1500).toInt().coerceIn(0, 3000)
            audioPlayer.setAudioBoost(gainMb)
        }
    }

    fun seekToBookmark(bookmark: Bookmark) {
        seekTo(bookmark.timestampMs)
    }

    fun seekToNextBookmark() {
        val currentPos = _uiState.value.currentPositionMs
        val next = _uiState.value.bookmarks.firstOrNull { it.timestampMs > currentPos + 500 }
        if (next != null) seekTo(next.timestampMs)
    }

    fun seekToPreviousBookmark() {
        val currentPos = _uiState.value.currentPositionMs
        val prev = _uiState.value.bookmarks.lastOrNull { it.timestampMs < currentPos - 1000 }
        if (prev != null) seekTo(prev.timestampMs)
    }

    fun toggleFavorite() {
        val recordingId = _uiState.value.recording?.id ?: return
        viewModelScope.launch {
            repository.toggleFavorite(recordingId)
            _uiState.value = _uiState.value.copy(isFavorite = !_uiState.value.isFavorite)
        }
    }

    fun deleteRecording() {
        val recordingId = _uiState.value.recording?.id ?: return
        viewModelScope.launch {
            repository.deleteRecordingById(recordingId)
        }
    }

    private val speakerDetector = com.oyesuno.core.audio.speaker.SpeakerDetector()

    fun detectSpeakers() {
        val recording = _uiState.value.recording ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDetectingSpeakers = true)
            val segments = speakerDetector.detectSpeakers(recording.filePath, _uiState.value.durationMs)
            _uiState.value = _uiState.value.copy(
                speakerSegments = segments,
                isDetectingSpeakers = false,
                isSpeakerMixerActive = segments.isNotEmpty()
            )
        }
    }
}
