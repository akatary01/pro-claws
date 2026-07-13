package com.vendistri.operations.features.settings

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.DateTimeParseException
import java.util.Locale

object AppTimeFormatter {
    private val twelveHourFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val twentyFourHourFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val systemTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val mediumDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    fun timeString(
        value: String,
        preference: TimeFormatPreference,
        systemUses24Hour: Boolean? = null,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        return parseDateTime(value, zoneId)?.format(timeFormatter(preference, systemUses24Hour)) ?: value
    }

    fun dateTimeString(
        value: String,
        preference: TimeFormatPreference,
        systemUses24Hour: Boolean? = null,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val parsed = parseDateTime(value, zoneId) ?: return value
        return "${parsed.format(mediumDateFormatter)}, ${parsed.format(timeFormatter(preference, systemUses24Hour))}"
    }

    fun dateOrDateTimeString(
        value: String,
        preference: TimeFormatPreference,
        systemUses24Hour: Boolean? = null,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (!value.contains('T')) return value
        return dateTimeString(value, preference, systemUses24Hour, zoneId)
    }

    fun arrivalTime(
        afterSeconds: Double,
        preference: TimeFormatPreference,
        systemUses24Hour: Boolean? = null,
        from: LocalDateTime = LocalDateTime.now()
    ): String {
        return from
            .plusSeconds(afterSeconds.toLong())
            .format(timeFormatter(preference, systemUses24Hour))
    }

    private fun timeFormatter(
        preference: TimeFormatPreference,
        systemUses24Hour: Boolean?
    ): DateTimeFormatter {
        return when (preference) {
            TimeFormatPreference.System -> when (systemUses24Hour) {
                true -> twentyFourHourFormatter
                false -> twelveHourFormatter
                null -> systemTimeFormatter
            }
            TimeFormatPreference.TwelveHour -> twelveHourFormatter
            TimeFormatPreference.TwentyFourHour -> twentyFourHourFormatter
        }
    }

    private fun parseDateTime(value: String, zoneId: ZoneId): LocalDateTime? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        return tryParse { OffsetDateTime.parse(trimmed).atZoneSameInstant(zoneId).toLocalDateTime() }
            ?: tryParse { Instant.parse(trimmed).atZone(zoneId).toLocalDateTime() }
            ?: tryParse { LocalDateTime.parse(trimmed) }
            ?: tryParse { LocalDate.parse(trimmed).atStartOfDay() }
    }

    private fun <T> tryParse(block: () -> T): T? {
        return try {
            block()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
