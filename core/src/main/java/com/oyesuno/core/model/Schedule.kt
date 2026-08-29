package com.oyesuno.core.model

data class Schedule(
    val id: Long = 0,
    val name: String,
    val categoryId: Long? = null,
    val startTimeMs: Long,
    val durationMs: Long,
    val repeatType: RepeatType = RepeatType.NEVER,
    val isEnabled: Boolean = true,
    val nextTriggerTimeMs: Long,
    val createdAt: Long = System.currentTimeMillis()
)
