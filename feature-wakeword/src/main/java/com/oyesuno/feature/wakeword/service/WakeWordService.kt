package com.oyesuno.feature.wakeword.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.oyesuno.core.audio.wakeword.WakeWordEngineImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class WakeWordService : Service() {

    companion object {
        private const val TAG = "WakeWordService"
        const val CHANNEL_ID = "oyesuno_wakeword_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.oyesuno.wakeword.START"
        const val ACTION_STOP = "com.oyesuno.wakeword.STOP"
        const val EXTRA_KEYWORD = "extra_keyword"

        fun start(context: Context, keyword: String = "suno") {
            val intent = Intent(context, WakeWordService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_KEYWORD, keyword)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WakeWordService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private var wakeWordEngine: WakeWordEngineImpl? = null
    private var detectionJob: Job? = null
    private var currentKeyword = "suno"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        wakeWordEngine = WakeWordEngineImpl(applicationContext)

        detectionJob = scope.launch {
            wakeWordEngine?.onWakeWordDetected?.collect {
                Log.i(TAG, "Wake word detected in background service! Launching RecordingService...")
                launchRecordingService()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        if (action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val kw = intent?.getStringExtra(EXTRA_KEYWORD) ?: currentKeyword
        currentKeyword = kw
        wakeWordEngine?.setCustomKeywords(listOf(kw))

        val notification = buildForegroundNotification(kw)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Background microphone FGS restriction on Android 14, falling back to standard notification: ${e.message}")
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to start foreground notification", e2)
            }
        }

        wakeWordEngine?.start()
        Log.i(TAG, "WakeWordService running continuously in foreground for keyword: '$kw'")

        return START_STICKY
    }

    private fun launchRecordingService() {
        try {
            val serviceIntent = Intent(
                applicationContext,
                com.oyesuno.feature.recorder.service.RecordingService::class.java
            ).apply {
                action = com.oyesuno.feature.recorder.service.RecordingService.ACTION_START
                putExtra("trigger_type", "WAKE_WORD")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start RecordingService from WakeWordService", e)
        }
    }

    private fun buildForegroundNotification(keyword: String): Notification {
        val appIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OyeSuno Wake Word Active")
            .setContentText("Listening silently for \"$keyword\" in the background")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wake Word Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps OyeSuno wake word listening when the app is minimized or screen is locked"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        detectionJob?.cancel()
        wakeWordEngine?.release()
        wakeWordEngine = null
        Log.i(TAG, "WakeWordService destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
