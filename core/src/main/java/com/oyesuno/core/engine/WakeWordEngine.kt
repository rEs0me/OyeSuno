package com.oyesuno.core.engine

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface WakeWordEngine {
    val isListening: StateFlow<Boolean>
    val onWakeWordDetected: SharedFlow<Unit>
    fun start()
    fun stop()
    fun setSensitivity(sensitivity: Float)
    fun setListener(listener: WakeWordListener?)
    fun release()
}

fun interface WakeWordListener {
    fun onWakeWordDetected()
}
