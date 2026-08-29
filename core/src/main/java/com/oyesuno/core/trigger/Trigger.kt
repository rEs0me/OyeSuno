package com.oyesuno.core.trigger

import com.oyesuno.core.model.RecordingTriggerType
import kotlinx.coroutines.flow.StateFlow

interface Trigger {
    val isActive: StateFlow<Boolean>
    val type: RecordingTriggerType
    fun activate()
    fun deactivate()
}
