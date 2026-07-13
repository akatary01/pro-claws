package com.vendistri.operations.features.tasks.overview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vendistri.operations.R
import com.vendistri.operations.components.BackButton
import com.vendistri.operations.components.CompactCalendarPicker
import com.vendistri.operations.components.RevenueChip
import com.vendistri.operations.components.SkeletonLine
import com.vendistri.operations.components.SkeletonList
import com.vendistri.operations.design.AppColors
import com.vendistri.operations.design.LocalVendistriPalette
import com.vendistri.operations.features.location.AppLocation
import com.vendistri.operations.features.location_contact.ContactVisibilityRules
import com.vendistri.operations.features.tasks.FinancialMetricBreakdownCell
import com.vendistri.operations.features.tasks.TaskFinancialBreakdownSummary
import com.vendistri.operations.features.tasks.TaskFinancialDisplayMode
import com.vendistri.operations.features.tasks.TaskFinancialHelpers
import com.vendistri.operations.features.tasks.TaskGroupingHelpers
import com.vendistri.operations.features.tasks.TaskLocationGroup
import com.vendistri.operations.features.tasks.TaskCardActions
import com.vendistri.operations.features.tasks.TaskPanelVisibility
import com.vendistri.operations.features.tasks.VendiTask
import com.vendistri.operations.features.tasks.TaskLocationCard
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskRollupMetricsRow
import com.vendistri.operations.features.tasks.formatDuration
import com.vendistri.operations.features.tasks.money
import com.vendistri.operations.features.tasks.oneDecimal
import com.vendistri.operations.features.tasks.actions.TaskActionKind
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun OverviewPanelView(
    tasks: List<VendiTask>,
    locationsById: Map<String, AppLocation>,
    selectedLocationId: String?,
    autoCalcCommission: Boolean,
    initialDate: LocalDate,
    isLoading: Boolean,
    isRefreshing: Boolean,
    errorMessage: String?,
    onDateVisible: (LocalDate) -> Unit,
    onApplySharedNotes: suspend (List<String>, String?) -> Boolean,
    onClose: () -> Unit,
    useContactVisibility: Boolean = false,
    modifier: Modifier = Modifier
) {
    var selectedDate by remember { mutableStateOf(initialDate) }
    var calendarMonth by remember { mutableStateOf(YearMonth.from(initialDate)) }
    var showsWeekSummary by remember { mutableStateOf(false) }
    var showsCalendar by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(initialDate) {
        selectedDate = initialDate
        calendarMonth = YearMonth.from(initialDate)
    }
    androidx.compose.runtime.LaunchedEffect(selectedDate) {
        onDateVisible(selectedDate)
    }
    val expandedLocationIds = remember(selectedDate, selectedLocationId) { mutableStateMapOf<String, Boolean>() }
    val readOnlyTaskActions = remember { TaskCardActions.readOnly() }
    val sourceTasks = remember(tasks, selectedLocationId) {
        selectedLocationId?.let { id -> tasks.filter { it.location == id } } ?: tasks
    }.let { panelTasks ->
        if (useContactVisibility) ContactVisibilityRules.visibleTasks(panelTasks) else panelTasks
    }
    val weekTasks = TaskPanelVisibility.finalTasksForWeek(sourceTasks, selectedDate)
    val dayTasks = TaskPanelVisibility.finalTasksForDate(sourceTasks, selectedDate)
    val dayGroups = TaskGroupingHelpers.groupByLocation(dayTasks, lookupTasks = tasks)
    val weekFinancials = TaskFinancialHelpers.sumTaskFinancials(weekTasks)
    val dayFinancials = TaskFinancialHelpers.sumTaskFinancials(dayTasks)
    val isPanelLoading = isLoading || isRefreshing

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OverviewHeader(
                amount = if (useContactVisibility) weekFinancials.commission else weekFinancials.gross,
                onClose = onClose,
                onCalendarClick = { showsCalendar = !showsCalendar }
            )

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            if (showsWeekSummary) {
                if (isPanelLoading) {
                    OverviewWeekSummarySkeleton()
                } else {
                    OverviewMetricsGrid(
                        tasks = weekTasks,
                        thirdMetric = "Tasks" to weekTasks.size.toString(),
                        locationsById = locationsById,
                        useContactVisibility = useContactVisibility
                    )
                    WeeklySummaryFooterRow(
                        tasks = weekTasks,
                        showMetrics = !useContactVisibility || weekTasks.any { task ->
                            ContactVisibilityRules.canSeeTaskMetrics(task.location?.let(locationsById::get))
                        },
                        onHide = { showsWeekSummary = false }
                    )
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    WeekSummaryToggle(
                        isExpanded = false,
                        onClick = { showsWeekSummary = true }
                    )
                }
            }

            WeekSelector(
                selectedDate = selectedDate,
                onDateSelected = { selectedDate = it }
            )

            if (isPanelLoading) {
                OverviewDayMetricsSkeleton()
                OverviewLocationSkeletonList()
            } else {
                OverviewMetricsGrid(
                    tasks = dayTasks,
                    locationsById = locationsById,
                    thirdMetric = if (useContactVisibility) {
                        "Tasks" to dayTasks.size.toString()
                    } else {
                        "Gross Revenue" to "$ ${money(dayFinancials.gross)}"
                    },
                    useContactVisibility = useContactVisibility
                )
                OverviewActivityRow(
                    tasks = dayTasks,
                    showMetrics = !useContactVisibility || dayTasks.any { task ->
                        ContactVisibilityRules.canSeeTaskMetrics(task.location?.let(locationsById::get))
                    }
                )

                if (dayGroups.isEmpty()) {
                    EmptyOverviewText("No completed work for this day")
                } else {
                    dayGroups.forEach { group ->
                        TaskLocationCard(
                            locationGroup = group,
                            isExpanded = expandedLocationIds[group.id] ?: false,
                            showCompletedMetrics = true,
                            onToggle = { expandedLocationIds[group.id] = !(expandedLocationIds[group.id] ?: false) },
                            onBulkTaskAction = { _, _ -> },
                            onApplySharedNotes = onApplySharedNotes,
                            pendingMutationTaskIds = emptySet(),
                            taskActions = readOnlyTaskActions,
                            showTaskMetrics = !useContactVisibility || ContactVisibilityRules.canSeeTaskMetrics(locationsById[group.id]),
                            financialDisplay = if (useContactVisibility) {
                                ContactVisibilityRules.financialDisplay(locationsById[group.id])
                            } else {
                                TaskFinancialDisplayMode.Full
                            },
                            appLocation = locationsById[group.id],
                            autoCalcCommission = autoCalcCommission
                        )
                    }
                }
            }
        }

        if (showsCalendar) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { showsCalendar = false }
            )
            CompactCalendarPicker(
                selectedDate = selectedDate,
                visibleMonth = calendarMonth,
                onDateSelected = {
                    selectedDate = it
                    calendarMonth = YearMonth.from(it)
                    showsCalendar = false
                },
                onVisibleMonthChanged = { calendarMonth = it },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 20.dp)
                    .offset(y = 54.dp)
            )
        }
    }
}

@Composable
private fun OverviewHeader(
    amount: Double,
    onClose: () -> Unit,
    onCalendarClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        RevenueChip(amount = amount)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackButton(onClick = onClose)
            Surface(onClick = onCalendarClick, color = Color.Transparent, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_calendar_grid),
                        contentDescription = "Calendar",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekSummaryToggle(isExpanded: Boolean, onClick: () -> Unit) {
    Text(
        text = if (isExpanded) "Hide Week Summary" else "Show Week Summary",
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        color = AppColors.vendBlue,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        softWrap = false
    )
}

@Composable
private fun WeeklySummaryFooterRow(
    tasks: List<VendiTask>,
    showMetrics: Boolean = true,
    onHide: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showMetrics) {
            OverviewMetricCell(
                label = "Distance",
                value = oneDecimal(TaskGroupingHelpers.totalDistanceMiles(tasks))
            )
            OverviewMetricCell(
                label = "Time",
                value = formatDuration(TaskGroupingHelpers.totalDurationMinutes(tasks))
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopStart) {
            WeekSummaryToggle(isExpanded = true, onClick = onHide)
        }
    }
}

@Composable
private fun OverviewWeekSummarySkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OverviewMetricSkeletonRow()
        OverviewMetricSkeletonRow()
        OverviewMetricSkeletonRow()
    }
}

@Composable
private fun WeekSelector(selectedDate: LocalDate, onDateSelected: (LocalDate) -> Unit) {
    val palette = LocalVendistriPalette.current
    val startOfWeek = selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong())
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = palette.surfaceVariant,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(7) { offset ->
                val day = startOfWeek.plusDays(offset.toLong())
                val isSelected = day == selectedDate
                Surface(
                    onClick = { onDateSelected(day) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) AppColors.vendBlue else Color.Transparent
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US),
                            color = if (isSelected) Color.White else palette.textSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = day.dayOfMonth.toString(),
                            color = if (isSelected) Color.White else palette.textSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewDayMetricsSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OverviewMetricSkeletonRow()
        OverviewMetricSkeletonRow()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SkeletonLine(width = 76.dp, height = 15.dp)
            SkeletonLine(width = 70.dp, height = 15.dp)
            Spacer(modifier = Modifier.weight(1f))
            SkeletonLine(width = 54.dp, height = 13.dp)
        }
    }
}

@Composable
private fun OverviewMetricSkeletonRow() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                SkeletonLine(width = 68.dp, height = 10.dp)
                SkeletonLine(width = 52.dp, height = 17.dp)
            }
        }
    }
}

@Composable
private fun OverviewLocationSkeletonList() {
    SkeletonList(rows = 2)
}

@Composable
private fun OverviewMetricsGrid(
    tasks: List<VendiTask>,
    thirdMetric: Pair<String, String>,
    locationsById: Map<String, AppLocation> = emptyMap(),
    useContactVisibility: Boolean = false
) {
    val financials = TaskFinancialHelpers.sumTaskFinancials(tasks)
    val financialSummary = remember(tasks) { TaskFinancialHelpers.breakdownSummary(tasks) }
    var showGrossBreakdown by remember { mutableStateOf(false) }
    var showCommissionBreakdown by remember { mutableStateOf(false) }
    val canShowFullFinancials = !useContactVisibility || tasks
        .mapNotNull { it.location }
        .distinct()
        .all { locationId ->
            ContactVisibilityRules.financialDisplay(locationsById[locationId]) == TaskFinancialDisplayMode.Full
        }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OverviewMetricsRow(
            listOf(
                "Locations" to TaskGroupingHelpers.groupByLocation(tasks).size.toString(),
                "Machines" to TaskGroupingHelpers.groupByMachine(tasks).size.toString(),
                thirdMetric
            ),
            financialSummary = financialSummary,
            showGrossBreakdown = showGrossBreakdown,
            showCommissionBreakdown = showCommissionBreakdown,
            onGrossToggle = { showGrossBreakdown = !showGrossBreakdown },
            onCommissionToggle = { showCommissionBreakdown = !showCommissionBreakdown }
        )
        OverviewMetricsRow(
            if (useContactVisibility) {
                buildList {
                    add("Commission" to "$ ${money(financials.commission)}")
                    if (canShowFullFinancials) {
                        add("Gross" to "$ ${money(financials.gross)}")
                        add("Refunds" to "$ ${money(financials.refunds)}")
                        add("Net" to "$ ${money(financials.net)}")
                    }
                }
            } else {
                listOf(
                    "Refunds" to "$ ${money(financials.refunds)}",
                    "Commission" to "$ ${money(financials.commission)}",
                    "Net Revenue" to "$ ${money(financials.net)}"
                )
            },
            financialSummary = financialSummary,
            showGrossBreakdown = showGrossBreakdown,
            showCommissionBreakdown = showCommissionBreakdown,
            onGrossToggle = { showGrossBreakdown = !showGrossBreakdown },
            onCommissionToggle = { showCommissionBreakdown = !showCommissionBreakdown }
        )
    }
}

@Composable
private fun OverviewMetricsRow(
    metrics: List<Pair<String, String>>,
    financialSummary: TaskFinancialBreakdownSummary,
    showGrossBreakdown: Boolean,
    showCommissionBreakdown: Boolean,
    onGrossToggle: () -> Unit,
    onCommissionToggle: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        metrics.forEach { (label, value) ->
            val normalized = label.lowercase()
            when {
                normalized.contains("gross") -> FinancialMetricBreakdownCell(
                    title = label,
                    value = value,
                    summary = financialSummary,
                    isGross = true,
                    isExpanded = showGrossBreakdown,
                    onToggle = onGrossToggle,
                    modifier = Modifier.weight(1f)
                )
                normalized.contains("commission") -> FinancialMetricBreakdownCell(
                    title = label,
                    value = value,
                    summary = financialSummary,
                    isGross = false,
                    isExpanded = showCommissionBreakdown,
                    onToggle = onCommissionToggle,
                    modifier = Modifier.weight(1f)
                )
                else -> OverviewMetricCell(label = label, value = value)
            }
        }
    }
}

@Composable
private fun RowScope.OverviewMetricCell(label: String, value: String) {
    val palette = LocalVendistriPalette.current
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = palette.textSecondary, style = MaterialTheme.typography.bodySmall)
        Text(value, color = palette.textPrimary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OverviewActivityRow(
    tasks: List<VendiTask>,
    showsTaskCount: Boolean = true,
    showMetrics: Boolean = true
) {
    val palette = LocalVendistriPalette.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (showMetrics) {
            TaskRollupMetricsRow(
                durationMinutes = TaskGroupingHelpers.totalDurationMinutes(tasks),
                distanceMiles = TaskGroupingHelpers.totalDistanceMiles(tasks),
                spacing = 18.dp
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (showsTaskCount) {
            Text("${tasks.size} ${if (tasks.size == 1) "task" else "tasks"}", color = palette.textSecondary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun OverviewLocationRow(group: TaskLocationGroup) {
    val palette = LocalVendistriPalette.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = palette.surfaceVariant,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(group.name, color = palette.textPrimary, fontWeight = FontWeight.Bold)
            Text("$ ${money(group.net)}", color = palette.textPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyOverviewText(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 34.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}
