package com.whisperlm.app.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtils {

    private val dateTimeFormatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    private val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun formatDateTime(epochMs: Long): String =
        dateTimeFormatter.format(Date(epochMs))

    fun formatDate(epochMs: Long): String =
        dateFormatter.format(Date(epochMs))

    /** Format milliseconds as mm:ss or hh:mm:ss */
    fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    /** Format a segment timestamp as [mm:ss] */
    fun formatTimestamp(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}
