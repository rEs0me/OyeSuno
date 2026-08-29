package com.oyesuno.core.model

data class Recording(
    val id: Long = 0,
    val title: String,
    val filePath: String,
    val durationMs: Long = 0,
    val format: AudioFormat = AudioFormat.M4A_AAC,
    val sampleRate: Int = 44100,
    val bitrate: Int = 128000,
    val channels: Int = 1,
    val categoryId: Long? = null,
    val categoryName: String? = null,
    val isFavorite: Boolean = false,
    val triggerType: RecordingTriggerType = RecordingTriggerType.MANUAL,
    val fileSize: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
