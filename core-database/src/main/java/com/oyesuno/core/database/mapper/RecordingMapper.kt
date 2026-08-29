package com.oyesuno.core.database.mapper

import com.oyesuno.core.database.entity.RecordingEntity
import com.oyesuno.core.model.AudioFormat
import com.oyesuno.core.model.Recording
import com.oyesuno.core.model.RecordingTriggerType

/**
 * Maps between RecordingEntity (database) and Recording (domain).
 */
fun RecordingEntity.toDomain(): Recording {
    return Recording(
        id = id,
        title = title,
        filePath = filePath,
        durationMs = durationMs,
        format = try { AudioFormat.valueOf(format) } catch (e: Exception) { AudioFormat.M4A_AAC },
        sampleRate = sampleRate,
        bitrate = bitrate,
        channels = channels,
        categoryId = categoryId,
        isFavorite = isFavorite,
        triggerType = try { RecordingTriggerType.valueOf(triggerType) } catch (e: Exception) { RecordingTriggerType.MANUAL },
        fileSize = fileSize,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun Recording.toEntity(): RecordingEntity {
    return RecordingEntity(
        id = id,
        title = title,
        filePath = filePath,
        durationMs = durationMs,
        format = format.name,
        sampleRate = sampleRate,
        bitrate = bitrate,
        channels = channels,
        categoryId = categoryId,
        isFavorite = isFavorite,
        triggerType = triggerType.name,
        fileSize = fileSize,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
