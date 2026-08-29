package com.oyesuno.feature.recorder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.oyesuno.feature.recorder.service.RecordingService

/**
 * BroadcastReceiver that detects incoming/outgoing calls and triggers automatic recording when enabled.
 */
class CallRecordingReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallRecordingReceiver"
        private var isCallRecordingActive = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val prefs = context.getSharedPreferences("oyesuno_settings_prefs", Context.MODE_PRIVATE)
        val isAutoCallEnabled = prefs.getBoolean("key_auto_record_calls", true)
        if (!isAutoCallEnabled) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return

        when (state) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Call is connected/answered: start auto recording
                if (!isCallRecordingActive) {
                    isCallRecordingActive = true
                    Log.i(TAG, "Phone call connected. Starting automatic call recording...")
                    try {
                        val serviceIntent = Intent(context, RecordingService::class.java).apply {
                            action = RecordingService.ACTION_START
                            putExtra("trigger_type", "CALL")
                        }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start call recording service: ${e.message}")
                    }
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                // Call has ended: stop recording and save
                if (isCallRecordingActive) {
                    isCallRecordingActive = false
                    Log.i(TAG, "Phone call ended. Stopping call recording and saving...")
                    try {
                        val stopIntent = Intent(context, RecordingService::class.java).apply {
                            action = RecordingService.ACTION_STOP
                        }
                        context.startService(stopIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to stop call recording service: ${e.message}")
                    }
                }
            }
        }
    }
}
