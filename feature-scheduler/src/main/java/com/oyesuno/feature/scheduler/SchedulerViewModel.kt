package com.oyesuno.feature.scheduler

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oyesuno.core.database.OyeSunoDatabase
import com.oyesuno.core.database.repository.ScheduleRepository
import com.oyesuno.core.model.Schedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SchedulerViewModel(application: Application) : AndroidViewModel(application) {

    private val database = OyeSunoDatabase.getInstance(application)
    private val repository = ScheduleRepository(database)

    data class SchedulerUiState(
        val schedules: List<Schedule> = emptyList(),
        val isLoading: Boolean = true
    )

    private val _uiState = MutableStateFlow(SchedulerUiState())
    val uiState: StateFlow<SchedulerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllSchedules().collect { list ->
                _uiState.value = SchedulerUiState(
                    schedules = list,
                    isLoading = false
                )
            }
        }
    }

    fun addSchedule(schedule: Schedule) {
        viewModelScope.launch {
            repository.insertSchedule(schedule)
        }
    }

    fun toggleSchedule(scheduleId: Long) {
        viewModelScope.launch {
            repository.toggleSchedule(scheduleId)
        }
    }

    fun deleteSchedule(schedule: Schedule) {
        viewModelScope.launch {
            repository.deleteSchedule(schedule)
        }
    }
}
