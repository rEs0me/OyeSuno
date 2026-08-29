package com.oyesuno.feature.wakeword

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.oyesuno.feature.wakeword.service.WakeWordService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class WakeWordViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val prefs = application.getSharedPreferences("oyesuno_settings_prefs", Context.MODE_PRIVATE)

    private val _isEnabled = MutableStateFlow(prefs.getBoolean("key_wake_word_enabled", false))
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _onTriggered = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 8)
    val onTriggered: SharedFlow<Unit> = _onTriggered.asSharedFlow()

    private val _customKeyword = MutableStateFlow(prefs.getString("key_custom_wake_word", "suno") ?: "suno")
    val customKeyword: StateFlow<String> = _customKeyword.asStateFlow()

    init {
        if (_isEnabled.value) {
            WakeWordService.start(context, _customKeyword.value)
        }
    }

    fun toggleListening(enabled: Boolean) {
        _isEnabled.value = enabled
        prefs.edit().putBoolean("key_wake_word_enabled", enabled).apply()
        if (enabled) {
            WakeWordService.start(context, _customKeyword.value)
        } else {
            WakeWordService.stop(context)
        }
    }

    fun setCustomKeyword(keyword: String) {
        val clean = keyword.trim().lowercase()
        _customKeyword.value = clean
        prefs.edit().putString("key_custom_wake_word", clean).apply()
        if (_isEnabled.value) {
            WakeWordService.start(context, clean)
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}
