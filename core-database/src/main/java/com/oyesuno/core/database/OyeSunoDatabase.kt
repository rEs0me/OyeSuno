package com.oyesuno.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.oyesuno.core.database.dao.BookmarkDao
import com.oyesuno.core.database.dao.CategoryDao
import com.oyesuno.core.database.dao.RecordingDao
import com.oyesuno.core.database.dao.ScheduleDao
import com.oyesuno.core.database.dao.TagDao
import com.oyesuno.core.database.dao.WaveformDao
import com.oyesuno.core.database.entity.BookmarkEntity
import com.oyesuno.core.database.entity.CategoryEntity
import com.oyesuno.core.database.entity.RecordingEntity
import com.oyesuno.core.database.entity.RecordingTagCrossRef
import com.oyesuno.core.database.entity.ScheduleEntity
import com.oyesuno.core.database.entity.TagEntity
import com.oyesuno.core.database.entity.WaveformEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        RecordingEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        RecordingTagCrossRef::class,
        BookmarkEntity::class,
        ScheduleEntity::class,
        WaveformEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class OyeSunoDatabase : RoomDatabase() {

    abstract fun recordingDao(): RecordingDao
    abstract fun categoryDao(): CategoryDao
    abstract fun tagDao(): TagDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun waveformDao(): WaveformDao

    companion object {
        private const val DATABASE_NAME = "oyesuno.db"

        @Volatile
        private var INSTANCE: OyeSunoDatabase? = null

        fun getInstance(context: Context): OyeSunoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): OyeSunoDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                OyeSunoDatabase::class.java,
                DATABASE_NAME
            )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Prepopulate built-in categories
                        CoroutineScope(Dispatchers.IO).launch {
                            val database = getInstance(context)
                            val categoryDao = database.categoryDao()
                            val builtInCategories = listOf(
                                CategoryEntity(name = "Personal", isBuiltIn = true),
                                CategoryEntity(name = "Study", isBuiltIn = true),
                                CategoryEntity(name = "Ideas", isBuiltIn = true),
                                CategoryEntity(name = "Meetings", isBuiltIn = true),
                                CategoryEntity(name = "Work", isBuiltIn = true),
                                CategoryEntity(name = "Other", isBuiltIn = true)
                            )
                            builtInCategories.forEach { categoryDao.insertCategory(it) }
                        }
                    }
                })
                .build()
        }
    }
}
