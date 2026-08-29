package com.oyesuno.feature.recorder.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.oyesuno.core.util.TimeUtils

class RecordingNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "oyesuno_recording_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_STOP = "com.oyesuno.action.NOTIFICATION_STOP"
        const val ACTION_PAUSE = "com.oyesuno.action.NOTIFICATION_PAUSE"
        const val ACTION_RESUME = "com.oyesuno.action.NOTIFICATION_RESUME"
    }

    private val prefs = context.getSharedPreferences("oyesuno_settings_prefs", Context.MODE_PRIVATE)
    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createNotificationChannel()
    }

    fun isNotificationEnabled(): Boolean {
        return prefs.getBoolean("key_notifications_enabled", true)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Recording Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays live timer and quick controls while recording"
                setShowBadge(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun showRecordingNotification(elapsedMs: Long, maxDurationMs: Long = 0L, isPaused: Boolean = false) {
        if (!isNotificationEnabled()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val openIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeText = TimeUtils.formatDuration(elapsedMs)
        val statusText = if (maxDurationMs > 0) {
            "$timeText / ${TimeUtils.formatDuration(maxDurationMs)}"
        } else {
            timeText
        }

        val title = if (isPaused) "Recording Paused" else "Recording in Progress"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("OyeSuno · $title")
            .setContentText("Elapsed: $statusText")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(!isPaused)
            .setContentIntent(openPendingIntent)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // Notification permission missing
        } catch (e: Exception) {
            // Ignore transient notification errors
        }
    }

    fun cancelNotification() {
        try {
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (e: Exception) { /* ignore */ }
    }
}
