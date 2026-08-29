package com.oyesuno.core.model

enum class AudioSourceOption(
    val label: String,
    val description: String,
    val sourceId: Int
) {
    VOICE_RECOGNITION(
        "Voice & Speech Optimized",
        "Directional beamforming with noise reduction for clear vocals",
        6 // MediaRecorder.AudioSource.VOICE_RECOGNITION
    ),
    DEFAULT_MIC(
        "Standard Microphone",
        "Default balanced studio microphone input",
        1 // MediaRecorder.AudioSource.MIC
    ),
    VOICE_COMMUNICATION(
        "Calls & Communication",
        "Hardware AEC & AGC tuning for VoIP and phone calls",
        7 // MediaRecorder.AudioSource.VOICE_COMMUNICATION
    ),
    CAMCORDER(
        "Ambient / Wide Field",
        "Wide stereo field capture ideal for rooms and environments",
        5 // MediaRecorder.AudioSource.CAMCORDER
    ),
    UNPROCESSED(
        "Raw Unprocessed Audio",
        "Pure uncompressed microphone signal without system filters",
        9 // MediaRecorder.AudioSource.UNPROCESSED
    )
}
