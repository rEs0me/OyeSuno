package com.oyesuno.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val categoryId: Long? = null,
    val startTimeMs: Long,
    val durationMs: Long,
    val repeatType: String = "NEVER",
    val isEnabled: Boolean = true,
    val nextTriggerTimeMs: Long,
    val createdAt: Long = System.currentTimeMillis()
)
