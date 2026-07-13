package com.vendistri.operations.features.tasks

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object TaskScheduleDate {
    private val backendFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun key(date: LocalDate): String {
        return date.format(backendFormatter)
    }

    fun parse(value: String?): LocalDate? {
        val datePrefix = value?.takeIf { it.length >= 10 }?.take(10) ?: return null
        return runCatching { LocalDate.parse(datePrefix, backendFormatter) }.getOrNull()
    }

    fun isSameDay(value: String?, date: LocalDate): Boolean {
        return parse(value) == date
    }
}

internal fun String?.toLocalDatePrefix(): LocalDate? {
    return TaskScheduleDate.parse(this)
}
