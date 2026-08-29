package com.oyesuno.core.database.mapper

import com.oyesuno.core.database.entity.BookmarkEntity
import com.oyesuno.core.model.Bookmark

fun BookmarkEntity.toDomain(): Bookmark {
    return Bookmark(
        id = id,
        recordingId = recordingId,
        timestampMs = timestampMs,
        label = label,
        createdAt = createdAt
    )
}

fun Bookmark.toEntity(): BookmarkEntity {
    return BookmarkEntity(
        id = id,
        recordingId = recordingId,
        timestampMs = timestampMs,
        label = label,
        createdAt = createdAt
    )
}
