package com.oyesuno.core.database.repository

import com.oyesuno.core.database.OyeSunoDatabase
import com.oyesuno.core.database.mapper.toDomain
import com.oyesuno.core.database.mapper.toEntity
import com.oyesuno.core.model.Schedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ScheduleRepository(private val database: OyeSunoDatabase) {

    private val scheduleDao = database.scheduleDao()

    fun getAllSchedules(): Flow<List<Schedule>> =
        scheduleDao.getAllSchedules().map { list -> list.map { it.toDomain() } }

    fun getEnabledSchedules(): Flow<List<Schedule>> =
        scheduleDao.getEnabledSchedules().map { list -> list.map { it.toDomain() } }

    suspend fun getScheduleById(id: Long): Schedule? =
        scheduleDao.getScheduleById(id)?.toDomain()

    suspend fun insertSchedule(schedule: Schedule): Long =
        scheduleDao.insertSchedule(schedule.toEntity())

    suspend fun updateSchedule(schedule: Schedule) =
        scheduleDao.updateSchedule(schedule.toEntity())

    suspend fun toggleSchedule(scheduleId: Long) {
        val entity = scheduleDao.getScheduleById(scheduleId) ?: return
        scheduleDao.updateSchedule(entity.copy(isEnabled = !entity.isEnabled))
    }

    suspend fun deleteSchedule(schedule: Schedule) =
        scheduleDao.deleteSchedule(schedule.toEntity())
}
