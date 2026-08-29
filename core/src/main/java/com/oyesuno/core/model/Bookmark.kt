package com.oyesuno.core.model

data class Bookmark(
    val id: Long = 0,
    val recordingId: Long,
    val timestampMs: Long,
    val label: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
