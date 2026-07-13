package com.vendistri.operations.features.tasks

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun panelDateTitle(tab: TasksHomePanelTab, date: LocalDate): String {
    return when (tab) {
        TasksHomePanelTab.Overview -> "Show Week Summary"
        TasksHomePanelTab.Tasks -> date.format(TaskDateFormatters.abbreviatedWeekdayShortDay)
        TasksHomePanelTab.CompletedToday -> date.format(TaskDateFormatters.weekdayShortDay)
    }
}

internal fun locationSummaryText(locationGroup: TaskLocationGroup): String {
    val parts = buildList {
        add("${locationGroup.machineGroups.size} ${if (locationGroup.machineGroups.size == 1) "machine" else "machines"}")
        if (locationGroup.cancelledCount > 0) add("${locationGroup.cancelledCount} canceled")
        if (locationGroup.net > 0) add("$${money(locationGroup.net)} net")
    }
    return parts.joinToString(" - ")
}

fun money(value: Double): String = String.format(Locale.US, "%,.2f", value)

fun oneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)

fun signedQuantity(value: Int): String = if (value > 0) "+$value" else value.toString()

fun formatDuration(rawMinutes: Double): String {
    val minutes = rawMinutes.toInt().coerceAtLeast(0)
    val hours = minutes / 60
    val remainder = minutes % 60
    return if (hours > 0) "${hours}h ${remainder}m" else "${minutes}m"
}

fun formatTaskDuration(rawSeconds: Double): String {
    val totalSeconds = rawSeconds.toInt().coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

object TaskDateFormatters {
    val shortDay: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    val mediumDate: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    val weekdayShortDay: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)
    val abbreviatedWeekdayShortDay: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
}
