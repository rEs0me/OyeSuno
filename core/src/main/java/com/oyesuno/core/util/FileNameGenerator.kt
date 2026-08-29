package com.oyesuno.core.util

import com.oyesuno.core.model.RecordingTriggerType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileNameGenerator {

    /**
     * Generates a descriptive recording title with:
     * Trigger Prefix + Day of week + Full date + 12-hour precise timestamp
     * Example: "Recording · Monday, 24 Aug 2026, 03:15 PM"
     */
    fun generateDefaultName(
        date: Date = Date(),
        triggerType: RecordingTriggerType = RecordingTriggerType.MANUAL
    ): String {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        val dateString = sdf.format(date)

        val prefix = when (triggerType) {
            RecordingTriggerType.CALL -> "Call"
            RecordingTriggerType.WAKE_WORD -> "Wake Word"
            RecordingTriggerType.SCHEDULE -> "Scheduled"
            RecordingTriggerType.TIMER -> "Timed"
            RecordingTriggerType.MANUAL -> "Recording"
        }

        return "$prefix · $dateString"
    }

    /**
     * Generates a filesystem-safe file name:
     * Example: "OyeSuno_20260824_154530.m4a"
     */
    fun generateFileSystemName(extension: String = "m4a"): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "OyeSuno_${sdf.format(Date())}.$extension"
    }

    /**
     * Generates a clean clip name derived from the original title:
     * Example: "Voice Memo · Monday... · Clip 01"
     */
    fun generateClipName(originalName: String, clipNumber: Int): String {
        val formattedNumber = String.format(Locale.getDefault(), "%02d", clipNumber)
        return "$originalName · Clip $formattedNumber"
    }
}
