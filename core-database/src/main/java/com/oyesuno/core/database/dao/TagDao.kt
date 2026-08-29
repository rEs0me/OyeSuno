package com.oyesuno.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.oyesuno.core.database.entity.RecordingTagCrossRef
import com.oyesuno.core.database.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: TagEntity): Long

    @Delete
    suspend fun deleteTag(tag: TagEntity)

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAllTags(): Flow<List<TagEntity>>

    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN recording_tags rt ON t.id = rt.tagId
        WHERE rt.recordingId = :recordingId
        ORDER BY t.name ASC
    """)
    fun getTagsForRecording(recordingId: Long): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecordingTag(crossRef: RecordingTagCrossRef)

    @Query("DELETE FROM recording_tags WHERE recordingId = :recordingId AND tagId = :tagId")
    suspend fun deleteRecordingTag(recordingId: Long, tagId: Long)
}
