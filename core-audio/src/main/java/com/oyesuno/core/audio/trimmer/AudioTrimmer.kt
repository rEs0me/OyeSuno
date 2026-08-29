package com.oyesuno.core.audio.trimmer

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object AudioTrimmer {

    private const val TAG = "AudioTrimmer"

    /**
     * Trims an audio file between [startMs] and [endMs] and writes to [outputPath].
     * Uses Android's native MediaExtractor and MediaMuxer for fast, lossless cutting.
     */
    suspend fun trimAudio(
        sourcePath: String,
        outputPath: String,
        startMs: Long,
        endMs: Long
    ): Boolean = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null

        try {
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()

            extractor.setDataSource(sourcePath)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || audioFormat == null) {
                Log.e(TAG, "No audio track found in $sourcePath")
                return@withContext false
            }

            extractor.selectTrack(audioTrackIndex)

            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()

            val startUs = (startMs.coerceAtLeast(0)) * 1000L
            val endUs = (endMs.coerceAtLeast(startMs)) * 1000L

            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val maxBufferSize = try {
                audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } catch (e: Exception) {
                1024 * 256
            }.coerceAtLeast(1024 * 64)

            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            var presentationTimeOffsetUs = -1L

            while (true) {
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break

                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs > endUs) break

                if (sampleTimeUs >= startUs) {
                    if (presentationTimeOffsetUs < 0) {
                        presentationTimeOffsetUs = sampleTimeUs
                    }
                    bufferInfo.presentationTimeUs = (sampleTimeUs - presentationTimeOffsetUs).coerceAtLeast(0)
                    bufferInfo.flags = extractor.sampleFlags
                    bufferInfo.offset = 0

                    muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                }

                if (!extractor.advance()) break
            }

            Log.i(TAG, "Successfully trimmed audio from $startMs ms to $endMs ms -> $outputPath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trim audio: ${e.message}", e)
            false
        } finally {
            try {
                extractor.release()
            } catch (e: Exception) { /* ignore */ }
            try {
                muxer?.stop()
                muxer?.release()
            } catch (e: Exception) { /* ignore */ }
        }
    }
}
