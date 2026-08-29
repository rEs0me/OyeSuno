package com.oyesuno.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a recording.
 */
@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val filePath: String,
    val durationMs: Long = 0,
    val format: String = "M4A_AAC",
    val sampleRate: Int = 44100,
    val bitrate: Int = 128000,
    val channels: Int = 1,
    val categoryId: Long? = null,
    val isFavorite: Boolean = false,
    val triggerType: String = "MANUAL",
    val fileSize: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
