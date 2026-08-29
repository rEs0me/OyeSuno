package com.oyesuno.feature.settings

import android.content.Context
import android.content.SharedPreferences
import com.oyesuno.core.model.AudioFormat
import com.oyesuno.core.model.QualityPreset

class SettingsPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("oyesuno_settings_prefs", Context.MODE_PRIVATE)

    var audioFormat: AudioFormat
        get() = try {
            AudioFormat.valueOf(prefs.getString(KEY_FORMAT, AudioFormat.M4A_AAC.name) ?: AudioFormat.M4A_AAC.name)
        } catch (e: Exception) {
            AudioFormat.M4A_AAC
        }
        set(value) = prefs.edit().putString(KEY_FORMAT, value.name).apply()

    var qualityPreset: QualityPreset
        get() = try {
            QualityPreset.valueOf(prefs.getString(KEY_QUALITY, QualityPreset.HIGH.name) ?: QualityPreset.HIGH.name)
        } catch (e: Exception) {
            QualityPreset.HIGH
        }
        set(value) = prefs.edit().putString(KEY_QUALITY, value.name).apply()

    var defaultDurationMs: Long
        get() = prefs.getLong(KEY_DURATION, 0L)
        set(value) = prefs.edit().putLong(KEY_DURATION, value).apply()

    var wakeWordEnabled: Boolean
        get() = prefs.getBoolean(KEY_WAKE_WORD, false)
        set(value) = prefs.edit().putBoolean(KEY_WAKE_WORD, value).apply()

    var customWakeWord: String
        get() = prefs.getString(KEY_CUSTOM_WAKE_WORD, "suno") ?: "suno"
        set(value) = prefs.edit().putString(KEY_CUSTOM_WAKE_WORD, value.lowercase().trim()).apply()

    var themeMode: String
        get() = prefs.getString(KEY_THEME, "SYSTEM") ?: "SYSTEM"
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()

    var autoRecordCalls: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RECORD_CALLS, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_RECORD_CALLS, value).apply()

    var autoSyncDeviceRecordings: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SYNC_DEVICE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SYNC_DEVICE, value).apply()

    var audioSource: com.oyesuno.core.model.AudioSourceOption
        get() = try {
            com.oyesuno.core.model.AudioSourceOption.valueOf(
                prefs.getString(KEY_AUDIO_SOURCE, com.oyesuno.core.model.AudioSourceOption.VOICE_RECOGNITION.name)
                    ?: com.oyesuno.core.model.AudioSourceOption.VOICE_RECOGNITION.name
            )
        } catch (e: Exception) {
            com.oyesuno.core.model.AudioSourceOption.VOICE_RECOGNITION
        }
        set(value) = prefs.edit().putString(KEY_AUDIO_SOURCE, value.name).apply()

    companion object {
        private const val KEY_FORMAT = "key_audio_format"
        private const val KEY_QUALITY = "key_quality_preset"
        private const val KEY_DURATION = "key_default_duration"
        private const val KEY_AUDIO_SOURCE = "key_audio_source_type"
        private const val KEY_WAKE_WORD = "key_wake_word_enabled"
        private const val KEY_CUSTOM_WAKE_WORD = "key_custom_wake_word"
        private const val KEY_THEME = "key_theme_mode"
        private const val KEY_NOTIFICATIONS = "key_notifications_enabled"
        private const val KEY_AUTO_RECORD_CALLS = "key_auto_record_calls"
        private const val KEY_AUTO_SYNC_DEVICE = "key_auto_sync_device"
    }
}
