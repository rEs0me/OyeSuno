package com.oyesuno.core.storage

import android.content.Context
import android.net.Uri
import com.oyesuno.core.model.AudioFormat
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages recording file operations: creation, deletion, moving, and export.
 */
class FileManager(
    private val context: Context,
    private val storageManager: StorageManager
) {

    /**
     * Creates a new empty recording file with the appropriate extension.
     *
     * @param format The audio format to use for the file extension
     * @return The created file
     */
    fun createRecordingFile(format: AudioFormat): File {
        val dir = storageManager.getRecordingDirectory()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "OyeSuno_${timestamp}.${format.extension}"
        val file = File(dir, fileName)
        file.createNewFile()
        return file
    }

    /**
     * Deletes a recording file at the given path.
     *
     * @return true if the file was successfully deleted
     */
    fun deleteRecordingFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            file.exists() && file.delete()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Moves a recording file to a new directory.
     *
     * @return The new file path
     * @throws IOException if the move fails
     */
    fun moveRecordingFile(filePath: String, newDir: File): String {
        val sourceFile = File(filePath)
        if (!sourceFile.exists()) {
            throw IOException("Source file does not exist: $filePath")
        }
        if (!newDir.exists()) {
            newDir.mkdirs()
        }
        val destFile = File(newDir, sourceFile.name)
        sourceFile.copyTo(destFile, overwrite = true)
        sourceFile.delete()
        return destFile.absolutePath
    }

    /**
     * Checks if a file exists at the given path.
     */
    fun fileExists(filePath: String): Boolean {
        return File(filePath).exists()
    }

    /**
     * Returns the file size in bytes.
     */
    fun getFileSize(filePath: String): Long {
        val file = File(filePath)
        return if (file.exists()) file.length() else 0L
    }

    /**
     * Exports a recording file to a destination URI (e.g., user-picked location).
     *
     * @param filePath Source recording file path
     * @param destinationUri Destination URI from the system file picker
     * @return true if export was successful
     */
    fun exportRecording(filePath: String, destinationUri: Uri): Boolean {
        return try {
            val sourceFile = File(filePath)
            if (!sourceFile.exists()) return false

            context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
