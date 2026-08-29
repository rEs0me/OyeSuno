package com.oyesuno.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.oyesuno.core.database.entity.WaveformEntity

@Dao
interface WaveformDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaveform(waveform: WaveformEntity)

    @Query("SELECT * FROM waveforms WHERE recordingId = :recordingId")
    suspend fun getWaveformForRecording(recordingId: Long): WaveformEntity?

    @Query("DELETE FROM waveforms WHERE recordingId = :recordingId")
    suspend fun deleteWaveformForRecording(recordingId: Long)
}
