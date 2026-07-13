package com.vendistri.operations.features.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vendistri.operations.design.AppColors
import com.vendistri.operations.design.LocalVendistriPalette
import com.vendistri.operations.components.RouteMetricIconKind
import com.vendistri.operations.components.RouteMetricText
import com.vendistri.operations.features.location.AppLocation
import com.vendistri.operations.features.location.LocationHours
import com.vendistri.operations.features.location.LocationHoursLabel
import com.vendistri.operations.features.settings.TimeFormatPreference
import com.vendistri.operations.features.tasks.ReadOnlyFinancialBreakdownRows
import com.vendistri.operations.features.tasks.SharedTaskNotesFooter
import com.vendistri.operations.features.tasks.TaskFinancialHelpers
import com.vendistri.operations.features.tasks.TaskGroupingHelpers
import com.vendistri.operations.features.tasks.TaskMachineGroup
import com.vendistri.operations.features.tasks.TaskScheduleDate
import com.vendistri.operations.features.tasks.TaskStateHelpers
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskStatusCounts
import com.vendistri.operations.features.tasks.TaskStatusHelpers
import com.vendistri.operations.features.tasks.TaskStatusPresentation
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask
import com.vendistri.operations.features.tasks.formatDuration
import com.vendistri.operations.features.tasks.money
import com.vendistri.operations.features.tasks.oneDecimal
import com.vendistri.operations.features.tasks.signedQuantity
import com.vendistri.operations.features.tasks.taskTypeLabel
import com.vendistri.operations.features.work.ActiveTaskExecution
import com.vendistri.operations.features.work.ExecutionTaskItem
import com.vendistri.operations.features.work.GoStopPlan
import com.vendistri.operations.features.work.LocalActiveExecutionSession
import com.vendistri.operations.features.work.PickupInventoryRouteContext
import com.vendistri.operations.features.work.PreviousWorkBlock
import com.vendistri.operations.features.work.PreviousWorkResolver
import com.vendistri.operations.features.work.TaskExecutionResolver
import com.vendistri.operations.features.work.TaskExecutionDisplay
import com.vendistri.operations.features.work.TaskExecutionMetrics
import com.vendistri.operations.features.work.CompletedPickupWorkBlock
import com.vendistri.operations.features.work.ExecutionScopeResolver
import com.vendistri.operations.utils.AddressFormatter
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

private enum class NavigationTripExpandedPage {
    Tasks,
    Overview
}

internal data class NavigationTripOverviewContext(
    val title: String,
    val addressLine: String?,
    val locationId: String?,
    val tasks: List<VendiTask>,
    val statusTasks: List<VendiTask>,
    val machineGroups: List<TaskMachineGroup>
)

@Composable
fun NavigationTripExpandedPanel(
    stop: GoStopPlan?,
    execution: ActiveTaskExecution?,
    localSession: LocalActiveExecutionSession?,
    allTasks: List<VendiTask>,
    postPickupDestinationStop: GoStopPlan?,
    postPickupDestinationTaskIds: Set<String> = emptySet(),
    locationsById: Map<String, AppLocation>,
    timeFormatPreference: TimeFormatPreference,
    systemUses24Hour: Boolean?,
    onApplySharedNotes: suspend (List<String>, String?) -> Boolean,
    modifier: Modifier = Modifier
) {
    var selectedPage by remember(stop?.id, execution?.stopId) { mutableStateOf(NavigationTripExpandedPage.Tasks) }
    var nowMillis by remember(execution?.stopId, execution?.currentTaskId) {
        mutableLongStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(execution?.stopId, execution?.currentTaskId) {
        while (execution != null) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val hydratedExecution = remember(execution, allTasks) {
        execution?.hydratedFrom(allTasks)
    }
    Column(modifier = modifier.fillMaxWidth()) {
        if (stop == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(LocalVendistriPalette.current.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Stop details unavailable",
                    color = LocalVendistriPalette.current.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(304.dp)
                    .pointerInput(stop.id, execution?.stopId) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            when {
                                dragAmount < -24f -> selectedPage = NavigationTripExpandedPage.Overview
                                dragAmount > 24f -> selectedPage = NavigationTripExpandedPage.Tasks
                            }
                        }
                    }
            ) {
                when (selectedPage) {
                    NavigationTripExpandedPage.Tasks -> NavigationTripTasksPage(
                        stop = stop,
                        execution = hydratedExecution,
                        localSession = localSession,
                        allTasks = allTasks,
                        nowMillis = nowMillis,
                        timeFormatPreference = timeFormatPreference,
                        systemUses24Hour = systemUses24Hour
                    )
                    NavigationTripExpandedPage.Overview -> NavigationTripOverviewPage(
                        stop = stop,
                        execution = hydratedExecution,
                        postPickupDestinationStop = postPickupDestinationStop,
                        postPickupDestinationTaskIds = postPickupDestinationTaskIds,
                        locationsById = locationsById,
                        allTasks = allTasks,
                        nowMillis = nowMillis,
                        onApplySharedNotes = onApplySharedNotes
                    )
                }
            }
            NavigationTripTabIndicator(
                selectedPage = selectedPage,
                onSelectPage = { selectedPage = it },
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun NavigationTripTasksPage(
    stop: GoStopPlan,
    execution: ActiveTaskExecution?,
    localSession: LocalActiveExecutionSession?,
    allTasks: List<VendiTask>,
    nowMillis: Long,
    timeFormatPreference: TimeFormatPreference,
    systemUses24Hour: Boolean?
) {
    val tasks = execution?.displayTasks?.takeIf { it.isNotEmpty() } ?: stop.tasks
    val executionScope = remember(execution, allTasks, nowMillis) {
        execution?.let { ExecutionScopeResolver.resolve(it, allTasks = allTasks, nowEpochMillis = nowMillis) }
    }
    val completedPickupTasks = remember(executionScope, tasks, allTasks) {
        if (tasks.firstOrNull()?.type == TaskType.MachinePickupInventory) {
            emptyList()
        } else {
            executionScope?.completedPickupTasks
                ?: TaskExecutionResolver.completedPickupTasks(linkedToTasks = tasks, candidates = allTasks)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        NavigationDestinationHeader(
            title = execution?.title ?: stop.title,
            addressLine = stop.compactAddressLine
        )
        if (execution != null) {
            TimeDistanceRow(execution = execution, nowMillis = nowMillis)
        }
        if (tasks.firstOrNull()?.type == TaskType.MachinePickupInventory) {
            PickupInventoryDetails(tasks = tasks)
        } else {
            MachineTaskGroups(groups = execution?.machineGroups?.takeIf { it.isNotEmpty() } ?: stop.machineGroups)
        }
        CompletedPickupWorkBlock(tasks = completedPickupTasks)
        LabelValueRow(label = "Started", value = startedText(localSession, timeFormatPreference, systemUses24Hour))
        LabelValueRow(label = "Destination", value = stop.compactAddressLine ?: "—")
        execution?.let {
            LiveTotalRow(execution = it, completedPickupTasks = completedPickupTasks, nowMillis = nowMillis)
        }
        if (tasks.firstOrNull()?.type != TaskType.MachinePickupInventory) {
            WarehouseStockSummary(
                tasks = tasks,
                excludedRefillTaskIds = TaskExecutionResolver.linkedRefillTaskIds(completedPickupTasks)
            )
        }
    }
}

@Composable
private fun NavigationTripOverviewPage(
    stop: GoStopPlan,
    execution: ActiveTaskExecution?,
    postPickupDestinationStop: GoStopPlan?,
    postPickupDestinationTaskIds: Set<String>,
    locationsById: Map<String, AppLocation>,
    allTasks: List<VendiTask>,
    nowMillis: Long,
    onApplySharedNotes: suspend (List<String>, String?) -> Boolean
) {
    val isPickupStop = stop.tasks.any { it.type == TaskType.MachinePickupInventory }
    val overviewContext = remember(stop, execution?.displayTasks, postPickupDestinationStop, allTasks) {
        navigationTripOverviewContext(
            stop = stop,
            execution = execution,
            postPickupDestinationStop = postPickupDestinationStop,
            postPickupDestinationTaskIds = postPickupDestinationTaskIds,
            allTasks = allTasks
        )
    }
    val visibleTasks = overviewContext.tasks
    val visibleStatusTasks = overviewContext.statusTasks
    val executionScope = remember(execution, allTasks, nowMillis) {
        execution?.let { ExecutionScopeResolver.resolve(it, allTasks = allTasks, nowEpochMillis = nowMillis) }
    }
    val completedPickupTasks = remember(executionScope, visibleTasks, allTasks, isPickupStop) {
        if (isPickupStop) {
            emptyList()
        } else {
            executionScope?.completedPickupTasks
                ?: TaskExecutionResolver.completedPickupTasks(linkedToTasks = visibleTasks, candidates = allTasks)
        }
    }
    val previousWorkTasks = remember(visibleTasks, completedPickupTasks, allTasks) {
        PreviousWorkResolver.previousWork(
            currentTasks = visibleTasks,
            allTasks = allTasks,
            completedPickupTasks = completedPickupTasks
        )
    }
    val overviewStop = if (isPickupStop) postPickupDestinationStop ?: stop else stop
    val overviewLocation = overviewContext.locationId?.let(locationsById::get) ?: locationsById[overviewStop.targetLocationId]
    val overviewHours = LocationHours.display(overviewLocation)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        NavigationDestinationHeader(
            title = overviewContext.title,
            addressLine = overviewContext.addressLine,
            hoursDisplay = overviewHours,
            trailing = {
                val counts = TaskStatusHelpers.statusCounts(visibleStatusTasks)
                MachineStatusSummary(counts = counts)
            }
        )
        if (isPickupStop) {
            NavigationWarehouseContext(warehouseName = stop.title, addressLine = stop.compactAddressLine)
        }
        FinancialBreakdownRows(tasks = visibleTasks)
        MachineTaskGroups(groups = overviewContext.machineGroups)
        CompletedPickupWorkBlock(tasks = completedPickupTasks)
        PreviousWorkBlock(tasks = previousWorkTasks)
        SharedTaskNotesFooter(
            tasks = visibleTasks,
            focusKey = "navigation:${stop.id}",
            placeholder = "Notes for this stop...",
            onApplySharedNotes = onApplySharedNotes
        )
    }
}

internal fun navigationTripOverviewContext(
    stop: GoStopPlan,
    execution: ActiveTaskExecution?,
    postPickupDestinationStop: GoStopPlan?,
    postPickupDestinationTaskIds: Set<String> = emptySet(),
    allTasks: List<VendiTask>
): NavigationTripOverviewContext {
    val isPickupStop = stop.tasks.any { it.type == TaskType.MachinePickupInventory }
    if (!isPickupStop) {
        val candidateTasks = execution?.displayTasks?.takeIf { it.isNotEmpty() } ?: stop.tasks
        val scheduledDate = candidateTasks
            .firstOrNull { it.id == execution?.currentTaskId }
            ?.scheduledFor
            ?.let(TaskScheduleDate::parse)
            ?: candidateTasks.firstOrNull { !TaskStateHelpers.isFinal(it.status) }
                ?.scheduledFor
                ?.let(TaskScheduleDate::parse)
            ?: candidateTasks.firstOrNull()?.scheduledFor?.let(TaskScheduleDate::parse)
        val tasks = if (scheduledDate == null) {
            candidateTasks
        } else {
            candidateTasks.filter { TaskScheduleDate.isSameDay(it.scheduledFor, scheduledDate) }
        }
        return NavigationTripOverviewContext(
            title = stop.title,
            addressLine = stop.compactAddressLine,
            locationId = stop.targetLocationId,
            tasks = tasks,
            statusTasks = tasks,
            machineGroups = TaskGroupingHelpers.groupByMachine(tasks = tasks, lookupTasks = allTasks)
        )
    }

    val destinationContext = PickupInventoryRouteContext.destinationLocationContext(
        pickupTasks = stop.tasks,
        allTasks = allTasks,
        savedStopId = postPickupDestinationStop?.targetLocationId,
        preferredRefillTaskId = postPickupDestinationStop?.tasks?.firstOrNull { it.type == TaskType.MachineRefill }?.id,
        savedSessionTaskIds = postPickupDestinationTaskIds.ifEmpty {
            postPickupDestinationStop?.tasks?.map { it.id }?.toSet().orEmpty()
        },
        fallbackTitle = postPickupDestinationStop?.title ?: stop.title
    )
    val destinationTasks = destinationContext?.tasks.orEmpty()
    val scheduledDate = destinationTasks
        .firstOrNull { !TaskStateHelpers.isFinal(it.status) }
        ?.scheduledFor
        ?.let(TaskScheduleDate::parse)
        ?: destinationTasks.firstOrNull()?.scheduledFor?.let(TaskScheduleDate::parse)
    val visibleTasks = destinationTasks.filter { task ->
        !TaskStateHelpers.isFinal(task.status) &&
            (scheduledDate == null || TaskScheduleDate.isSameDay(task.scheduledFor, scheduledDate))
    }
    val addressLine = destinationContext?.address
        ?.let(AddressFormatter::singleLineWithoutCountry)
        ?.takeIf { it.isNotBlank() }
        ?: postPickupDestinationStop?.compactAddressLine
    return NavigationTripOverviewContext(
        title = destinationContext?.title ?: postPickupDestinationStop?.title ?: stop.title,
        addressLine = addressLine,
        locationId = destinationContext?.locationId,
        tasks = visibleTasks,
        statusTasks = visibleTasks,
        machineGroups = TaskGroupingHelpers.groupByMachine(
            tasks = visibleTasks,
            lookupTasks = allTasks
        )
    )
}

@Composable
private fun NavigationDestinationHeader(
    title: String,
    addressLine: String?,
    hoursDisplay: com.vendistri.operations.features.location.LocationHoursDisplay? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                color = LocalVendistriPalette.current.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            trailing?.invoke()
        }
        addressLine?.let {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = it,
                    color = LocalVendistriPalette.current.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                hoursDisplay?.let { display -> LocationHoursLabel(display = display) }
            }
        }
    }
}

@Composable
private fun TimeDistanceRow(execution: ActiveTaskExecution, nowMillis: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RouteMetricText(
            kind = RouteMetricIconKind.Time,
            text = TaskExecutionDisplay.timeText(execution, nowMillis),
            compact = true,
            textColor = AppColors.vendBlue
        )
        Spacer(modifier = Modifier.width(12.dp))
        RouteMetricText(
            kind = RouteMetricIconKind.Distance,
            text = TaskExecutionDisplay.distanceText(execution, nowMillis),
            compact = true,
            textColor = AppColors.vendBlue
        )
    }
}

@Composable
private fun PickupInventoryDetails(tasks: List<VendiTask>) {
    val pickupTasks = tasks.filter { it.type == TaskType.MachinePickupInventory }
    val lines = pickupTasks.flatMap { it.pickupLines }
    val grouped = lines.groupBy { it.product.id }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Pickup Inventory",
                color = LocalVendistriPalette.current.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            pickupTasks.firstOrNull()?.let { TaskStatusPill(task = it, status = it.status, labelStyle = TaskStatusPillStyle.Status) }
        }
        grouped.values.forEach { productLines ->
            val product = productLines.first().product
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = listOf(product.name, product.brand, product.size)
                            .mapNotNull { it?.takeIf(String::isNotBlank) }
                            .joinToString(" • "),
                        color = LocalVendistriPalette.current.textPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = pickupStockLabel(productLines),
                        color = pickupStockColor(productLines),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                productLines.forEach { line ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = line.machineName ?: "Machine",
                            color = LocalVendistriPalette.current.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = line.pickedUpQuantity?.let { buildAnnotatedString { append("$it / ${line.suggestedQuantity}") } }
                                ?: suggestedQuantityText(line.suggestedQuantity),
                            color = LocalVendistriPalette.current.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FinancialBreakdownRows(tasks: List<VendiTask>) {
    ReadOnlyFinancialBreakdownRows(tasks = tasks, showCommissionPercent = true)
}

@Composable
private fun MachineTaskGroups(groups: List<TaskMachineGroup>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        groups.forEach { group ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = group.name,
                        color = LocalVendistriPalette.current.textPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    MachineStatusSummary(TaskStatusHelpers.statusCounts(group.tasks))
                }
                financialSummaryText(group.tasks)?.let {
                    Text(
                        text = it,
                        color = LocalVendistriPalette.current.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2
                    )
                }
                TaskMachineVisitMetricFormatterSafe(group.tasks)?.let {
                    Text(
                        text = it,
                        color = LocalVendistriPalette.current.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    group.tasks.forEach { TaskStatusPill(task = it, status = it.status) }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(AppColors.border.copy(alpha = 0.75f))
                )
            }
        }
    }
}

@Composable
private fun MachineStatusSummary(counts: TaskStatusCounts) {
    val status = when {
        counts.pending > 0 -> TaskStatus.Pending
        counts.unassigned > 0 -> TaskStatus.Unassigned
        counts.done > 0 -> TaskStatus.Done
        counts.cancelled > 0 -> TaskStatus.Cancelled
        else -> TaskStatus.Pending
    }
    val text = when {
        counts.pending > 0 -> "${counts.pending} pending"
        counts.unassigned > 0 -> "${counts.unassigned} unassigned"
        counts.done > 0 -> "${counts.done} done"
        counts.cancelled > 0 -> "${counts.cancelled} cancelled"
        else -> "${counts.total} tasks"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallMetricDot(TaskStatusPresentation.indicatorColor(status))
        Text(
            text = text,
            color = LocalVendistriPalette.current.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private enum class TaskStatusPillStyle {
    Type,
    Status
}

@Composable
private fun TaskStatusPill(
    task: VendiTask,
    status: TaskStatus,
    labelStyle: TaskStatusPillStyle = TaskStatusPillStyle.Type
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        SmallMetricDot(TaskStatusPresentation.indicatorColor(status))
        Text(
            text = if (labelStyle == TaskStatusPillStyle.Status) TaskStatusPresentation.label(status) else taskTypeLabel(task.type),
            color = LocalVendistriPalette.current.textPrimary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun LabelValueRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = label,
            color = LocalVendistriPalette.current.textSecondary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            color = LocalVendistriPalette.current.textPrimary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.7f)
        )
    }
}

@Composable
private fun LiveTotalRow(execution: ActiveTaskExecution, completedPickupTasks: List<VendiTask>, nowMillis: Long) {
    val currentMetrics = TaskExecutionMetrics.totals(execution, nowMillis)
    val completedPickupMetrics = TaskExecutionMetrics.aggregateMetrics(
        tasks = completedPickupTasks.filterNot { it.id in execution.taskIds },
        nowEpochMillis = nowMillis
    )
    LabelValueRow(
        label = "Total",
        value = "${formatDuration(currentMetrics.durationMinutes + completedPickupMetrics.durationMinutes)} • " +
            "${oneDecimal((currentMetrics.distanceMiles + completedPickupMetrics.distanceMiles).coerceAtLeast(0.0))} mi"
    )
}

private fun ActiveTaskExecution.hydratedFrom(allTasks: List<VendiTask>): ActiveTaskExecution {
    val hydratedTasks = TaskExecutionResolver.hydratedTasks(displayTasks, allTasks)
    return copy(
        displayTasks = hydratedTasks,
        tasks = hydratedTasks.map(::navigationExecutionTaskItem),
        machineGroups = TaskExecutionResolver.orderedMachineGroups(hydratedTasks)
    )
}

private fun navigationExecutionTaskItem(task: VendiTask): ExecutionTaskItem {
    return ExecutionTaskItem(
        id = task.id,
        type = task.type,
        status = task.status,
        machineId = task.machine,
        machineName = task.machineName,
        startedAt = task.startedAt,
        doneAt = task.doneAt,
        isWrapper = task.type == TaskType.MachineService
    )
}

@Composable
private fun WarehouseStockSummary(tasks: List<VendiTask>, excludedRefillTaskIds: Set<String> = emptySet()) {
    val refillTasks = tasks.filter { it.type == TaskType.MachineRefill && it.id !in excludedRefillTaskIds }
    if (refillTasks.none { it.inventorySourceWarehouseName != null || it.inventorySourceWarehouseId != null }) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Warehouse Stock",
            color = LocalVendistriPalette.current.textSecondary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        refillTasks.forEach { task ->
            Text(
                text = listOfNotNull(task.machineName, task.inventorySourceWarehouseName).joinToString(" • ").ifBlank { task.displayTitle },
                color = LocalVendistriPalette.current.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun NavigationWarehouseContext(warehouseName: String, addressLine: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Pickup Inventory",
            color = LocalVendistriPalette.current.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
        Text(warehouseName, color = LocalVendistriPalette.current.textPrimary, fontWeight = FontWeight.SemiBold)
        addressLine?.let {
            Text(it, color = LocalVendistriPalette.current.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NavigationTripTabIndicator(
    selectedPage: NavigationTripExpandedPage,
    onSelectPage: (NavigationTripExpandedPage) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        listOf(NavigationTripExpandedPage.Tasks, NavigationTripExpandedPage.Overview).forEach { page ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .width(28.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (selectedPage == page) AppColors.vendBlue else AppColors.border.copy(alpha = 0.45f))
                    .clickable { onSelectPage(page) }
            )
        }
    }
}

@Composable
private fun SmallMetricDot(color: Color) {
    Surface(modifier = Modifier.size(7.dp), shape = CircleShape, color = color) {}
}

private val GoStopPlan.compactAddressLine: String?
    get() = listOfNotNull(addressStreetLine, addressCityStateZipLine)
        .filter { it.isNotBlank() }
        .joinToString(", ")
        .ifBlank { null }

private fun startedText(
    localSession: LocalActiveExecutionSession?,
    preference: TimeFormatPreference,
    systemUses24Hour: Boolean?
): String {
    val startedAt = localSession?.startedAtEpochMillis?.takeIf { it > 0L } ?: return "—"
    val instant = Instant.ofEpochMilli(startedAt)
    val formatter = DateTimeFormatter.ofPattern(
        if (preference == TimeFormatPreference.TwentyFourHour || (preference == TimeFormatPreference.System && systemUses24Hour == true)) {
            "HH:mm"
        } else {
            "h:mm"
        },
        Locale.US
    )
    return instant.atZone(ZoneId.systemDefault()).format(formatter)
}

private fun financialSummaryText(tasks: List<VendiTask>): String? {
    val totals = TaskFinancialHelpers.sumTaskFinancials(tasks.filter { it.type == TaskType.MachineCollection || it.type == TaskType.MachineRefund })
    if (listOf(totals.gross, totals.refunds, totals.commission, totals.net).all { abs(it) < 0.01 }) return null
    return listOf(
        "Gross $${money(totals.gross)}",
        "Refunds $${money(totals.refunds)}",
        "Commission $${money(totals.commission)}",
        "Net $${money(totals.net)}"
    ).joinToString(" • ")
}

private fun pickupStockLabel(lines: List<com.vendistri.operations.features.tasks.TaskPickupLine>): String {
    val available = lines.mapNotNull { it.warehouseAvailableStock }.maxOrNull()
    val needed = lines.sumOf { it.suggestedQuantity }
    return when {
        available == null -> "Suggested ${signedQuantity(needed)}"
        needed <= 0 || available >= needed -> "Available"
        available > 0 -> "Partial"
        else -> "None"
    }
}

@Composable
private fun pickupStockColor(lines: List<com.vendistri.operations.features.tasks.TaskPickupLine>): Color {
    val available = lines.mapNotNull { it.warehouseAvailableStock }.maxOrNull()
    val needed = lines.sumOf { it.suggestedQuantity }
    return when {
        available == null || needed <= 0 || available >= needed -> AppColors.statusDone
        available > 0 -> AppColors.statusPending
        else -> AppColors.statusError
    }
}

@Composable
private fun suggestedQuantityText(value: Int) = buildAnnotatedString {
    append("Suggested ")
    withStyle(SpanStyle(color = if (value > 0) AppColors.statusDone else LocalVendistriPalette.current.textSecondary)) {
        append(signedQuantity(value))
    }
}

private fun TaskMachineVisitMetricFormatterSafe(tasks: List<VendiTask>): String? {
    val days = tasks.firstOrNull { it.daysSinceLastVisit != null }?.daysSinceLastVisit ?: return null
    return "Last visit $days ${if (days == 1) "day" else "days"}"
}
