package com.oyesuno.core.model

data class Category(
    val id: Long,
    val name: String,
    val isBuiltIn: Boolean
) {
    companion object {
        val PERSONAL = Category(1, "Personal", true)
        val STUDY = Category(2, "Study", true)
        val IDEAS = Category(3, "Ideas", true)
        val MEETINGS = Category(4, "Meetings", true)
        val WORK = Category(5, "Work", true)
        val OTHER = Category(6, "Other", true)
        
        val BUILT_IN_CATEGORIES = listOf(PERSONAL, STUDY, IDEAS, MEETINGS, WORK, OTHER)
    }
}
