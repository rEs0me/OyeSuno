package com.oyesuno.core.model

enum class AudioFormat(val extension: String, val mimeType: String) {
    M4A_AAC("m4a", "audio/mp4"),
    OPUS("opus", "audio/ogg"),
    WAV("wav", "audio/wav")
}
