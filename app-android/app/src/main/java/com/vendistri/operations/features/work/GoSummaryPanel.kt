package com.vendistri.operations.features.work

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.vendistri.operations.R
import com.vendistri.operations.components.PrimaryActionButton
import com.vendistri.operations.components.RevenueChip
import com.vendistri.operations.components.RouteTravelSummaryRow
import com.vendistri.operations.components.SearchableDropdown
import com.vendistri.operations.components.SearchableDropdownOption
import com.vendistri.operations.design.AppColors
import com.vendistri.operations.design.LocalVendistriPalette
import com.vendistri.operations.features.location.AppLocation
import com.vendistri.operations.features.location.LocationHours
import com.vendistri.operations.features.location.LocationHoursLabel
import com.vendistri.operations.features.settings.AppTimeFormatter
import com.vendistri.operations.features.settings.TimeFormatPreference
import com.vendistri.operations.features.tasks.CollectionInputMode
import com.vendistri.operations.features.tasks.FinancialMetricBreakdownCell
import com.vendistri.operations.features.tasks.LocationStatusRows
import com.vendistri.operations.features.tasks.TaskCommissionCalculator
import com.vendistri.operations.features.tasks.TaskMachineAssigneeDisplay
import com.vendistri.operations.features.tasks.TaskMachineAssigneeHelper
import com.vendistri.operations.features.tasks.TaskMachineGroup
import com.vendistri.operations.features.tasks.TaskMachineVisitMetricFormatter
import com.vendistri.operations.features.tasks.TaskFinancialHelpers
import com.vendistri.operations.features.tasks.TaskGroupingHelpers
import com.vendistri.operations.features.tasks.TaskLocationGroup
import com.vendistri.operations.features.tasks.TaskStatusPresentation
import com.vendistri.operations.features.tasks.TaskStateHelpers
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskStatusHelpers
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.TaskTypeIcon
import com.vendistri.operations.features.tasks.VendiTask
import com.vendistri.operations.features.tasks.formatDuration
import com.vendistri.operations.features.tasks.money
import com.vendistri.operations.features.tasks.oneDecimal

private enum class GoSummaryTab {
    Summary,
    Route
}

@Composable
fun GoSummaryPanel(
    state: WorkUiState,
    currentUserId: String?,
    locationsById: Map<String, AppLocation>,
    timeFormatPreference: TimeFormatPreference,
    onStopSelected: (String) -> Unit,
    onStart: () -> Unit,
    onScopeChoiceSelected: (RouteStartScopeChoice) -> Unit,
    onDismissScopeChoice: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val plan = state.goPlan
    if (plan == null) {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!state.isLoading) NoRemainingWorkStatus()
            TextButton(onClick = onClose) { Text("Close") }
        }
        return
    }

    var selectedTab by remember { mutableStateOf(GoSummaryTab.Summary) }
    var expandedMetricIds by remember { mutableStateOf(emptySet<String>()) }
    var expandedRouteStopIds by remember { mutableStateOf(emptySet<String>()) }
    val selectedStop = state.selectedStop ?: plan.stops.firstOrNull { it.id == state.selectedStopId } ?: plan.stops.firstOrNull()
    val bodyScrollState = rememberScrollState()
    var hoursRefreshTick by remember { mutableStateOf(0L) }
    LaunchedEffect(plan) {
        while (true) {
            delay(30_000)
            hoursRefreshTick++
        }
    }
    val systemUses24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val hasRemainingWork = plan.tasks.any { TaskStateHelpers.isActionable(it.status) }
    val bodyModifier = Modifier
        .heightIn(max = 390.dp)
        .verticalScroll(bodyScrollState)

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 414.dp)
                .fillMaxWidth()
                .padding(top = 14.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)),
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GoSummaryTabs(selectedTab = selectedTab, onSelected = { selectedTab = it })
                GoSummaryMetrics(
                    summary = plan.summary,
                    tasks = plan.tasks,
                    expandedMetricIds = expandedMetricIds,
                    onToggleMetric = { expandedMetricIds = toggleSet(expandedMetricIds, it) }
                )

                Column(
                    modifier = bodyModifier,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (selectedTab == GoSummaryTab.Route) {
                        RouteTabContent(
                            plan = plan,
                            selectedStop = selectedStop,
                            routePreview = state.routePreview,
                            locationsById = locationsById,
                            timeFormatPreference = timeFormatPreference,
                            systemUses24Hour = systemUses24Hour,
                            expandedRouteStopIds = expandedRouteStopIds,
                            onToggleStop = { expandedRouteStopIds = toggleSet(expandedRouteStopIds, it) },
                            hoursRefreshTick = hoursRefreshTick
                        )
                    } else {
                        SummaryTabContent(
                            plan = plan,
                            selectedStop = selectedStop,
                            currentUserId = currentUserId,
                            routePreview = state.routePreview,
                            locationsById = locationsById,
                            timeFormatPreference = timeFormatPreference,
                            systemUses24Hour = systemUses24Hour,
                            expandedMetricIds = expandedMetricIds,
                            onToggleMetric = { expandedMetricIds = toggleSet(expandedMetricIds, it) },
                            onStopSelected = onStopSelected
                        )

                        if (!hasRemainingWork && !state.isLoading) {
                            NoRemainingWorkStatus()
                        }

                        state.errorMessage?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }

                        if (hasRemainingWork) {
                            PrimaryActionButton(
                                text = "Start Route",
                                onClick = onStart,
                                enabled = selectedStop != null && state.routeStartScopeDecision == null,
                                isLoading = state.isLoading,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                }
            }
        }

        RevenueChip(
            amount = plan.summary.gross,
            modifier = Modifier.offset(y = 0.dp)
        )
    }
}

@Composable
private fun NoRemainingWorkStatus() {
    val palette = LocalVendistriPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(modifier = Modifier.size(7.dp), shape = CircleShape, color = AppColors.done) {}
        Text(
            "No remaining work for today",
            color = palette.textPrimary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun GoSummaryTabs(
    selectedTab: GoSummaryTab,
    onSelected: (GoSummaryTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GoSummaryTabButton("Summary", GoSummaryTab.Summary, selectedTab, onSelected)
        GoSummaryTabButton("Route", GoSummaryTab.Route, selectedTab, onSelected)
    }
}

@Composable
private fun GoSummaryTabButton(
    title: String,
    tab: GoSummaryTab,
    selectedTab: GoSummaryTab,
    onSelected: (GoSummaryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = tab == selectedTab
    TextButton(
        onClick = { onSelected(tab) },
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
    ) {
        Text(
            title,
            color = if (isSelected) AppColors.vendBlue else AppColors.muted,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun GoSummaryMetrics(
    summary: GoSummary,
    tasks: List<com.vendistri.operations.features.tasks.VendiTask>,
    expandedMetricIds: Set<String>,
    onToggleMetric: (String) -> Unit
) {
    val breakdown = remember(tasks) { TaskFinancialHelpers.breakdownSummary(tasks) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GoMetric("Locations", summary.locations.toString(), Modifier.weight(1f))
            GoMetric("Machines", summary.machines.toString(), Modifier.weight(1f))
            GoMetric("Tasks", summary.tasks.toString(), Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GoMetric("Refunds", "$ ${money(summary.refunds)}", Modifier.weight(1f))
            FinancialMetricBreakdownCell(
                title = "Commission",
                value = "$ ${money(summary.commission)}",
                summary = breakdown,
                isGross = false,
                isExpanded = "summary-commission" in expandedMetricIds,
                onToggle = { onToggleMetric("summary-commission") },
                modifier = Modifier.weight(1f)
            )
            GoMetric("Net Revenue", "$ ${money(summary.net)}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun GoMetric(label: String, value: String, modifier: Modifier = Modifier) {
    val palette = LocalVendistriPalette.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = palette.textSecondary, style = MaterialTheme.typography.bodySmall)
        Text(value, color = palette.textPrimary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DetailMetric(label: String, value: String, modifier: Modifier = Modifier) {
    val palette = LocalVendistriPalette.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = palette.textSecondary, style = MaterialTheme.typography.bodySmall)
        Text(value, color = palette.textPrimary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SummaryTabContent(
    plan: GoPlan,
    selectedStop: GoStopPlan?,
    currentUserId: String?,
    routePreview: RoutePreview?,
    locationsById: Map<String, AppLocation>,
    timeFormatPreference: TimeFormatPreference,
    systemUses24Hour: Boolean,
    expandedMetricIds: Set<String>,
    onToggleMetric: (String) -> Unit,
    onStopSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GoStopDropdown(
            stops = plan.stops,
            selectedStop = selectedStop,
            suggestedStopId = plan.suggestedStopId,
            onStopSelected = onStopSelected
        )

        if (selectedStop == null) return@Column

        fullAddress(selectedStop)?.let {
            Text(it, color = AppColors.muted, style = MaterialTheme.typography.bodySmall)
        }

        LocationHours.display(
            location = locationsById[selectedStop.targetLocationId],
            prefersClosedWarning = true
        )?.let {
            LocationHoursLabel(display = it)
        }

        ownershipText(selectedStop, currentUserId)?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }

        SelectedLocationMetrics(
            stop = selectedStop,
            expandedMetricIds = expandedMetricIds,
            onToggleMetric = onToggleMetric
        )
        CurrentLocationRouteEstimate(
            routePreview = routePreview,
            timeFormatPreference = timeFormatPreference,
            systemUses24Hour = systemUses24Hour
        )
    }
}

@Composable
private fun GoStopDropdown(
    stops: List<GoStopPlan>,
    selectedStop: GoStopPlan?,
    suggestedStopId: String?,
    onStopSelected: (String) -> Unit
) {
    SearchableDropdown(
        allLabel = "No locations",
        options = stops.map { stop ->
            val isSuggested = stop.id == suggestedStopId
            SearchableDropdownOption(
                id = stop.id,
                title = stop.title,
                menuTitle = if (isSuggested) "(Suggested) ${stop.title}" else stop.title,
                subtitle = fullAddress(stop),
                searchText = listOfNotNull(stop.title, fullAddress(stop)).joinToString(" "),
                isSuggested = isSuggested
            )
        },
        selectedId = selectedStop?.id,
        onSelected = { stopId -> stopId?.let(onStopSelected) },
        includesAllOption = false
    )
}

@Composable
private fun SelectedLocationMetrics(
    stop: GoStopPlan,
    expandedMetricIds: Set<String>,
    onToggleMetric: (String) -> Unit
) {
    val totals = TaskFinancialHelpers.sumTaskFinancials(stop.tasks)
    val breakdown = remember(stop.tasks) { TaskFinancialHelpers.breakdownSummary(stop.tasks) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FinancialMetricBreakdownCell(
                title = "Gross\nRevenue",
                value = "$ ${money(totals.gross)}",
                summary = breakdown,
                isGross = true,
                isExpanded = "stop-${stop.id}-gross" in expandedMetricIds,
                onToggle = { onToggleMetric("stop-${stop.id}-gross") },
                modifier = Modifier.weight(1f)
            )
            DetailMetric("Machines", stop.machineCount.toString(), Modifier.weight(1f))
            DetailMetric("Tasks", stop.taskCount.toString(), Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailMetric("Refunds", "$ ${money(totals.refunds)}", Modifier.weight(1f))
            FinancialMetricBreakdownCell(
                title = "Commission",
                value = "$ ${money(totals.commission)}",
                summary = breakdown,
                isGross = false,
                isExpanded = "stop-${stop.id}-commission" in expandedMetricIds,
                onToggle = { onToggleMetric("stop-${stop.id}-commission") },
                modifier = Modifier.weight(1f)
            )
            DetailMetric("Net", "$ ${money(totals.net)}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun CurrentLocationRouteEstimate(
    routePreview: RoutePreview?,
    timeFormatPreference: TimeFormatPreference,
    systemUses24Hour: Boolean
) {
    RouteTravelSummaryRow(
        durationText = routePreview?.let { formatDuration(it.expectedTravelSeconds.coerceAtLeast(0.0) / 60.0) } ?: "--",
        arrivalDistanceText = routePreview?.let {
            "${arrivalText(it.expectedTravelSeconds.coerceAtLeast(0.0), timeFormatPreference, systemUses24Hour)} • ${oneDecimal(it.distanceMiles.coerceAtLeast(0.0))} mi"
        } ?: "-- • -- mi",
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
    )
}

@Composable
private fun RouteTabContent(
    plan: GoPlan,
    selectedStop: GoStopPlan?,
    routePreview: RoutePreview?,
    locationsById: Map<String, AppLocation>,
    timeFormatPreference: TimeFormatPreference,
    systemUses24Hour: Boolean,
    expandedRouteStopIds: Set<String>,
    onToggleStop: (String) -> Unit,
    hoursRefreshTick: Long
) {
    val orderedStops = remember(plan.stops, selectedStop?.id) {
        orderedRouteStops(plan.stops, selectedStop)
    }
    val routePreviews = remember(orderedStops, routePreview) {
        buildRouteStopPreviews(orderedStops, routePreview)
    }
    val totalPreview = routePreviews.lastOrNull()?.let {
        RoutePreview(it.cumulativeDistanceMiles, it.cumulativeTravelSeconds)
    } ?: routePreview
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        totalPreview?.let {
            GoRoutePreview(
                preview = it,
                timeFormatPreference = timeFormatPreference,
                systemUses24Hour = systemUses24Hour
            )
        }
        routePreviews.forEach { preview ->
            RouteStopRow(
                preview = preview,
                location = locationsById[preview.stop.targetLocationId],
                timeFormatPreference = timeFormatPreference,
                systemUses24Hour = systemUses24Hour,
                isExpanded = preview.stop.id in expandedRouteStopIds,
                hoursRefreshTick = hoursRefreshTick,
                onToggle = {
                    onToggleStop(preview.stop.id)
                }
            )
        }
    }
}

@Composable
private fun RouteStopRow(
    preview: RouteStopPreview,
    location: AppLocation?,
    timeFormatPreference: TimeFormatPreference,
    systemUses24Hour: Boolean,
    isExpanded: Boolean,
    hoursRefreshTick: Long,
    onToggle: () -> Unit
) {
    val palette = LocalVendistriPalette.current
    val stop = preview.stop
    val statusItems = remember(stop.tasks) {
        LocationStatusRows.items(TaskStatusHelpers.statusCounts(stop.tasks))
    }
    val statusSummary = remember(statusItems) { LocationStatusRows.joinedStatusText(statusItems) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                preview.stopNumber.toString(),
                color = AppColors.vendBlue,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp)
            )
            Surface(modifier = Modifier.padding(top = 7.dp).size(8.dp), shape = CircleShape, color = AppColors.pending) {}
            Text(
                stop.title,
                modifier = Modifier.weight(1f),
                color = palette.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${formatDuration(preview.legTravelSeconds / 60.0)} ${if (isExpanded) "⌃" else "⌄"}",
                    color = palette.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                if (!isExpanded) {
                    Text(
                        text = "${arrivalText(preview.cumulativeTravelSeconds, timeFormatPreference, systemUses24Hour)} • ${oneDecimal(preview.legDistanceMiles)} mi",
                        color = AppColors.muted,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        if (isExpanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LocationHours.display(
                    location = location,
                    prefersClosedWarning = true,
                    availabilityTime = null
                )?.let { LocationHoursLabel(display = it, modifier = Modifier.weight(1f)) }
                Text(
                    text = "${arrivalText(preview.cumulativeTravelSeconds, timeFormatPreference, systemUses24Hour)} • ${oneDecimal(preview.legDistanceMiles)} mi",
                    color = AppColors.muted,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            if (fullAddress(stop) != null || statusSummary != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggle),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        fullAddress(stop).orEmpty(),
                        modifier = Modifier.weight(1f),
                        color = AppColors.muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (statusSummary != null) {
                        Text(
                            statusSummary,
                            color = AppColors.muted,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
            RouteStopExpandedDetails(stop = stop)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    }
}

@Composable
private fun RouteStopExpandedDetails(stop: GoStopPlan) {
    val totals = TaskFinancialHelpers.sumTaskFinancials(stop.tasks)
    val breakdown = remember(stop.tasks) { TaskFinancialHelpers.breakdownSummary(stop.tasks) }
    var isGrossExpanded by remember(stop.id) { mutableStateOf(false) }
    var isCommissionExpanded by remember(stop.id) { mutableStateOf(false) }
    val group = taskLocationGroup(stop)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RouteFinancialRow(
            label = "Gross Revenue ${if (isGrossExpanded) "⌃" else "⌄"}",
            value = "$ ${money(totals.gross)}",
            onLabelClick = { isGrossExpanded = !isGrossExpanded }
        )
        if (isGrossExpanded) {
            RouteFinancialBreakdownRow(
                leftLabel = "CASH $ ${money(breakdown.grossCash)}",
                rightLabel = "CARD $ ${money(breakdown.grossCard)}"
            )
        }
        RouteFinancialRow("Refunds", "$ ${money(totals.refunds)}")
        RouteFinancialRow(
            label = "Commission ${if (isCommissionExpanded) "⌃" else "⌄"}",
            value = "$ ${money(totals.commission)}",
            onLabelClick = { isCommissionExpanded = !isCommissionExpanded }
        )
        if (isCommissionExpanded && breakdown.hasCommissionBreakdown) {
            breakdown.commissionByPaymentType.forEach { line ->
                RouteFinancialRow(
                    label = line.label,
                    value = "$ ${money(line.amount)}"
                )
            }
        }
        RouteFinancialRow("Net Revenue", "$ ${money(totals.net)}")
        group.machineGroups.forEach { machine ->
            RouteMachineCard(machineGroup = machine)
        }
    }
}

@Composable
private fun RouteMachineCard(machineGroup: TaskMachineGroup) {
    val palette = LocalVendistriPalette.current
    val assigneeDisplay = TaskMachineAssigneeHelper.display(machineGroup.tasks)
    val assigneeSummary = (assigneeDisplay as? TaskMachineAssigneeDisplay.Summary)?.text
    val showPerTaskAssignee = assigneeDisplay is TaskMachineAssigneeDisplay.Mixed
    val visitMetricText = TaskMachineVisitMetricFormatter.visitMetricText(machineGroup.tasks)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                machineGroup.name,
                color = palette.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            if (assigneeSummary != null || visitMetricText != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        assigneeSummary.orEmpty(),
                        modifier = Modifier.weight(1f),
                        color = palette.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    visitMetricText?.let {
                        Text(
                            it,
                            color = palette.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                    }
                }
            }
            machineGroup.tasks.forEach { task ->
                RouteTaskRow(task = task, showAssignee = showPerTaskAssignee)
            }
        }
    }
}

@Composable
private fun RouteTaskRow(task: VendiTask, showAssignee: Boolean) {
    val palette = LocalVendistriPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TaskTypeIcon(task.type, modifier = Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    taskTypeLabel(task.type),
                    color = palette.textPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (showAssignee) {
                    TaskStateHelpers.assigneeLine(task)?.let {
                        Text(
                            it,
                            color = palette.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(7.dp),
                    shape = CircleShape,
                    color = TaskStatusPresentation.indicatorColor(task.status)
                ) {}
                Text(
                    TaskStatusPresentation.label(task.status),
                    color = palette.textPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        when (task.type) {
            TaskType.MachineCollection -> RouteCollectionFinancialRows(task)
            TaskType.MachineRefund -> {
                val financials = TaskFinancialHelpers.displayedFinancials(task)
                RouteFinancialRow("Refunds", "$ ${money(financials.refunds)}")
            }
            else -> Unit
        }
    }
}

@Composable
private fun RouteCollectionFinancialRows(task: VendiTask) {
    var isGrossExpanded by remember(task.id) { mutableStateOf(false) }
    val financials = TaskFinancialHelpers.displayedFinancials(task)
    RouteFinancialRow(
        label = "Gross ${if (isGrossExpanded) "⌃" else "⌄"}",
        value = "$ ${money(financials.gross)}",
        onLabelClick = { isGrossExpanded = !isGrossExpanded }
    )
    if (isGrossExpanded) {
        RouteFinancialBreakdownRow(
            leftLabel = if (task.collectionInputMode == CollectionInputMode.Credits) {
                "CASH CREDITS ${financials.grossCash.toInt()}"
            } else {
                "CASH $ ${money(financials.grossCash)}"
            },
            rightLabel = "CARD $ ${money(financials.grossCard)}"
        )
    }
    RouteFinancialRow("Refunds", "$ ${money(financials.refunds)}")
    RouteFinancialRow(
        label = "Commission",
        value = "$ ${money(financials.commission)}",
        labelDetail = TaskCommissionCalculator.commissionPercentText(
            gross = financials.gross,
            commission = financials.commission
        )?.let { "($it)" },
        valueDetail = task.commissionPaymentType?.label
    )
    RouteFinancialRow("Net", "$ ${money(financials.net)}")
}

@Composable
private fun RouteFinancialRow(
    label: String,
    value: String,
    labelDetail: String? = null,
    valueDetail: String? = null,
    onLabelClick: (() -> Unit)? = null
) {
    val palette = LocalVendistriPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onLabelClick != null) Modifier.clickable(onClick = onLabelClick) else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                label,
                color = AppColors.muted,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            labelDetail?.let {
                Text(
                    it,
                    color = AppColors.muted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                value,
                color = palette.textPrimary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
            valueDetail?.let {
                Text(
                    it,
                    color = AppColors.muted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun RouteFinancialBreakdownRow(leftLabel: String, rightLabel: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            leftLabel,
            modifier = Modifier.weight(1f),
            color = AppColors.muted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            rightLabel,
            color = AppColors.muted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

private fun taskTypeLabel(type: TaskType): String {
    return when (type) {
        TaskType.MachineService -> "Service"
        TaskType.MachineCollection -> "Collection"
        TaskType.MachineRefill -> "Refill"
        TaskType.MachineClean -> "Clean"
        TaskType.MachineRepair -> "Repair"
        TaskType.MachineRefund -> "Refund"
        TaskType.MachineInstall -> "Install"
        TaskType.MachineRemove -> "Remove"
        TaskType.MachinePickupInventory -> "Pickup Inventory"
        TaskType.Default,
        TaskType.Other -> "Task"
    }
}

@Composable
private fun GoRoutePreview(
    preview: RoutePreview,
    timeFormatPreference: TimeFormatPreference,
    systemUses24Hour: Boolean
) {
    val travelSeconds = preview.expectedTravelSeconds.coerceAtLeast(0.0)
    val distanceMiles = preview.distanceMiles.coerceAtLeast(0.0)
    RouteTravelSummaryRow(
        durationText = formatDuration(travelSeconds / 60.0),
        arrivalDistanceText = "${arrivalText(travelSeconds, timeFormatPreference, systemUses24Hour)} • ${oneDecimal(distanceMiles)} mi",
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
    )
}

private fun ownershipText(stop: GoStopPlan, currentUserId: String?): AnnotatedString? {
    val normalizedUserId = currentUserId?.trim().orEmpty()
    val assignedCount = stop.tasks.count { task ->
        normalizedUserId.isNotBlank() &&
            task.assignee?.trim() == normalizedUserId &&
            task.status != TaskStatus.Unassigned
    }
    val unassignedCount = stop.tasks.count { task ->
        task.assignee.isNullOrBlank() || task.status == TaskStatus.Unassigned
    }
    if (assignedCount + unassignedCount == 0) return null

    return buildAnnotatedString {
        when {
            unassignedCount == 0 -> withStyle(SpanStyle(color = AppColors.statusPending)) {
                append("$assignedCount ${if (assignedCount == 1) "task" else "tasks"} assigned to you")
            }
            assignedCount == 0 -> withStyle(SpanStyle(color = AppColors.statusUnassigned)) {
                append("$unassignedCount unassigned ${if (unassignedCount == 1) "task" else "tasks"}")
            }
            else -> {
                withStyle(SpanStyle(color = AppColors.statusPending)) {
                    append("$assignedCount assigned")
                }
                withStyle(SpanStyle(color = AppColors.muted)) {
                    append(", ")
                }
                withStyle(SpanStyle(color = AppColors.statusUnassigned)) {
                    append("$unassignedCount unassigned")
                }
            }
        }
    }
}

private fun fullAddress(stop: GoStopPlan): String? {
    return listOfNotNull(stop.addressStreetLine, stop.addressCityStateZipLine)
        .joinToString(", ")
        .takeIf { it.isNotBlank() }
}

private data class RouteStopPreview(
    val stopNumber: Int,
    val stop: GoStopPlan,
    val legDistanceMiles: Double,
    val legTravelSeconds: Double,
    val cumulativeDistanceMiles: Double,
    val cumulativeTravelSeconds: Double
)

private fun orderedRouteStops(stops: List<GoStopPlan>, selectedStop: GoStopPlan?): List<GoStopPlan> {
    val selectedId = selectedStop?.id ?: return stops
    return stops.filter { it.id == selectedId } + stops.filter { it.id != selectedId }
}

private fun buildRouteStopPreviews(
    stops: List<GoStopPlan>,
    selectedRoutePreview: RoutePreview?
): List<RouteStopPreview> {
    var previousStop: GoStopPlan? = null
    var cumulativeDistance = 0.0
    var cumulativeSeconds = 0.0
    return stops.mapIndexed { index, stop ->
        val legPreview = if (index == 0) {
            selectedRoutePreview ?: RoutePreview(0.0, 0.0)
        } else {
            val previousCoordinate = previousStop?.coordinate
            val coordinate = stop.coordinate
            if (previousCoordinate != null && coordinate != null) {
                RoutePreviewEstimator.previewRoute(previousCoordinate, coordinate) ?: RoutePreview(0.0, 0.0)
            } else {
                RoutePreview(0.0, 0.0)
            }
        }
        cumulativeDistance += legPreview.distanceMiles.coerceAtLeast(0.0)
        cumulativeSeconds += legPreview.expectedTravelSeconds.coerceAtLeast(0.0)
        previousStop = stop
        RouteStopPreview(
            stopNumber = index + 1,
            stop = stop,
            legDistanceMiles = legPreview.distanceMiles.coerceAtLeast(0.0),
            legTravelSeconds = legPreview.expectedTravelSeconds.coerceAtLeast(0.0),
            cumulativeDistanceMiles = cumulativeDistance,
            cumulativeTravelSeconds = cumulativeSeconds
        )
    }
}

private fun taskLocationGroup(stop: GoStopPlan): TaskLocationGroup {
    val tasks = TaskGroupingHelpers.uniqueTasksById(stop.tasks)
    val totals = TaskFinancialHelpers.sumTaskFinancials(tasks)
    return TaskLocationGroup(
        id = stop.id,
        name = stop.title,
        addressStreetLine = stop.addressStreetLine,
        addressCityStateZipLine = stop.addressCityStateZipLine,
        tasks = tasks,
        machineGroups = TaskGroupingHelpers.groupByMachine(tasks),
        doneCount = tasks.count { it.status == com.vendistri.operations.features.tasks.TaskStatus.Done },
        cancelledCount = tasks.count {
            it.status == com.vendistri.operations.features.tasks.TaskStatus.Cancelled ||
                it.status == com.vendistri.operations.features.tasks.TaskStatus.Error
        },
        totalCount = tasks.size,
        gross = totals.gross,
        commission = totals.commission,
        net = totals.net,
        durationMinutes = TaskGroupingHelpers.totalDurationMinutes(tasks),
        distanceMiles = TaskGroupingHelpers.totalDistanceMiles(tasks)
    )
}

private fun toggleSet(values: Set<String>, value: String): Set<String> {
    return if (value in values) values - value else values + value
}

private fun arrivalText(
    afterSeconds: Double,
    preference: TimeFormatPreference,
    systemUses24Hour: Boolean
): String {
    return AppTimeFormatter.arrivalTime(
        afterSeconds = afterSeconds,
        preference = preference,
        systemUses24Hour = systemUses24Hour
    )
}
