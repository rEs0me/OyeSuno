package com.oyesuno.core.storage

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import com.oyesuno.core.model.AudioFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ImportedAudioInfo(
    val title: String,
    val filePath: String,
    val durationMs: Long,
    val fileSize: Long,
    val format: AudioFormat,
    val sampleRate: Int,
    val bitrate: Int,
    val createdAt: Long
)

class AudioImporter(
    private val context: Context,
    private val storageManager: StorageManager
) {
    private val TAG = "AudioImporter"

    /**
     * Imports an audio file from a Content URI (from document picker or external app)
     * into OyeSuno's managed storage.
     */
    suspend fun importFromUri(uri: Uri): ImportedAudioInfo? = withContext(Dispatchers.IO) {
        try {
            var fileName = "Imported_${System.currentTimeMillis()}"
            var fileSize = 0L

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }

            val targetDir = storageManager.getRecordingDirectory()
            val ext = if (fileName.contains(".")) fileName.substringAfterLast(".") else "m4a"
            val baseName = if (fileName.contains(".")) fileName.substringBeforeLast(".") else fileName
            val destFile = File(targetDir, "${baseName}_${System.currentTimeMillis()}.$ext")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            fileSize = destFile.length()

            // Extract metadata via MediaMetadataRetriever
            val retriever = MediaMetadataRetriever()
            var durationMs = 0L
            var sampleRate = 48000
            var bitrate = 192000

            try {
                retriever.setDataSource(destFile.absolutePath)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 192000
                sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 48000
            } catch (e: Exception) {
                Log.w(TAG, "Could not extract full metadata: ${e.message}")
            } finally {
                try { retriever.release() } catch (e: Exception) { /* ignore */ }
            }

            val format = when (ext.lowercase()) {
                "opus", "ogg" -> AudioFormat.OPUS
                "wav" -> AudioFormat.WAV
                else -> AudioFormat.M4A_AAC
            }

            ImportedAudioInfo(
                title = baseName.replace("_", " "),
                filePath = destFile.absolutePath,
                durationMs = durationMs,
                fileSize = fileSize,
                format = format,
                sampleRate = sampleRate,
                bitrate = bitrate,
                createdAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import audio from $uri", e)
            null
        }
    }

    /**
     * Scans device for call recordings and voice recordings from MediaStore.
     * Does NOT import music files — only recordings and call-related audio.
     */
    suspend fun scanDeviceRecordings(): List<ImportedAudioInfo> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ImportedAudioInfo>()
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.RELATIVE_PATH
            )

            // Only query recordings (not music) — IS_RECORDING=1 captures voice recorder files
            val selection = "${MediaStore.Audio.Media.IS_RECORDING} = 1"
            val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)

                var count = 0
                while (cursor.moveToNext() && count < 200) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Audio $id"
                    val duration = cursor.getLong(durationCol)
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateCol) * 1000L
                    val relativePath = cursor.getString(pathCol) ?: ""

                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    // Determine if this is a call recording based on path or filename
                    val nameLower = name.lowercase()
                    val pathLower = relativePath.lowercase()
                    val isCallRecording = pathLower.contains("call") ||
                            nameLower.contains("call") ||
                            pathLower.contains("recorder") ||
                            pathLower.contains("voip")

                    val baseName = name.substringBeforeLast(".")
                    val title = if (isCallRecording) {
                        "Call · $baseName"
                    } else {
                        "Recording · $baseName"
                    }

                    list.add(
                        ImportedAudioInfo(
                            title = title,
                            filePath = contentUri.toString(),
                            durationMs = duration,
                            fileSize = size,
                            format = if (name.endsWith(".wav", true)) AudioFormat.WAV else AudioFormat.M4A_AAC,
                            sampleRate = 48000,
                            bitrate = 192000,
                            createdAt = if (dateAdded > 0) dateAdded else System.currentTimeMillis()
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning device audio: ${e.message}", e)
        }
        list
    }
}
