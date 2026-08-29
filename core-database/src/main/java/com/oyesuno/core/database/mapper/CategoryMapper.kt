package com.oyesuno.core.database.mapper

import com.oyesuno.core.database.entity.CategoryEntity
import com.oyesuno.core.model.Category

fun CategoryEntity.toDomain(): Category {
    return Category(
        id = id,
        name = name,
        isBuiltIn = isBuiltIn
    )
}

fun Category.toEntity(): CategoryEntity {
    return CategoryEntity(
        id = id,
        name = name,
        isBuiltIn = isBuiltIn
    )
}
