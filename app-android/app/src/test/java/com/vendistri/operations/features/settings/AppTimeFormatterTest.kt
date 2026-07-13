package com.vendistri.operations.features.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class AppTimeFormatterTest {
    private val baseTime = LocalDateTime.of(2026, 7, 6, 13, 5)

    @Test
    fun `arrivalTime uses phone 12-hour format for system preference`() {
        val text = AppTimeFormatter.arrivalTime(
            afterSeconds = 0.0,
            preference = TimeFormatPreference.System,
            systemUses24Hour = false,
            from = baseTime
        )

        assertEquals("1:05 PM", text)
    }

    @Test
    fun `arrivalTime uses phone 24-hour format for system preference`() {
        val text = AppTimeFormatter.arrivalTime(
            afterSeconds = 0.0,
            preference = TimeFormatPreference.System,
            systemUses24Hour = true,
            from = baseTime
        )

        assertEquals("13:05", text)
    }

    @Test
    fun `arrivalTime explicit preferences override phone format`() {
        assertEquals(
            "1:05 PM",
            AppTimeFormatter.arrivalTime(
                afterSeconds = 0.0,
                preference = TimeFormatPreference.TwelveHour,
                systemUses24Hour = true,
                from = baseTime
            )
        )
        assertEquals(
            "13:05",
            AppTimeFormatter.arrivalTime(
                afterSeconds = 0.0,
                preference = TimeFormatPreference.TwentyFourHour,
                systemUses24Hour = false,
                from = baseTime
            )
        )
    }
}
