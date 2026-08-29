package com.oyesuno.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.oyesuno.core.audio.call.VoipCallDetector
import com.oyesuno.core.database.OyeSunoDatabase
import com.oyesuno.core.storage.FileManager
import com.oyesuno.core.storage.StorageManager
import com.oyesuno.feature.recorder.service.RecordingService

class OyeSunoApp : Application() {
    lateinit var database: OyeSunoDatabase
        private set
    lateinit var storageManager: StorageManager
        private set
    lateinit var fileManager: FileManager
        private set

    private var voipCallDetector: VoipCallDetector? = null
    private var isAutoCallRecordingOngoing = false

    override fun onCreate() {
        super.onCreate()
        database = OyeSunoDatabase.getInstance(this)
        storageManager = StorageManager(this)
        fileManager = FileManager(this, storageManager)

        initGlobalCallAndVoipDetector()
    }

    /**
     * Initializes global listener for WhatsApp, Instagram, Telegram, and Cellular calls.
     * Automatically starts background recording on call start and saves to DB on call end.
     */
    private fun initGlobalCallAndVoipDetector() {
        voipCallDetector = VoipCallDetector(
            context = this,
            onCallStarted = { isVoip ->
                val prefs = getSharedPreferences("oyesuno_settings_prefs", Context.MODE_PRIVATE)
                val isAutoCallEnabled = prefs.getBoolean("key_auto_record_calls", true)
                if (isAutoCallEnabled && !isAutoCallRecordingOngoing) {
                    isAutoCallRecordingOngoing = true
                    Log.i("OyeSunoApp", "Auto-starting recording for ${if (isVoip) "WhatsApp/VoIP" else "Phone"} call...")
                    try {
                        val intent = Intent(this, RecordingService::class.java).apply {
                            action = RecordingService.ACTION_START
                            putExtra("trigger_type", "CALL")
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                    } catch (e: Exception) {
                        Log.e("OyeSunoApp", "Failed to start call recording service: ${e.message}")
                    }
                }
            },
            onCallEnded = {
                if (isAutoCallRecordingOngoing) {
                    isAutoCallRecordingOngoing = false
                    Log.i("OyeSunoApp", "Call ended. Auto-stopping and saving recording...")
                    try {
                        val stopIntent = Intent(this, RecordingService::class.java).apply {
                            action = RecordingService.ACTION_STOP
                        }
                        startService(stopIntent)
                    } catch (e: Exception) {
                        Log.e("OyeSunoApp", "Failed to stop call recording service: ${e.message}")
                    }
                }
            }
        )
        voipCallDetector?.startListening()
    }

    fun refreshCallDetector() {
        voipCallDetector?.stopListening()
        initGlobalCallAndVoipDetector()
    }
}
