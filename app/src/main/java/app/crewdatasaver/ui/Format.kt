package app.crewdatasaver.ui

import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun formatBytes(bytes: Long): String {
    val mb = bytes / 1_000_000.0
    return if (mb >= 1000) String.format(Locale.getDefault(), "%.2f GB", mb / 1000)
    else String.format(Locale.getDefault(), "%.1f MB", mb)
}

fun formatDateTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
