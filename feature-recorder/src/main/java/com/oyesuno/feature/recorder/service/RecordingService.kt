package com.oyesuno.feature.recorder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.oyesuno.core.audio.recorder.RecordingEngineImpl
import com.oyesuno.core.model.AudioFormat
import com.oyesuno.core.model.QualityPreset
import com.oyesuno.core.model.RecordingState
import com.oyesuno.core.util.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service that manages audio recording.
 * Keeps recording alive when the screen is off, app is minimized, or device is locked.
 * Provides notification with Pause/Resume and Stop controls.
 * Notification tap opens the live recorder screen.
 */
class RecordingService : Service() {

    companion object {
        const val CHANNEL_ID = "oyesuno_recording_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.oyesuno.action.START"
        const val ACTION_PAUSE = "com.oyesuno.action.PAUSE"
        const val ACTION_RESUME = "com.oyesuno.action.RESUME"
        const val ACTION_STOP = "com.oyesuno.action.STOP"

        /** Extra on the launch intent telling MainActivity to navigate to recorder */
        const val EXTRA_NAVIGATE_TO_RECORDER = "navigate_to_recorder"

        const val EXTRA_OUTPUT_PATH = "extra_output_path"
        const val EXTRA_FORMAT = "extra_format"
        const val EXTRA_QUALITY = "extra_quality"
        const val EXTRA_MAX_DURATION_MS = "extra_max_duration_ms"

        fun createStartIntent(
            context: Context,
            outputPath: String,
            format: AudioFormat = AudioFormat.M4A_AAC,
            quality: QualityPreset = QualityPreset.STANDARD,
            maxDurationMs: Long = 0L
        ): Intent {
            return Intent(context, RecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_OUTPUT_PATH, outputPath)
                putExtra(EXTRA_FORMAT, format.name)
                putExtra(EXTRA_QUALITY, quality.name)
                putExtra(EXTRA_MAX_DURATION_MS, maxDurationMs)
            }
        }
    }

    inner class RecordingBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }

    private val binder = RecordingBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var notificationUpdateJob: Job? = null
    private var timerJob: Job? = null
    private var maxDurationMs: Long = 0L
    private var isRecordingStarted = false

    lateinit var recordingEngine: RecordingEngineImpl
        private set

    override fun onCreate() {
        super.onCreate()
        recordingEngine = RecordingEngineImpl.getInstance(this)
        createNotificationChannel()
    }

    private var triggerType: String = "MANUAL"
    private var currentFormat: AudioFormat = AudioFormat.M4A_AAC
    private var currentQuality: QualityPreset = QualityPreset.STANDARD

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording()
            else -> {
                // Guard against double-start: if already recording, ignore
                if (isRecordingStarted) {
                    android.util.Log.w("RecordingService", "Already recording, ignoring duplicate START")
                    return START_STICKY
                }

                val formatName = intent?.getStringExtra(EXTRA_FORMAT) ?: AudioFormat.M4A_AAC.name
                val qualityName = intent?.getStringExtra(EXTRA_QUALITY) ?: QualityPreset.STANDARD.name
                maxDurationMs = intent?.getLongExtra(EXTRA_MAX_DURATION_MS, 0L) ?: 0L
                triggerType = intent?.getStringExtra("trigger_type") ?: "MANUAL"

                val format = try { AudioFormat.valueOf(formatName) } catch (e: Exception) { AudioFormat.M4A_AAC }
                val quality = try { QualityPreset.valueOf(qualityName) } catch (e: Exception) { QualityPreset.STANDARD }
                currentFormat = format
                currentQuality = quality

                val outputPath = intent?.getStringExtra(EXTRA_OUTPUT_PATH) ?: run {
                    val storageManager = com.oyesuno.core.storage.StorageManager(this)
                    val fileManager = com.oyesuno.core.storage.FileManager(this, storageManager)
                    fileManager.createRecordingFile(format).absolutePath
                }

                startRecording(outputPath, format, quality)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Automatically flush and save in-progress recording when app is swiped away
        stopRecording()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        recordingEngine.release()
        super.onDestroy()
    }

    private fun startRecording(outputPath: String, format: AudioFormat, quality: QualityPreset) {
        isRecordingStarted = true
        val title = getRecordingTitle()
        val notification = buildNotification(title, "00:00", isPaused = false)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            android.util.Log.w("RecordingService", "Background microphone FGS restriction on Android 14, falling back to standard notification: ${e.message}")
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (e2: Exception) {
                android.util.Log.e("RecordingService", "Failed to start foreground notification", e2)
            }
        }

        recordingEngine.start(format, quality, outputPath)
        startNotificationUpdates()
        startTimerIfNeeded()
    }

    private fun pauseRecording() {
        recordingEngine.pause()
        updateNotification("Paused", TimeUtils.formatDuration(recordingEngine.getElapsedTimeMs()), isPaused = true)
        notificationUpdateJob?.cancel()
    }

    private fun resumeRecording() {
        recordingEngine.resume()
        startNotificationUpdates()
    }

    fun stopRecording() {
        if (!isRecordingStarted) return
        isRecordingStarted = false

        notificationUpdateJob?.cancel()
        notificationUpdateJob = null
        timerJob?.cancel()
        timerJob = null
        val durationMs = recordingEngine.getElapsedTimeMs()
        val path = recordingEngine.stop()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(NOTIFICATION_ID)
        } catch (e: Exception) { /* ignore */ }

        serviceScope.launch {
            // Use NonCancellable so DB save completes even if service is destroyed
            withContext(NonCancellable + Dispatchers.IO) {
                try {
                    val file = java.io.File(path)
                    if (file.exists() && file.length() > 0) {
                        val database = com.oyesuno.core.database.OyeSunoDatabase.getInstance(applicationContext)
                        val repository = com.oyesuno.core.database.repository.RecordingRepository(database)
                        val trigger = when (triggerType) {
                            "CALL" -> com.oyesuno.core.model.RecordingTriggerType.CALL
                            "WAKE_WORD" -> com.oyesuno.core.model.RecordingTriggerType.WAKE_WORD
                            else -> com.oyesuno.core.model.RecordingTriggerType.MANUAL
                        }
                        val title = com.oyesuno.core.util.FileNameGenerator.generateDefaultName(triggerType = trigger)

                        val recording = com.oyesuno.core.model.Recording(
                            title = title,
                            filePath = file.absolutePath,
                            durationMs = durationMs.coerceAtLeast(1000L),
                            format = currentFormat,
                            sampleRate = currentQuality.sampleRate,
                            bitrate = currentQuality.bitrate,
                            fileSize = file.length(),
                            triggerType = trigger,
                            createdAt = System.currentTimeMillis()
                        )
                        val recId = repository.insertRecording(recording)
                        val waveformGen = com.oyesuno.core.audio.waveform.WaveformGenerator()
                        val waveform = waveformGen.generateWaveform(file.absolutePath, 100)
                        repository.saveWaveformAmplitudes(recId, waveform)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("RecordingService", "Failed to save recording: ${e.message}")
                }
            }
            stopSelf()
        }
    }

    fun addBookmark(label: String = "") {
        recordingEngine.addBookmark(label)
    }

    private fun getRecordingTitle(): String {
        return when (triggerType) {
            "WAKE_WORD" -> "\"Suno\" Detected · Recording"
            "CALL" -> "Call Recording"
            else -> "Recording"
        }
    }

    private fun startNotificationUpdates() {
        notificationUpdateJob?.cancel()
        notificationUpdateJob = serviceScope.launch {
            while (isActive) {
                val elapsed = recordingEngine.getElapsedTimeMs()
                val timeText = TimeUtils.formatDuration(elapsed)

                val statusText = if (maxDurationMs > 0) {
                    "$timeText / ${TimeUtils.formatDuration(maxDurationMs)}"
                } else {
                    timeText
                }

                updateNotification(getRecordingTitle(), statusText, isPaused = false)
                delay(1000)
            }
        }
    }

    private fun startTimerIfNeeded() {
        if (maxDurationMs <= 0) return

        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                val elapsed = recordingEngine.getElapsedTimeMs()
                if (elapsed >= maxDurationMs) {
                    stopRecording()
                    break
                }
                delay(500)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Recording",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows recording status"
            setShowBadge(false)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, contentText: String, isPaused: Boolean): Notification {
        // Deep link: tapping notification opens the app directly to the recorder screen
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            putExtra(EXTRA_NAVIGATE_TO_RECORDER, true)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OyeSuno")
            .setContentText("$title · $contentText")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .setSilent(true)

        // Pause/Resume action
        if (isPaused) {
            val resumeIntent = Intent(this, RecordingService::class.java).apply { action = ACTION_RESUME }
            val resumePending = PendingIntent.getService(this, 1, resumeIntent, PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(android.R.drawable.ic_media_play, "Resume", resumePending)
        } else {
            val pauseIntent = Intent(this, RecordingService::class.java).apply { action = ACTION_PAUSE }
            val pausePending = PendingIntent.getService(this, 2, pauseIntent, PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePending)
        }

        // Stop action
        val stopIntent = Intent(this, RecordingService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(this, 3, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        builder.addAction(android.R.drawable.ic_delete, "Stop", stopPending)

        return builder.build()
    }

    private fun updateNotification(title: String, contentText: String, isPaused: Boolean) {
        val notification = buildNotification(title, contentText, isPaused)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
}
