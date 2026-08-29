package com.oyesuno.feature.player.service

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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.oyesuno.core.audio.player.AudioPlayerImpl
import com.oyesuno.core.engine.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground Service for background audio playback with lock screen and notification shade media controls.
 */
class PlaybackService : Service() {

    companion object {
        const val CHANNEL_ID = "oyesuno_playback_channel"
        const val NOTIFICATION_ID = 2002

        const val ACTION_START = "com.oyesuno.ACTION_PLAYBACK_START"
        const val ACTION_TOGGLE = "com.oyesuno.ACTION_PLAYBACK_TOGGLE"
        const val ACTION_REWIND = "com.oyesuno.ACTION_PLAYBACK_REWIND"
        const val ACTION_FORWARD = "com.oyesuno.ACTION_PLAYBACK_FORWARD"
        const val ACTION_STOP = "com.oyesuno.ACTION_PLAYBACK_STOP"

        const val EXTRA_TITLE = "extra_recording_title"
        const val EXTRA_FILE_PATH = "extra_file_path"

        var sharedPlayer: AudioPlayerImpl? = null
            private set

        fun getOrCreatePlayer(context: Context): AudioPlayerImpl {
            if (sharedPlayer == null) {
                sharedPlayer = AudioPlayerImpl(context.applicationContext)
            }
            return sharedPlayer!!
        }
    }

    private var currentTitle: String = "Recording"
    private var isPlayingState: Boolean = false
    private val scope = CoroutineScope(Dispatchers.Main)
    private var observerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        observePlayerState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val player = getOrCreatePlayer(this)

        when (intent?.action) {
            ACTION_START -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Recording"
                val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
                currentTitle = title

                if (!filePath.isNullOrBlank()) {
                    player.load(filePath)
                    player.play()
                }

                val notification = buildNotification(isPlaying = true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
            ACTION_TOGGLE -> {
                if (isPlayingState) {
                    player.pause()
                } else {
                    player.play()
                }
            }
            ACTION_REWIND -> {
                val pos = player.currentPositionMs.value - 10000
                player.seekTo(pos.coerceAtLeast(0))
            }
            ACTION_FORWARD -> {
                val pos = player.currentPositionMs.value + 10000
                player.seekTo(pos.coerceAtMost(player.durationMs.value))
            }
            ACTION_STOP -> {
                player.pause()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun observePlayerState() {
        val player = getOrCreatePlayer(this)
        observerJob?.cancel()
        observerJob = scope.launch {
            player.playbackState.collectLatest { state ->
                isPlayingState = (state is PlaybackState.Playing)
                if (state is PlaybackState.Completed) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else if (state is PlaybackState.Playing || state is PlaybackState.Paused) {
                    val notification = buildNotification(isPlaying = isPlayingState)
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, notification)
                }
            }
        }
    }

    private fun buildNotification(isPlaying: Boolean): Notification {
        val toggleIntent = PendingIntent.getService(
            this, 1,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_TOGGLE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rewindIntent = PendingIntent.getService(
            this, 2,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_REWIND },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val forwardIntent = PendingIntent.getService(
            this, 3,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_FORWARD },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 4,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(if (isPlaying) "Playing in background" else "Paused")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openAppIntent)
            .setOngoing(isPlaying)
            .addAction(android.R.drawable.ic_media_rew, "-10s", rewindIntent)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                toggleIntent
            )
            .addAction(android.R.drawable.ic_media_ff, "+10s", forwardIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Playback Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows player controls and status for active audio playback"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        observerJob?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
