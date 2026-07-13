package com.vendistri.operations.features.location_contact

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vendistri.operations.design.LocalVendistriPalette
import java.time.DayOfWeek
import java.time.LocalDate

enum class ContactCommissionRange(val label: String) {
    Today("Today"),
    Yesterday("Yesterday"),
    ThisWeek("This Week"),
    LastWeek("Last Week"),
    Last2Weeks("Last 2 Weeks"),
    ThisMonth("This Month"),
    LastMonth("Last Month"),
    Last3Months("Last 3 Months"),
    Last6Months("Last 6 Months"),
    ThisYear("This Year"),
    LastYear("Last Year"),
    AllTime("All Time"),
    Custom("Custom");

    fun contains(date: LocalDate, today: LocalDate = LocalDate.now()): Boolean {
        val bounds = bounds(today) ?: return true
        return !date.isBefore(bounds.first) && !date.isAfter(bounds.second)
    }

    fun bounds(today: LocalDate = LocalDate.now()): Pair<LocalDate, LocalDate>? {
        val weekStart = today.minusDays((today.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
        return when (this) {
            Today -> today to today
            Yesterday -> today.minusDays(1) to today.minusDays(1)
            ThisWeek -> weekStart to today
            LastWeek -> weekStart.minusWeeks(1) to weekStart.minusDays(1)
            Last2Weeks -> today.minusDays(13) to today
            ThisMonth -> today.withDayOfMonth(1) to today
            LastMonth -> today.minusMonths(1).withDayOfMonth(1) to today.withDayOfMonth(1).minusDays(1)
            Last3Months -> today.minusMonths(3) to today
            Last6Months -> today.minusMonths(6) to today
            ThisYear -> today.withDayOfYear(1) to today
            LastYear -> today.minusYears(1).withDayOfYear(1) to today.withDayOfYear(1).minusDays(1)
            AllTime, Custom -> null
        }
    }
}

@Composable
fun ContactCommissionRangePicker(
    selected: ContactCommissionRange,
    onSelected: (ContactCommissionRange) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalVendistriPalette.current
    var isOpen by remember { mutableStateOf(false) }
    Surface(
        onClick = { isOpen = true },
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                selected.label,
                modifier = Modifier.weight(1f),
                color = palette.textPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(if (isOpen) "⌃" else "⌄", color = palette.textSecondary, fontWeight = FontWeight.SemiBold)
        }
        DropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }, modifier = Modifier.fillMaxWidth(0.78f)) {
            ContactCommissionRange.entries.forEach { range ->
                DropdownMenuItem(
                    text = {
                        Text(
                            range.label,
                            color = palette.textPrimary,
                            fontWeight = if (range == selected) FontWeight.Bold else FontWeight.SemiBold
                        )
                    },
                    onClick = {
                        onSelected(range)
                        isOpen = false
                    }
                )
            }
        }
    }
}
