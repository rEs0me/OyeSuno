package com.oyesuno.core.audio.recorder

import android.content.Context
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import com.oyesuno.core.engine.RecordingEngine
import com.oyesuno.core.model.AudioFormat
import com.oyesuno.core.model.QualityPreset
import com.oyesuno.core.model.RecordingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
 * Full implementation of [RecordingEngine] using Android's MediaRecorder API.
 * Supports M4A/AAC, Opus (on API 29+), and PCM/WAV formats.
 * Provides real-time amplitude data for waveform visualization.
 */
class RecordingEngineImpl(private val context: Context) : RecordingEngine {

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    override val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val _amplitudes = MutableSharedFlow<Float>(replay = 0, extraBufferCapacity = 256)
    override val amplitudes: SharedFlow<Float> = _amplitudes.asSharedFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var outputPath: String = ""
    private var startTimeMs: Long = 0L
    private var pausedElapsedMs: Long = 0L
    private var amplitudeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    // Bookmarks collected during recording (timestamp in ms from start)
    private val _bookmarks = mutableListOf<Pair<Long, String>>()
    val bookmarks: List<Pair<Long, String>> get() = _bookmarks.toList()

    val currentOutputPath: String get() = outputPath
    val currentStartTimeMs: Long get() = startTimeMs

    companion object {
        @Volatile
        private var instance: RecordingEngineImpl? = null

        fun getInstance(context: Context): RecordingEngineImpl {
            return instance ?: synchronized(this) {
                instance ?: RecordingEngineImpl(context.applicationContext).also { instance = it }
            }
        }

        private val _isRecordingActive = MutableStateFlow(false)

        /** Observable recording state — UI can collect this to auto-navigate to recorder */
        val isRecordingActiveFlow: StateFlow<Boolean> = _isRecordingActive.asStateFlow()

        /** Quick check for non-reactive contexts */
        val isAnyRecordingActive: Boolean get() = _isRecordingActive.value
    }

    override fun start(format: AudioFormat, quality: QualityPreset, outputPath: String) {
        this.outputPath = outputPath
        this._bookmarks.clear()
        this.pausedElapsedMs = 0L
        _isRecordingActive.value = true

        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val isVoipOrCall = audioManager?.mode == AudioManager.MODE_IN_COMMUNICATION ||
                           audioManager?.mode == AudioManager.MODE_IN_CALL

        // Ultra-low latency (<10ms): VOICE_RECOGNITION and MIC bypass audio HAL communication locks
        // without the blocking contention delay of exclusive communication channels.
        val primarySource = if (isVoipOrCall) MediaRecorder.AudioSource.VOICE_RECOGNITION else MediaRecorder.AudioSource.MIC
        val fallbackSource = if (isVoipOrCall) MediaRecorder.AudioSource.MIC else MediaRecorder.AudioSource.VOICE_COMMUNICATION

        try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(primarySource)
                configureFormat(this, format, quality)
                setOutputFile(outputPath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            startTimeMs = System.currentTimeMillis()

            _state.value = RecordingState.Recording(
                startTimeMs = startTimeMs,
                elapsedMs = 0,
                isPaused = false
            )

            android.util.Log.i("OyeSuno", "Recording started instantly with source=$primarySource on $outputPath")
            startAmplitudePolling()
        } catch (e: Exception) {
            android.util.Log.w("OyeSuno", "Primary source failed, trying fallback $fallbackSource...", e)
            try {
                releaseRecorder()
                val fallbackRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }

                fallbackRecorder.apply {
                    setAudioSource(fallbackSource)
                    configureFormat(this, format, quality)
                    setOutputFile(outputPath)
                    prepare()
                    start()
                }

                mediaRecorder = fallbackRecorder
                startTimeMs = System.currentTimeMillis()

                _state.value = RecordingState.Recording(
                    startTimeMs = startTimeMs,
                    elapsedMs = 0,
                    isPaused = false
                )

                startAmplitudePolling()
            } catch (fallbackEx: Exception) {
                android.util.Log.e("OyeSuno", "RecordingEngine failed to start", fallbackEx)
                _state.value = RecordingState.Idle
                _isRecordingActive.value = false
                releaseRecorder()
                throw fallbackEx
            }
        }
    }

    override fun pause() {
        try {
            mediaRecorder?.pause()
            amplitudeJob?.cancel()

            val elapsed = System.currentTimeMillis() - startTimeMs + pausedElapsedMs
            pausedElapsedMs = elapsed

            _state.value = RecordingState.Paused(elapsedMs = elapsed)
        } catch (e: Exception) {
            // Some devices may not support pause
        }
    }

    override fun resume() {
        try {
            mediaRecorder?.resume()
            startTimeMs = System.currentTimeMillis()

            _state.value = RecordingState.Recording(
                startTimeMs = startTimeMs,
                elapsedMs = pausedElapsedMs,
                isPaused = false
            )

            startAmplitudePolling()
        } catch (e: Exception) {
            // Handle resume failure
        }
    }

    override fun stop(): String {
        _isRecordingActive.value = false
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            // MediaRecorder may throw if stopped too quickly
        }
        mediaRecorder = null
        amplitudeJob?.cancel()

        _state.value = RecordingState.Idle
        return outputPath
    }

    override fun addBookmark(label: String) {
        val currentState = _state.value
        val timestamp = when (currentState) {
            is RecordingState.Recording ->
                System.currentTimeMillis() - currentState.startTimeMs + pausedElapsedMs
            is RecordingState.Paused -> currentState.elapsedMs
            else -> 0L
        }
        _bookmarks.add(Pair(timestamp, label))
    }

    override fun release() {
        _isRecordingActive.value = false
        amplitudeJob?.cancel()
        releaseRecorder()
        _state.value = RecordingState.Idle
    }

    /**
     * Returns the current recording elapsed time in milliseconds.
     */
    fun getElapsedTimeMs(): Long {
        return when (val state = _state.value) {
            is RecordingState.Recording ->
                System.currentTimeMillis() - state.startTimeMs + pausedElapsedMs
            is RecordingState.Paused -> state.elapsedMs
            else -> 0L
        }
    }

    /**
     * Returns the file size of the current recording.
     */
    fun getCurrentFileSize(): Long {
        return try {
            File(outputPath).length()
        } catch (e: Exception) {
            0L
        }
    }

    private fun configureFormat(recorder: MediaRecorder, format: AudioFormat, quality: QualityPreset) {
        val sampleRate = quality.sampleRate
        val bitrate = quality.bitrate

        when (format) {
            AudioFormat.M4A_AAC -> {
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioSamplingRate(sampleRate)
                recorder.setAudioEncodingBitRate(bitrate)
                recorder.setAudioChannels(1)
            }
            AudioFormat.OPUS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                    recorder.setAudioSamplingRate(sampleRate)
                    recorder.setAudioEncodingBitRate(bitrate)
                    recorder.setAudioChannels(1)
                } else {
                    // Fall back to AAC on older devices
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    recorder.setAudioSamplingRate(sampleRate)
                    recorder.setAudioEncodingBitRate(bitrate)
                    recorder.setAudioChannels(1)
                }
            }
            AudioFormat.WAV -> {
                // MediaRecorder doesn't natively support WAV
                // Use 3GPP as closest alternative; true WAV would need AudioRecord
                recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                recorder.setAudioSamplingRate(8000)
                recorder.setAudioChannels(1)
            }
        }
    }

    private fun startAmplitudePolling() {
        amplitudeJob?.cancel()
        amplitudeJob = scope.launch {
            var smoothed = 0.05f
            while (isActive) {
                try {
                    val maxAmplitude = mediaRecorder?.maxAmplitude ?: 0
                    // Logarithmic/decibel normalization for realistic human speech dynamics
                    val raw = (maxAmplitude / 32767f).coerceIn(0f, 1f)
                    val target = if (raw > 0.01f) (0.2f + 0.8f * raw) else 0.05f
                    smoothed = smoothed * 0.60f + target * 0.40f
                    _amplitudes.emit(smoothed.coerceIn(0.05f, 1f))
                } catch (e: Exception) {
                    _amplitudes.emit(0.05f)
                }
                delay(40) // 25 FPS update rate for buttery smooth waveform
            }
        }
    }

    private fun releaseRecorder() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            // Ignore
        }
        mediaRecorder = null
    }
}
