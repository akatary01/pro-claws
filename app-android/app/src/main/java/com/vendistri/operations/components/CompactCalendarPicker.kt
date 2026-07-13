package com.vendistri.operations.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vendistri.operations.design.AppColors
import com.vendistri.operations.design.LocalVendistriPalette
import com.vendistri.operations.design.LocalVendistriResponsiveLayout
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class CalendarPickerMode {
    Days,
    Months,
    Years
}

@Composable
fun CompactCalendarPicker(
    selectedDate: LocalDate,
    visibleMonth: YearMonth,
    onDateSelected: (LocalDate) -> Unit,
    onVisibleMonthChanged: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    minimumDate: LocalDate? = null,
    maximumDate: LocalDate? = null
) {
    val palette = LocalVendistriPalette.current
    val responsiveLayout = LocalVendistriResponsiveLayout.current
    var mode by remember { mutableStateOf(CalendarPickerMode.Days) }
    val month = visibleMonth
    val first = month.atDay(1)
    val gridStart = first.minusDays((first.dayOfWeek.value % 7).toLong())
    Surface(
        modifier = modifier.width(responsiveLayout.calendarWidth),
        shape = RoundedCornerShape(18.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border.copy(alpha = 0.65f)),
        shadowElevation = 16.dp
    ) {
        Column(modifier = Modifier.padding(5.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            CalendarHeader(
                month = month,
                mode = mode,
                onPrevious = {
                    when (mode) {
                        CalendarPickerMode.Days -> onVisibleMonthChanged(month.minusMonths(1))
                        CalendarPickerMode.Months -> onVisibleMonthChanged(month.minusYears(1))
                        CalendarPickerMode.Years -> onVisibleMonthChanged(month.minusYears(8))
                    }
                },
                onNext = {
                    when (mode) {
                        CalendarPickerMode.Days -> onVisibleMonthChanged(month.plusMonths(1))
                        CalendarPickerMode.Months -> onVisibleMonthChanged(month.plusYears(1))
                        CalendarPickerMode.Years -> onVisibleMonthChanged(month.plusYears(8))
                    }
                },
                onTitleClick = {
                    mode = when (mode) {
                        CalendarPickerMode.Days -> CalendarPickerMode.Months
                        CalendarPickerMode.Months -> CalendarPickerMode.Days
                        CalendarPickerMode.Years -> CalendarPickerMode.Months
                    }
                }
            )

            when (mode) {
                CalendarPickerMode.Days -> CalendarDaysGrid(
                    selectedDate = selectedDate,
                    month = month,
                    gridStart = gridStart,
                    onDateSelected = onDateSelected,
                    minimumDate = minimumDate,
                    maximumDate = maximumDate,
                    dayRowHeight = responsiveLayout.calendarDayRowHeight
                )
                CalendarPickerMode.Months -> CalendarMonthsGrid(
                    month = month,
                    onMonthSelected = {
                        onVisibleMonthChanged(it)
                        mode = CalendarPickerMode.Days
                    },
                    onChangeYear = { mode = CalendarPickerMode.Years }
                )
                CalendarPickerMode.Years -> CalendarYearsGrid(
                    month = month,
                    onYearSelected = {
                        onVisibleMonthChanged(YearMonth.of(it, month.monthValue))
                        mode = CalendarPickerMode.Months
                    }
                )
            }
            CalendarFooter(
                selectedDate = selectedDate,
                onToday = { onDateSelected(LocalDate.now()) },
                isTodayEnabled = LocalDate.now().isSelectable(minimumDate, maximumDate)
            )
        }
    }
}

@Composable
fun CompactCalendarDateField(
    date: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    minimumDate: LocalDate? = null,
    maximumDate: LocalDate? = null
) {
    var isOpen by remember { mutableStateOf(false) }
    var visibleMonth by remember(date) { mutableStateOf(YearMonth.from(date)) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        label?.let {
            Text(it, color = LocalVendistriPalette.current.textSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
        Surface(
            onClick = { isOpen = !isOpen },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = LocalVendistriPalette.current.surface,
            border = BorderStroke(1.dp, LocalVendistriPalette.current.border)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(date.format(dateFieldFormatter), color = LocalVendistriPalette.current.textPrimary, fontWeight = FontWeight.SemiBold)
                Text("▦", color = AppColors.vendBlue, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        if (isOpen) {
            CompactCalendarPicker(
                selectedDate = date,
                visibleMonth = visibleMonth,
                onDateSelected = {
                    if (it.isSelectable(minimumDate, maximumDate)) {
                        onDateSelected(it)
                        visibleMonth = YearMonth.from(it)
                        isOpen = false
                    }
                },
                onVisibleMonthChanged = { visibleMonth = it },
                minimumDate = minimumDate,
                maximumDate = maximumDate
            )
        }
    }
}

@Composable
private fun CalendarHeader(
    month: YearMonth,
    mode: CalendarPickerMode,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTitleClick: () -> Unit
) {
    val palette = LocalVendistriPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().height(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(onClick = onPrevious, color = Color.Transparent, modifier = Modifier.size(24.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text("<", color = palette.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
        Row(
            modifier = Modifier
                .height(24.dp)
                .clickable { onTitleClick() }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)), color = palette.textPrimary, fontWeight = FontWeight.Bold)
            Text(
                text = if (mode == CalendarPickerMode.Days) "⌄" else "^",
                color = palette.textPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        Surface(onClick = onNext, color = Color.Transparent, modifier = Modifier.size(24.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(">", color = palette.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CalendarDaysGrid(
    selectedDate: LocalDate,
    month: YearMonth,
    gridStart: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    minimumDate: LocalDate?,
    maximumDate: LocalDate?,
    dayRowHeight: androidx.compose.ui.unit.Dp
) {
    val palette = LocalVendistriPalette.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("S", "M", "T", "W", "T", "F", "S").forEach {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(it, color = palette.textSecondary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    repeat(6) { week ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(7) { dayOffset ->
                val date = gridStart.plusDays((week * 7 + dayOffset).toLong())
                val isSelected = date == selectedDate
                val isEnabled = date.isSelectable(minimumDate, maximumDate)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = isEnabled) { onDateSelected(date) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) AppColors.vendBlue else Color.Transparent
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(dayRowHeight)
                    ) {
                        Text(
                            date.dayOfMonth.toString(),
                            color = when {
                                isSelected -> Color.White
                                !isEnabled -> palette.textSecondary.copy(alpha = 0.28f)
                                YearMonth.from(date) != month -> palette.textSecondary
                                else -> palette.textPrimary
                            },
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarMonthsGrid(
    month: YearMonth,
    onMonthSelected: (YearMonth) -> Unit,
    onChangeYear: () -> Unit
) {
    val palette = LocalVendistriPalette.current
    Text(
        text = "Change year",
        color = palette.textSecondary,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp)
            .clickable { onChangeYear() }
    )
    val months = java.time.Month.entries
    repeat(4) { row ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { column ->
                val calendarMonth = months[row * 3 + column]
                val candidate = YearMonth.of(month.year, calendarMonth)
                val isSelected = candidate == month
                Surface(
                    onClick = { onMonthSelected(candidate) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) AppColors.vendBlue else Color.Transparent
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.height(46.dp)) {
                        Text(
                            calendarMonth.getDisplayName(TextStyle.SHORT, Locale.US),
                            color = if (isSelected) Color.White else palette.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarYearsGrid(month: YearMonth, onYearSelected: (Int) -> Unit) {
    val palette = LocalVendistriPalette.current
    val startYear = month.year - 3
    repeat(4) { row ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(2) { column ->
                val year = startYear + row * 2 + column
                val isSelected = year == month.year
                Surface(
                    onClick = { onYearSelected(year) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) palette.border.copy(alpha = 0.35f) else Color.Transparent
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.height(42.dp)) {
                        Text(
                            year.toString(),
                            color = palette.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarFooter(selectedDate: LocalDate, onToday: () -> Unit, isTodayEnabled: Boolean) {
    val palette = LocalVendistriPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(selectedDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)), color = palette.textSecondary)
        Box(
            modifier = Modifier
                .height(22.dp)
                .clickable { if (isTodayEnabled) onToday() }
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Today",
                color = if (isTodayEnabled) AppColors.vendBlue else palette.textSecondary.copy(alpha = 0.45f),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun LocalDate.isSelectable(minimumDate: LocalDate?, maximumDate: LocalDate?): Boolean {
    if (minimumDate != null && this < minimumDate) return false
    if (maximumDate != null && this > maximumDate) return false
    return true
}

private val dateFieldFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
