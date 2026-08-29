package com.oyesuno.core.engine

import com.oyesuno.core.model.AudioFormat
import com.oyesuno.core.model.QualityPreset
import com.oyesuno.core.model.RecordingState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface RecordingEngine {
    val state: StateFlow<RecordingState>
    val amplitudes: SharedFlow<Float>
    fun start(format: AudioFormat, quality: QualityPreset, outputPath: String)
    fun pause()
    fun resume()
    fun stop(): String // returns file path
    fun addBookmark(label: String = "")
    fun release()
}
