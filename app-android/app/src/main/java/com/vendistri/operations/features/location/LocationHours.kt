package com.vendistri.operations.features.location

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vendistri.operations.design.AppColors
import com.vendistri.operations.design.LocalVendistriPalette
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class LocationHoursAvailability {
    Open,
    Closed
}

sealed class LocationHoursStatus {
    data object Unconfigured : LocationHoursStatus()
    data class Open(val closesAt: ZonedDateTime?) : LocationHoursStatus()
    data class Closed(val opensAt: ZonedDateTime?) : LocationHoursStatus()
}

data class LocationHoursDisplay(
    val text: String,
    val availability: LocationHoursAvailability
)

object LocationHours {
    fun display(
        location: AppLocation?,
        on: LocalDate = LocalDate.now(),
        availabilityTime: ZonedDateTime? = null,
        prefersClosedWarning: Boolean = false
    ): LocationHoursDisplay? {
        if (!isConfigured(location)) return null
        val currentStatus = status(location, availabilityTime)
        val text = when {
            prefersClosedWarning && currentStatus is LocationHoursStatus.Closed -> {
                closedWarningText(location, availabilityTime)
            }
            else -> displayText(location, on)
        } ?: return null
        return LocationHoursDisplay(
            text = text,
            availability = when (currentStatus) {
                is LocationHoursStatus.Open -> LocationHoursAvailability.Open
                LocationHoursStatus.Unconfigured,
                is LocationHoursStatus.Closed -> LocationHoursAvailability.Closed
            }
        )
    }

    fun status(location: AppLocation?, at: ZonedDateTime? = null): LocationHoursStatus {
        if (!isConfigured(location) || location == null) return LocationHoursStatus.Unconfigured
        val current = at?.withZoneSameInstant(zoneId(location)) ?: ZonedDateTime.now(zoneId(location))
        intervalContaining(location, current)?.let { return LocationHoursStatus.Open(closesAt = it.end) }
        return LocationHoursStatus.Closed(opensAt = nextOpening(location, current))
    }

    fun isOpenOrUnconfigured(location: AppLocation?, at: ZonedDateTime? = null): Boolean {
        return when (status(location, at)) {
            LocationHoursStatus.Unconfigured,
            is LocationHoursStatus.Open -> true
            is LocationHoursStatus.Closed -> false
        }
    }

    private fun closedWarningText(location: AppLocation?, at: ZonedDateTime? = null): String? {
        val resolvedLocation = location ?: return "Location closed"
        val closedStatus = status(location, at) as? LocationHoursStatus.Closed ?: return null
        val opensAt = closedStatus.opensAt ?: return "Location closed"
        val now = at?.withZoneSameInstant(zoneId(resolvedLocation))
            ?: ZonedDateTime.now(zoneId(resolvedLocation))
        return "Location closed, opens ${openingText(now, opensAt, resolvedLocation)}"
    }

    private fun displayText(location: AppLocation?, on: LocalDate): String? {
        val dayHours = dayHours(location, on) ?: return null
        if (dayHours.closed == true) return "Closed"
        val open = parseTime(dayHours.open) ?: return null
        val close = parseTime(dayHours.close) ?: return null
        return "${formatTime(open)}-${formatTime(close)}"
    }

    private fun isConfigured(location: AppLocation?): Boolean {
        val hours = location?.hours ?: return false
        return hours.values.any { it.closed == false && parseTime(it.open) != null && parseTime(it.close) != null }
    }

    private fun dayHours(location: AppLocation?, on: LocalDate): LocationDayHours? {
        val hours = location?.hours ?: return null
        val dayName = on.dayOfWeek.name.lowercase(Locale.US)
        return hours.entries.firstOrNull { (key, value) ->
            normalizeDay(key) == dayName || normalizeDay(value.day.orEmpty()) == dayName
        }?.value
    }

    private fun zoneId(location: AppLocation): ZoneId {
        return try {
            location.timeZone?.takeIf { it.isNotBlank() }?.let(ZoneId::of) ?: ZoneId.systemDefault()
        } catch (_: Exception) {
            ZoneId.systemDefault()
        }
    }

    private fun intervalContaining(location: AppLocation, at: ZonedDateTime): OpeningInterval? {
        return listOf(at.toLocalDate().minusDays(1), at.toLocalDate())
            .asSequence()
            .mapNotNull { openingInterval(location, it) }
            .firstOrNull { !at.isBefore(it.start) && at.isBefore(it.end) }
    }

    private fun nextOpening(location: AppLocation, after: ZonedDateTime): ZonedDateTime? {
        for (offset in 0..7) {
            val interval = openingInterval(location, after.toLocalDate().plusDays(offset.toLong())) ?: continue
            if (interval.start.isAfter(after)) return interval.start
        }
        return null
    }

    private fun openingInterval(location: AppLocation, date: LocalDate): OpeningInterval? {
        val dayHours = dayHours(location, date) ?: return null
        if (dayHours.closed == true) return null
        val open = parseTime(dayHours.open) ?: return null
        val close = parseTime(dayHours.close) ?: return null
        val zoneId = zoneId(location)
        val start = ZonedDateTime.of(date, open, zoneId)
        var end = ZonedDateTime.of(date, close, zoneId)
        if (!end.isAfter(start)) {
            end = end.plusDays(1)
        }
        return OpeningInterval(start = start, end = end)
    }

    private fun parseTime(value: String?): LocalTime? {
        if (value.isNullOrBlank()) return null
        return try {
            LocalTime.parse(value)
        } catch (_: Exception) {
            null
        }
    }

    private fun formatTime(time: LocalTime): String {
        return time.format(DateTimeFormatter.ofPattern(if (time.minute == 0) "ha" else "h:mma", Locale.US))
    }

    private fun relativeDuration(start: ZonedDateTime, end: ZonedDateTime): String {
        val minutes = maxOf(1, kotlin.math.ceil(Duration.between(start, end).seconds / 60.0).toInt())
        if (minutes < 60) return "${minutes}m"
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        return if (remainingMinutes == 0) "${hours}h" else "${hours}h ${remainingMinutes}m"
    }

    private fun openingText(start: ZonedDateTime, end: ZonedDateTime, location: AppLocation): String {
        if (Duration.between(start, end).seconds <= 24 * 60 * 60) {
            return "in ${relativeDuration(start, end)}"
        }
        val localEnd = end.withZoneSameInstant(zoneId(location))
        val weekday = localEnd.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.US)
        return "$weekday ${formatTime(localEnd.toLocalTime())}"
    }

    private fun normalizeDay(value: String): String {
        val lettersOnly = value.lowercase(Locale.US)
            .filter { it.isLetter() }
            .replace("weekday", "")
        return when {
            lettersOnly.startsWith("mon") -> "monday"
            lettersOnly.startsWith("tue") -> "tuesday"
            lettersOnly.startsWith("wed") -> "wednesday"
            lettersOnly.startsWith("thu") -> "thursday"
            lettersOnly.startsWith("fri") -> "friday"
            lettersOnly.startsWith("sat") -> "saturday"
            lettersOnly.startsWith("sun") -> "sunday"
            else -> lettersOnly
        }
    }

    private data class OpeningInterval(
        val start: ZonedDateTime,
        val end: ZonedDateTime
    )
}

@Composable
internal fun LocationHoursLabel(display: LocationHoursDisplay, modifier: Modifier = Modifier) {
    val palette = LocalVendistriPalette.current
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(7.dp),
            shape = CircleShape,
            color = if (display.availability == LocationHoursAvailability.Open) AppColors.statusDone else AppColors.statusError
        ) {}
        Text(
            text = display.text,
            color = palette.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
