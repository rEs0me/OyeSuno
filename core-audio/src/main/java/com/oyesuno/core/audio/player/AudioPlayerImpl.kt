package com.oyesuno.core.audio.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.oyesuno.core.engine.AudioPlayer
import com.oyesuno.core.engine.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Full [AudioPlayer] implementation using Media3 ExoPlayer.
 * Provides playback with speed control, seek, and position tracking.
 */
class AudioPlayerImpl(context: Context) : AudioPlayer {

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    override val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)
    private var positionUpdateJob: Job? = null

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        _durationMs.value = duration.coerceAtLeast(0)
                        applyAudioBoost()
                        if (isPlaying) {
                            _playbackState.value = PlaybackState.Playing
                            isAnyAudioPlaying = true
                        }
                    }
                    Player.STATE_ENDED -> {
                        _playbackState.value = PlaybackState.Completed
                        isAnyAudioPlaying = false
                        stopPositionUpdates()
                    }
                    Player.STATE_BUFFERING -> { /* loading */ }
                    Player.STATE_IDLE -> {
                        _playbackState.value = PlaybackState.Idle
                        isAnyAudioPlaying = false
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isAnyAudioPlaying = isPlaying
                if (isPlaying) {
                    applyAudioBoost()
                    _playbackState.value = PlaybackState.Playing
                    startPositionUpdates()
                } else {
                    if (_playbackState.value !is PlaybackState.Completed) {
                        _playbackState.value = PlaybackState.Paused
                    }
                    stopPositionUpdates()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _playbackState.value = PlaybackState.Error(error.message ?: "Playback error")
                stopPositionUpdates()
            }
        })
    }

    companion object {
        @Volatile
        var isAnyAudioPlaying: Boolean = false
            private set
    }

    override fun load(filePath: String) {
        try {
            val uri = if (filePath.startsWith("content://") || filePath.startsWith("file://")) {
                android.net.Uri.parse(filePath)
            } else {
                android.net.Uri.fromFile(java.io.File(filePath))
            }
            val mediaItem = MediaItem.fromUri(uri)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            _playbackState.value = PlaybackState.Idle
            _currentPositionMs.value = 0L
        } catch (e: Exception) {
            android.util.Log.e("OyeSuno", "Error loading file in AudioPlayer", e)
            _playbackState.value = PlaybackState.Error("Could not load file: ${e.message}")
        }
    }

    override fun play() {
        exoPlayer.play()
    }

    override fun pause() {
        exoPlayer.pause()
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _currentPositionMs.value = positionMs
    }

    override fun setSpeed(speed: Float) {
        exoPlayer.playbackParameters = PlaybackParameters(speed)
    }

    private var currentGainMb = 0
    private var loudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null

    fun setAudioBoost(gainMb: Int) {
        currentGainMb = gainMb
        applyAudioBoost()
    }

    private fun applyAudioBoost() {
        try {
            val audioSessionId = exoPlayer.audioSessionId
            if (audioSessionId != 0 && audioSessionId != androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) {
                if (loudnessEnhancer == null) {
                    loudnessEnhancer = android.media.audiofx.LoudnessEnhancer(audioSessionId)
                }
                loudnessEnhancer?.apply {
                    setTargetGain(currentGainMb.coerceIn(0, 4000))
                    enabled = (currentGainMb > 0)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("AudioPlayer", "Hardware LoudnessEnhancer notice: ${e.message}")
        }

        // Dual amplification: Software digital gain scaling
        try {
            val multiplier = when (currentGainMb) {
                600 -> 1.35f  // +6dB Clarity
                1200 -> 1.75f // +12dB Boost
                2000 -> 2.30f // +20dB Max
                else -> 1.0f  // Normal
            }
            exoPlayer.volume = multiplier
        } catch (e: Exception) { /* ignore */ }
    }

    override fun release() {
        stopPositionUpdates()
        try {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
            loudnessEnhancer = null
        } catch (e: Exception) { /* ignore */ }
        exoPlayer.release()
        _playbackState.value = PlaybackState.Idle
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive) {
                _currentPositionMs.value = exoPlayer.currentPosition.coerceAtLeast(0)
                delay(50) // ~20 FPS for smooth seekbar
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
    }
}
