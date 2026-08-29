package com.oyesuno.core.audio.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Universal real-time Call & VoIP Detector:
 * 1. Cellular Phone Calls: via TelephonyCallback / PhoneStateListener.
 * 2. WhatsApp, Telegram, Instagram, Zoom, Meet VoIP Calls:
 *    - Instant AudioPlaybackCallback (USAGE_VOICE_COMMUNICATION)
 *    - Instant AudioRecordingCallback (VOICE_COMMUNICATION client)
 *    - AudioManager.OnModeChangedListener (MODE_IN_COMMUNICATION)
 */
class VoipCallDetector(
    private val context: Context,
    private val onCallStarted: (isVoip: Boolean) -> Unit,
    private val onCallEnded: () -> Unit
) {

    private val TAG = "VoipCallDetector"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var isCallActive = false
    private var isVoipCommunicationActive = false
    private var isTelephonyCallActive = false

    private val modeListener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        AudioManager.OnModeChangedListener { mode ->
            handleAudioMode(mode)
        }
    } else null

    private val playbackCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
                super.onPlaybackConfigChanged(configs)
                val hasVoipPlayback = configs.any { config ->
                    val usage = config.audioAttributes.usage
                    usage == AudioAttributes.USAGE_VOICE_COMMUNICATION ||
                    usage == AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING
                }
                handleVoipStateChange(hasVoipPlayback, "PlaybackCallback")
            }
        }
    } else null

    private val recordingCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
                super.onRecordingConfigChanged(configs)
                // If another app (e.g. WhatsApp) is recording with voice communication
                val hasVoipRecording = configs.any { config ->
                    config.clientAudioSource == android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION
                }
                if (hasVoipRecording) {
                    handleVoipStateChange(true, "RecordingCallback")
                }
            }
        }
    } else null

    private var telephonyCallback: Any? = null

    fun startListening() {
        // 1. Audio Mode change listener
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && modeListener != null) {
            try {
                audioManager.addOnModeChangedListener(context.mainExecutor, modeListener)
            } catch (e: Exception) {
                Log.w(TAG, "Could not add audio mode listener: ${e.message}")
            }
        }

        // 2. Playback callback (captures WhatsApp/VoIP speaker activity instantly)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && playbackCallback != null) {
            try {
                audioManager.registerAudioPlaybackCallback(playbackCallback, mainHandler)
            } catch (e: Exception) {
                Log.w(TAG, "Could not register playback callback: ${e.message}")
            }
        }

        // 3. Recording callback (captures VoIP mic capture instantly)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && recordingCallback != null) {
            try {
                audioManager.registerAudioRecordingCallback(recordingCallback, mainHandler)
            } catch (e: Exception) {
                Log.w(TAG, "Could not register recording callback: ${e.message}")
            }
        }

        // Check initial state
        handleAudioMode(audioManager.mode)

        // 4. Cellular Phone Call Listener
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleTelephonyState(state)
                    }
                }
                telephonyCallback = callback
                telephonyManager?.registerTelephonyCallback(context.mainExecutor, callback)
            } else {
                @Suppress("DEPRECATION")
                val listener = object : android.telephony.PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleTelephonyState(state)
                    }
                }
                telephonyCallback = listener
                @Suppress("DEPRECATION")
                telephonyManager?.listen(listener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not register telephony listener: ${e.message}")
        }

        // 5. Active background watchdog (ensures MIUI / HyperOS never misses WhatsApp call events)
        startWatchdog()
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private var watchdogJob: Job? = null

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                delay(1200L)
                val currentMode = audioManager.mode
                if (currentMode == AudioManager.MODE_IN_COMMUNICATION) {
                    if (!isCallActive) {
                        Log.i(TAG, "VoIP Watchdog: Active WhatsApp/VoIP call detected!")
                        mainHandler.post { handleAudioMode(currentMode) }
                    }
                } else if (currentMode == AudioManager.MODE_NORMAL && isVoipCommunicationActive && !isTelephonyCallActive) {
                    Log.i(TAG, "VoIP Watchdog: WhatsApp/VoIP call ended.")
                    mainHandler.post { handleAudioMode(currentMode) }
                }
            }
        }
    }

    fun stopListening() {
        watchdogJob?.cancel()
        watchdogJob = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && modeListener != null) {
            try { audioManager.removeOnModeChangedListener(modeListener) } catch (e: Exception) { /* ignore */ }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && playbackCallback != null) {
            try { audioManager.unregisterAudioPlaybackCallback(playbackCallback) } catch (e: Exception) { /* ignore */ }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && recordingCallback != null) {
            try { audioManager.unregisterAudioRecordingCallback(recordingCallback) } catch (e: Exception) { /* ignore */ }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback is TelephonyCallback) {
                telephonyManager?.unregisterTelephonyCallback(telephonyCallback as TelephonyCallback)
            } else if (telephonyCallback is android.telephony.PhoneStateListener) {
                @Suppress("DEPRECATION")
                telephonyManager?.listen(telephonyCallback as android.telephony.PhoneStateListener, android.telephony.PhoneStateListener.LISTEN_NONE)
            }
        } catch (e: Exception) { /* ignore */ }
    }

    private fun handleVoipStateChange(active: Boolean, source: String) {
        if (active && !isCallActive && !isTelephonyCallActive) {
            isVoipCommunicationActive = true
            isCallActive = true
            Log.i(TAG, "VoIP Call connected via $source! Auto-starting recording...")
            onCallStarted(true)
        } else if (!active && isVoipCommunicationActive && !isTelephonyCallActive) {
            // Confirm mode is also normal before stopping
            if (audioManager.mode == AudioManager.MODE_NORMAL) {
                isVoipCommunicationActive = false
                isCallActive = false
                Log.i(TAG, "VoIP Call ended ($source). Stopping recording...")
                onCallEnded()
            }
        }
    }

    private fun handleTelephonyState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (!isCallActive) {
                    isTelephonyCallActive = true
                    isCallActive = true
                    Log.i(TAG, "Cellular Phone call answered/connected.")
                    onCallStarted(false)
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (isTelephonyCallActive) {
                    isTelephonyCallActive = false
                    isCallActive = false
                    Log.i(TAG, "Cellular Phone call ended.")
                    onCallEnded()
                }
            }
        }
    }

    private fun handleAudioMode(mode: Int) {
        when (mode) {
            AudioManager.MODE_IN_COMMUNICATION -> {
                if (!isCallActive) {
                    isVoipCommunicationActive = true
                    isCallActive = true
                    Log.i(TAG, "WhatsApp / VoIP Communication Call active via AudioManager mode.")
                    onCallStarted(true)
                }
            }
            AudioManager.MODE_IN_CALL -> {
                if (!isCallActive) {
                    isTelephonyCallActive = true
                    isCallActive = true
                    Log.i(TAG, "Cellular Phone Call active via AudioManager mode.")
                    onCallStarted(false)
                }
            }
            AudioManager.MODE_NORMAL -> {
                if (isCallActive && !isTelephonyCallActive) {
                    isVoipCommunicationActive = false
                    isCallActive = false
                    Log.i(TAG, "VoIP Call ended via AudioManager MODE_NORMAL.")
                    onCallEnded()
                }
            }
        }
    }
}
