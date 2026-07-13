package com.vendistri.operations.features.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class LocationHoursTest {
    @Test
    fun isOpenOrUnconfiguredReturnsTrueForMissingHours() {
        val location = location(hours = null)

        assertTrue(LocationHours.isOpenOrUnconfigured(location, at = easternTime(hour = 10)))
    }

    @Test
    fun isOpenOrUnconfiguredReturnsTrueInsideOpenInterval() {
        val location = location(
            hours = mapOf(
                "monday" to day("monday", open = "09:00", close = "17:00")
            )
        )

        assertTrue(LocationHours.isOpenOrUnconfigured(location, at = easternTime(hour = 10)))
    }

    @Test
    fun isOpenOrUnconfiguredReturnsFalseOutsideOpenInterval() {
        val location = location(
            hours = mapOf(
                "monday" to day("monday", open = "09:00", close = "17:00")
            )
        )

        assertFalse(LocationHours.isOpenOrUnconfigured(location, at = easternTime(hour = 20)))
    }

    @Test
    fun statusHandlesOvernightHoursFromPreviousDay() {
        val location = location(
            hours = mapOf(
                "monday" to day("monday", open = "22:00", close = "02:00")
            )
        )
        val tuesdayAtOne = ZonedDateTime.of(2026, 7, 7, 1, 0, 0, 0, ZoneId.of("America/New_York"))

        assertTrue(LocationHours.status(location, at = tuesdayAtOne) is LocationHoursStatus.Open)
    }

    private fun location(hours: Map<String, LocationDayHours>?): AppLocation {
        return AppLocation(
            id = "location-1",
            name = "Downtown",
            timeZone = "America/New_York",
            address = null,
            hours = hours,
            defaultAssigneeId = null,
            discontinued = false
        )
    }

    private fun day(day: String, open: String, close: String): LocationDayHours {
        return LocationDayHours(day = day, closed = false, open = open, close = close)
    }

    private fun easternTime(hour: Int): ZonedDateTime {
        return ZonedDateTime.of(2026, 7, 6, hour, 0, 0, 0, ZoneId.of("America/New_York"))
    }
}
