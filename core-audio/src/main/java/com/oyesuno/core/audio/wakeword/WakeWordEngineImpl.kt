package com.oyesuno.core.audio.wakeword

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.oyesuno.core.engine.WakeWordEngine
import com.oyesuno.core.engine.WakeWordListener
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
import java.util.Locale

/**
 * Robust Neural Wake Word Engine with Full Custom Keyword Support.
 *
 * - Continuous & Re-arming: Listens repeatedly after every recording completes.
 * - Multi-Language & Custom Keyword Support: Supports custom phrases (e.g. "suno", "record",
 *   "jarvis", "meeting", "notes", etc.) with fuzzy matching and word-boundary isolation.
 * - Muted System Feedback: Automatically suppresses speech chimes so recognition stays silent.
 * - Zero Stalls: Freshly cycles SpeechRecognizer instances on error to prevent ERROR_BUSY / ERROR_CLIENT lockups.
 */
class WakeWordEngineImpl(private val context: Context) : WakeWordEngine {

    private val TAG = "WakeWordEngine"

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _onWakeWordDetected = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 16)
    override val onWakeWordDetected: SharedFlow<Unit> = _onWakeWordDetected.asSharedFlow()

    private var listener: WakeWordListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default)

    private var isUserEnabled = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var restartJob: Job? = null
    private var lastTriggerTimestamp = 0L

    private var customKeyword: String = "suno"
    private var customKeywordsList: List<String> = listOf("suno", "oye suno", "सुनो", "suno na", "oye", "jarvis", "record")

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    init {
        // Automatically re-arm wake word detection whenever a recording ends
        scope.launch {
            com.oyesuno.core.audio.recorder.RecordingEngineImpl.isRecordingActiveFlow.collect { isRecActive ->
                if (!isRecActive && isUserEnabled) {
                    Log.i(TAG, "Recording stopped: automatically re-arming wake word listening...")
                    delay(600L)
                    if (isUserEnabled && !com.oyesuno.core.audio.recorder.RecordingEngineImpl.isAnyRecordingActive) {
                        startContinuousRecognition()
                    }
                } else if (isRecActive) {
                    destroyRecognizer()
                }
            }
        }
    }

    fun setCustomKeywords(customWords: List<String>) {
        if (customWords.isNotEmpty()) {
            val clean = customWords.first().lowercase().trim()
            customKeyword = clean
            val list = mutableListOf(clean)
            if (clean == "suno" || clean == "oye suno") {
                list.addAll(listOf("suno", "oye suno", "सुनo", "सुनो", "suno na", "oye"))
            } else if (clean == "record") {
                list.addAll(listOf("record", "start record", "recording", "recorder"))
            }
            customKeywordsList = list.distinct()
            Log.i(TAG, "Active wake keywords updated to: $customKeywordsList")
        }
    }

    override fun start() {
        isUserEnabled = true
        _isListening.value = true
        startContinuousRecognition()
    }

    override fun stop() {
        isUserEnabled = false
        _isListening.value = false
        restartJob?.cancel()
        restartJob = null
        destroyRecognizer()
        unmuteSystemStreams()
    }

    override fun setSensitivity(sensitivity: Float) {}

    override fun setListener(listener: WakeWordListener?) {
        this.listener = listener
    }

    override fun release() {
        stop()
        this.listener = null
    }

    private fun startContinuousRecognition() {
        mainHandler.post {
            if (!isUserEnabled) return@post
            if (com.oyesuno.core.audio.recorder.RecordingEngineImpl.isAnyRecordingActive) {
                scheduleRestart(1000L)
                return@post
            }

            try {
                // 1. Mute system and notification streams before starting
                muteSystemStreams()

                // 2. Cleanly recreate instance to avoid ERROR_BUSY / ERROR_CLIENT stalls
                destroyRecognizer()

                speechRecognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context.applicationContext)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
                }.apply {
                    setRecognitionListener(createRecognitionListener())
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
                }

                speechRecognizer?.startListening(intent)
                Log.d(TAG, "Continuous speech recognizer active for keywords: $customKeywordsList")
            } catch (e: Exception) {
                Log.w(TAG, "SpeechRecognizer start exception: ${e.message}")
                scheduleRestart(800L)
            }
        }
    }

    private fun createRecognitionListener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            if (isUserEnabled && !com.oyesuno.core.audio.recorder.RecordingEngineImpl.isAnyRecordingActive) {
                scheduleRestart(150L)
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val matched = checkSpeechMatches(matches)
            if (!matched && isUserEnabled && !com.oyesuno.core.audio.recorder.RecordingEngineImpl.isAnyRecordingActive) {
                scheduleRestart(100L)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            checkSpeechMatches(matches)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun checkSpeechMatches(matches: ArrayList<String>?): Boolean {
        if (matches.isNullOrEmpty()) return false
        val cleanCustom = customKeyword.lowercase().trim()

        for (candidate in matches) {
            val spoken = candidate.lowercase().trim()
            Log.d(TAG, "Recognized spoken text: \"$spoken\"")

            // 1. Check primary keywords list
            for (keyword in customKeywordsList) {
                val target = keyword.lowercase().trim()
                val regex = if (target.contains(" ")) {
                    Regex(Regex.escape(target), RegexOption.IGNORE_CASE)
                } else {
                    Regex("\\b${Regex.escape(target)}\\b", RegexOption.IGNORE_CASE)
                }

                if (regex.containsMatchIn(spoken) || spoken.contains(target)) {
                    Log.i(TAG, "Exact Wake Word Matched: \"$target\" in spoken speech: \"$spoken\"!")
                    triggerDetection()
                    return true
                }
            }

            // 2. Check custom user-defined keyword directly
            if (cleanCustom.isNotBlank()) {
                if (spoken.contains(cleanCustom) || cleanCustom.contains(spoken)) {
                    Log.i(TAG, "Custom Wake Word Matched: \"$cleanCustom\" in spoken speech: \"$spoken\"!")
                    triggerDetection()
                    return true
                }
            }
        }
        return false
    }

    private fun scheduleRestart(delayMs: Long) {
        if (!isUserEnabled) return
        restartJob?.cancel()
        restartJob = scope.launch(Dispatchers.Main) {
            delay(delayMs)
            if (isUserEnabled && !com.oyesuno.core.audio.recorder.RecordingEngineImpl.isAnyRecordingActive) {
                startContinuousRecognition()
            }
        }
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) { /* ignore */ }
        speechRecognizer = null
    }

    private fun muteSystemStreams() {
        try {
            audioManager?.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
            audioManager?.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
            audioManager?.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)
            audioManager?.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_MUTE, 0)
        } catch (e: Exception) { /* ignore */ }
    }

    private fun unmuteSystemStreams() {
        try {
            audioManager?.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
            audioManager?.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
            audioManager?.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0)
            audioManager?.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_UNMUTE, 0)
        } catch (e: Exception) { /* ignore */ }
    }

    private fun triggerDetection() {
        if (com.oyesuno.core.audio.player.AudioPlayerImpl.isAnyAudioPlaying) {
            Log.d(TAG, "In-app audio is playing: suppressing wake word.")
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastTriggerTimestamp < 3500) {
            return
        }
        lastTriggerTimestamp = now

        destroyRecognizer()
        unmuteSystemStreams()

        // Haptic feedback
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(80)
            }
        } catch (e: Exception) { /* ignore */ }

        Log.i(TAG, "Wake word MATCHED! Starting recording session instantly...")
        scope.launch {
            _onWakeWordDetected.emit(Unit)
        }
        mainHandler.post {
            listener?.onWakeWordDetected()
        }
    }
}
