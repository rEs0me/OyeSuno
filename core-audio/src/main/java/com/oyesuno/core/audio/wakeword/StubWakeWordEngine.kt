package com.oyesuno.core.audio.wakeword

import com.oyesuno.core.engine.WakeWordEngine
import com.oyesuno.core.engine.WakeWordListener
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stub implementation of [WakeWordEngine].
 * Will be replaced in Phase 4 with Sherpa-ONNX integration.
 */
class StubWakeWordEngine : WakeWordEngine {

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _onWakeWordDetected = MutableSharedFlow<Unit>()
    override val onWakeWordDetected: SharedFlow<Unit> = _onWakeWordDetected.asSharedFlow()

    private var listener: WakeWordListener? = null

    override fun start() {
        // TODO: Phase 4 - Start Sherpa-ONNX keyword spotting
        // For now, this is a no-op stub
    }

    override fun stop() {
        // TODO: Phase 4 - Stop Sherpa-ONNX
        _isListening.value = false
    }

    override fun setSensitivity(sensitivity: Float) {
        // TODO: Phase 4 - Configure Sherpa-ONNX sensitivity
    }

    override fun setListener(listener: WakeWordListener?) {
        this.listener = listener
    }

    override fun release() {
        // TODO: Phase 4 - Release Sherpa-ONNX resources
        _isListening.value = false
        this.listener = null
    }
}
