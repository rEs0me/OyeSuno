package com.oyesuno.core.model

sealed class RecordingState {
    object Idle : RecordingState()
    data class Recording(
        val startTimeMs: Long,
        val elapsedMs: Long,
        val isPaused: Boolean
    ) : RecordingState()
    data class Paused(val elapsedMs: Long) : RecordingState()
}
