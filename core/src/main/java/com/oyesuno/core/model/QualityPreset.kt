package com.oyesuno.core.model

enum class QualityPreset(
    val sampleRate: Int,
    val bitrate: Int,
    val label: String,
    val description: String
) {
    STUDIO_ULTRA(48000, 320000, "Studio Ultra", "48 kHz @ 320 kbps · Lossless Master"),
    VOCAL_BOOST(48000, 256000, "Voice Clarity Boost", "48 kHz @ 256 kbps · Enhanced speech & distant voices"),
    HIGH(48000, 192000, "High Fidelity", "48 kHz @ 192 kbps · Rich studio vocal"),
    STANDARD(44100, 128000, "Standard", "44.1 kHz @ 128 kbps · Everyday voice"),
    CONFERENCE(32000, 96000, "Lecture & Meeting", "32 kHz @ 96 kbps · Optimized for long meetings"),
    LOW(22050, 64000, "Compact Memo", "22 kHz @ 64 kbps · Minimal storage"),
    CUSTOM(48000, 256000, "Custom Voice", "48 kHz @ 256 kbps · Dynamic profile")
}
