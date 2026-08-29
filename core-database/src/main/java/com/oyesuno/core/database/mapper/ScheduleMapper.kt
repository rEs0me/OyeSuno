package com.oyesuno.core.database.mapper

import com.oyesuno.core.database.entity.ScheduleEntity
import com.oyesuno.core.model.RepeatType
import com.oyesuno.core.model.Schedule

fun ScheduleEntity.toDomain(): Schedule {
    return Schedule(
        id = id,
        name = name,
        categoryId = categoryId,
        startTimeMs = startTimeMs,
        durationMs = durationMs,
        repeatType = try { RepeatType.valueOf(repeatType) } catch (e: Exception) { RepeatType.NEVER },
        isEnabled = isEnabled,
        nextTriggerTimeMs = nextTriggerTimeMs,
        createdAt = createdAt
    )
}

fun Schedule.toEntity(): ScheduleEntity {
    return ScheduleEntity(
        id = id,
        name = name,
        categoryId = categoryId,
        startTimeMs = startTimeMs,
        durationMs = durationMs,
        repeatType = repeatType.name,
        isEnabled = isEnabled,
        nextTriggerTimeMs = nextTriggerTimeMs,
        createdAt = createdAt
    )
}
