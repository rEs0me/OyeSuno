package com.oyesuno.core.storage

import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File

/**
 * Manages storage directories and space monitoring for recordings.
 */
class StorageManager(private val context: Context) {

    /**
     * Returns the recording directory, creating it if it doesn't exist.
     */
    fun getRecordingDirectory(): File {
        return try {
            val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS)
            val dir = File(publicDir, "OyeSuno")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            if (dir.exists() && dir.canWrite()) dir
            else {
                val fallback = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "recordings")
                if (!fallback.exists()) fallback.mkdirs()
                fallback
            }
        } catch (e: Exception) {
            val fallback = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "recordings")
            if (!fallback.exists()) fallback.mkdirs()
            fallback
        }
    }

    /**
     * Returns the available free space in bytes on the recording storage volume.
     */
    fun getAvailableSpace(): Long {
        val dir = getRecordingDirectory()
        return try {
            val stat = StatFs(dir.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Calculates the total size of all files in the recording directory.
     */
    fun getTotalAudioSize(): Long {
        val dir = getRecordingDirectory()
        return dir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    /**
     * Checks if storage is below the threshold.
     *
     * @param thresholdMb Threshold in megabytes (default 500 MB)
     */
    fun isLowStorage(thresholdMb: Long = 500): Boolean {
        val thresholdBytes = thresholdMb * 1024 * 1024
        return getAvailableSpace() < thresholdBytes
    }

    /**
     * Returns a user-friendly storage warning message, or null if storage is adequate.
     */
    fun getStorageWarningMessage(): String? {
        val availableMb = getAvailableSpace() / (1024 * 1024)
        return when {
            availableMb < 100 -> "Only ${availableMb} MB left. Consider freeing up space."
            availableMb < 500 -> "Only ${availableMb} MB left."
            else -> null
        }
    }

    /**
     * Returns the recording directory path as a user-readable string.
     */
    fun getRecordingDirectoryPath(): String {
        return getRecordingDirectory().absolutePath
    }
}
