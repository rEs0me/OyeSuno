package com.oyesuno.core.database.repository

import com.oyesuno.core.database.OyeSunoDatabase
import com.oyesuno.core.database.entity.BookmarkEntity
import com.oyesuno.core.database.entity.WaveformEntity
import com.oyesuno.core.database.mapper.toDomain
import com.oyesuno.core.database.mapper.toEntity
import com.oyesuno.core.model.Bookmark
import com.oyesuno.core.model.Recording
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository providing a clean API over the Room database for recording operations.
 */
class RecordingRepository(private val database: OyeSunoDatabase) {

    private val recordingDao = database.recordingDao()
    private val bookmarkDao = database.bookmarkDao()
    private val waveformDao = database.waveformDao()

    // --- Recordings ---

    fun getAllRecordings(): Flow<List<Recording>> =
        recordingDao.getAllRecordings().map { entities -> entities.map { it.toDomain() } }

    fun getMostRecentRecording(): Flow<Recording?> =
        recordingDao.getMostRecentRecording().map { it?.toDomain() }

    fun getFavoriteRecordings(): Flow<List<Recording>> =
        recordingDao.getFavoriteRecordings().map { entities -> entities.map { it.toDomain() } }

    fun searchRecordings(query: String): Flow<List<Recording>> =
        recordingDao.searchRecordings(query).map { entities -> entities.map { it.toDomain() } }

    fun getRecordingsByTriggerType(triggerType: String): Flow<List<Recording>> =
        recordingDao.getRecordingsByTriggerType(triggerType).map { entities -> entities.map { it.toDomain() } }

    fun getRecordingsCreatedAfter(timestamp: Long): Flow<List<Recording>> =
        recordingDao.getRecordingsCreatedAfter(timestamp).map { entities -> entities.map { it.toDomain() } }

    fun getTotalAudioSize(): Flow<Long> = recordingDao.getTotalAudioSize()

    fun getRecordingCount(): Flow<Int> = recordingDao.getRecordingCount()

    suspend fun getRecordingById(id: Long): Recording? =
        recordingDao.getRecordingById(id)?.toDomain()

    suspend fun insertRecording(recording: Recording): Long =
        recordingDao.insertRecording(recording.toEntity())

    suspend fun updateRecording(recording: Recording) =
        recordingDao.updateRecording(recording.toEntity())

    suspend fun deleteRecording(recording: Recording) {
        recordingDao.deleteRecording(recording.toEntity())
    }

    suspend fun deleteRecordingById(id: Long) =
        recordingDao.deleteRecordingById(id)

    suspend fun toggleFavorite(recordingId: Long) {
        val recording = recordingDao.getRecordingById(recordingId) ?: return
        recordingDao.updateRecording(recording.copy(isFavorite = !recording.isFavorite))
    }

    suspend fun renameRecording(recordingId: Long, newTitle: String) {
        val recording = recordingDao.getRecordingById(recordingId) ?: return
        recordingDao.updateRecording(
            recording.copy(title = newTitle, updatedAt = System.currentTimeMillis())
        )
    }

    // --- Bookmarks ---

    fun getBookmarksForRecording(recordingId: Long): Flow<List<Bookmark>> =
        bookmarkDao.getBookmarksForRecording(recordingId).map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun insertBookmark(bookmark: Bookmark): Long =
        bookmarkDao.insertBookmark(bookmark.toEntity())

    suspend fun deleteBookmark(bookmarkId: Long) {
        val entity = BookmarkEntity(id = bookmarkId, recordingId = 0, timestampMs = 0)
        bookmarkDao.deleteBookmark(entity)
    }

    // --- Waveforms ---

    suspend fun getWaveformAmplitudes(recordingId: Long): List<Float>? {
        val entity = waveformDao.getWaveformForRecording(recordingId) ?: return null
        return parseAmplitudesJson(entity.amplitudes)
    }

    suspend fun saveWaveformAmplitudes(recordingId: Long, amplitudes: List<Float>) {
        waveformDao.insertWaveform(
            WaveformEntity(
                recordingId = recordingId,
                amplitudes = amplitudes.joinToString(",") { "%.3f".format(it) },
                sampleCount = amplitudes.size
            )
        )
    }

    private fun parseAmplitudesJson(json: String): List<Float> {
        return try {
            json.split(",").map { it.trim().toFloat() }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
