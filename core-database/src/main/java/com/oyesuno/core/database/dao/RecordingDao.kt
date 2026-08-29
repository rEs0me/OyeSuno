package com.oyesuno.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.oyesuno.core.database.entity.RecordingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Insert
    suspend fun insertRecording(recording: RecordingEntity): Long

    @Update
    suspend fun updateRecording(recording: RecordingEntity)

    @Delete
    suspend fun deleteRecording(recording: RecordingEntity)

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getRecordingById(id: Long): RecordingEntity?

    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    fun getAllRecordings(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE categoryId = :categoryId ORDER BY createdAt DESC")
    fun getRecordingsByCategory(categoryId: Long): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun getFavoriteRecordings(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE title LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchRecordings(query: String): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE triggerType = :triggerType ORDER BY createdAt DESC")
    fun getRecordingsByTriggerType(triggerType: String): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE createdAt >= :timestamp ORDER BY createdAt DESC")
    fun getRecordingsCreatedAfter(timestamp: Long): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings ORDER BY createdAt DESC LIMIT 1")
    fun getMostRecentRecording(): Flow<RecordingEntity?>

    @Query("SELECT COALESCE(SUM(fileSize), 0) FROM recordings")
    fun getTotalAudioSize(): Flow<Long>

    @Query("SELECT COUNT(*) FROM recordings")
    fun getRecordingCount(): Flow<Int>

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteRecordingById(id: Long)
}
