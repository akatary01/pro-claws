package com.vendistri.operations.features.work

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vendistri.operations.R
import com.vendistri.operations.components.RouteMetricIcon
import com.vendistri.operations.components.RouteMetricIconKind
import com.vendistri.operations.design.AppColors
import com.vendistri.operations.features.location.AppLocation
import com.vendistri.operations.features.location.LocationHours
import com.vendistri.operations.features.location.LocationHoursLabel
import com.vendistri.operations.features.location.WarehouseOption
import com.vendistri.operations.features.pickup.PickupInventoryUiState
import com.vendistri.operations.features.refill.RefillInventoryUiState
import com.vendistri.operations.features.tasks.RefillInventorySourceMode
import com.vendistri.operations.features.tasks.ReadOnlyFinancialBreakdownRows
import com.vendistri.operations.features.tasks.SharedTaskNotesFooter
import com.vendistri.operations.features.tasks.TaskCardActions
import com.vendistri.operations.features.tasks.TaskBulkSelectionRules
import com.vendistri.operations.features.tasks.TaskBundleHelpers
import com.vendistri.operations.features.tasks.TaskGroupingHelpers
import com.vendistri.operations.features.tasks.TaskMachineGroup
import com.vendistri.operations.features.tasks.TaskStateHelpers
import com.vendistri.operations.features.tasks.TaskStatusPresentation
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask
import com.vendistri.operations.features.tasks.taskTypeLabel
import com.vendistri.operations.features.tasks.oneDecimal
import com.vendistri.operations.features.tasks.formatTaskDuration
import com.vendistri.operations.features.tasks.actions.TaskActionKind
import com.vendistri.operations.features.tasks.actions.TaskActionSheet
import com.vendistri.operations.features.tasks.actions.TaskActionState
import com.vendistri.operations.utils.AddressFormatter
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AtLocationExecutionView(
    execution: ActiveTaskExecution,
    refillInventoryState: RefillInventoryUiState,
    pickupInventoryState: PickupInventoryUiState,
    warehouses: List<WarehouseOption>,
    allTasks: List<VendiTask>,
    locationsById: Map<String, AppLocation> = emptyMap(),
    postPickupDestination: PostPickupDestination?,
    pendingMutationTaskIds: Set<String>,
    errorMessage: String?,
    taskActions: TaskCardActions,
    autoCalcCommission: Boolean,
    onPrepareCurrentInventoryTask: () -> Unit,
    onMarkCurrentTaskDone: () -> Unit,
    onCompleteCurrentInventoryTask: () -> Unit,
    onAdvanceTask: () -> Unit,
    onRefillQuantityChanged: (String, String) -> Unit,
    onRefillFinalStockChanged: (String, String) -> Unit,
    onRefillSourceSelected: (RefillInventorySourceMode, String?) -> Unit,
    onPickupQuantityChanged: (String, String) -> Unit,
    onFinishVisit: () -> Unit,
    onCancelTasks: (List<VendiTask>) -> Unit,
    onAddPhoto: (VendiTask) -> Unit,
    onRemovePhoto: (VendiTask) -> Unit,
    onApplySharedNotes: suspend (List<String>, String?) -> Boolean,
    modifier: Modifier = Modifier
) {
    AtDestinationExecutionContent(
        execution = execution,
        title = "At location",
        notesPlaceholder = "Notes for this stop...",
        finishTitle = "Finished",
        refillInventoryState = refillInventoryState,
        pickupInventoryState = pickupInventoryState,
        warehouses = warehouses,
        allTasks = allTasks,
        locationsById = locationsById,
        postPickupDestination = postPickupDestination,
        pendingMutationTaskIds = pendingMutationTaskIds,
        errorMessage = errorMessage,
        taskActions = taskActions,
        autoCalcCommission = autoCalcCommission,
        onPrepareCurrentInventoryTask = onPrepareCurrentInventoryTask,
        onMarkCurrentTaskDone = onMarkCurrentTaskDone,
        onCompleteCurrentInventoryTask = onCompleteCurrentInventoryTask,
        onAdvanceTask = onAdvanceTask,
        onRefillQuantityChanged = onRefillQuantityChanged,
        onRefillFinalStockChanged = onRefillFinalStockChanged,
        onRefillSourceSelected = onRefillSourceSelected,
        onPickupQuantityChanged = onPickupQuantityChanged,
        onFinishVisit = onFinishVisit,
        onCancelTasks = onCancelTasks,
        onAddPhoto = onAddPhoto,
        onRemovePhoto = onRemovePhoto,
        onApplySharedNotes = onApplySharedNotes,
        modifier = modifier
    )
}

@Composable
fun AtWarehouseExecutionView(
    execution: ActiveTaskExecution,
    refillInventoryState: RefillInventoryUiState,
    pickupInventoryState: PickupInventoryUiState,
    warehouses: List<WarehouseOption>,
    allTasks: List<VendiTask>,
    locationsById: Map<String, AppLocation> = emptyMap(),
    postPickupDestination: PostPickupDestination?,
    pendingMutationTaskIds: Set<String>,
    errorMessage: String?,
    taskActions: TaskCardActions,
    autoCalcCommission: Boolean,
    onPrepareCurrentInventoryTask: () -> Unit,
    onMarkCurrentTaskDone: () -> Unit,
    onCompleteCurrentInventoryTask: () -> Unit,
    onAdvanceTask: () -> Unit,
    onRefillQuantityChanged: (String, String) -> Unit,
    onRefillFinalStockChanged: (String, String) -> Unit,
    onRefillSourceSelected: (RefillInventorySourceMode, String?) -> Unit,
    onPickupQuantityChanged: (String, String) -> Unit,
    onFinishVisit: () -> Unit,
    onCancelTasks: (List<VendiTask>) -> Unit,
    onAddPhoto: (VendiTask) -> Unit,
    onRemovePhoto: (VendiTask) -> Unit,
    onApplySharedNotes: suspend (List<String>, String?) -> Boolean,
    modifier: Modifier = Modifier
) {
    AtDestinationExecutionContent(
        execution = execution,
        title = "At warehouse",
        notesPlaceholder = "Notes for this pickup...",
        finishTitle = "Finished",
        refillInventoryState = refillInventoryState,
        pickupInventoryState = pickupInventoryState,
        warehouses = warehouses,
        allTasks = allTasks,
        locationsById = locationsById,
        postPickupDestination = postPickupDestination,
        pendingMutationTaskIds = pendingMutationTaskIds,
        errorMessage = errorMessage,
        taskActions = taskActions,
        autoCalcCommission = autoCalcCommission,
        onPrepareCurrentInventoryTask = onPrepareCurrentInventoryTask,
        onMarkCurrentTaskDone = onMarkCurrentTaskDone,
        onCompleteCurrentInventoryTask = onCompleteCurrentInventoryTask,
        onAdvanceTask = onAdvanceTask,
        onRefillQuantityChanged = onRefillQuantityChanged,
        onRefillFinalStockChanged = onRefillFinalStockChanged,
        onRefillSourceSelected = onRefillSourceSelected,
        onPickupQuantityChanged = onPickupQuantityChanged,
        onFinishVisit = onFinishVisit,
        onCancelTasks = onCancelTasks,
        onAddPhoto = onAddPhoto,
        onRemovePhoto = onRemovePhoto,
        onApplySharedNotes = onApplySharedNotes,
        modifier = modifier
    )
}

@Composable
private fun AtDestinationExecutionContent(
    execution: ActiveTaskExecution,
    title: String,
    notesPlaceholder: String,
    finishTitle: String,
    refillInventoryState: RefillInventoryUiState,
    pickupInventoryState: PickupInventoryUiState,
    warehouses: List<WarehouseOption>,
    allTasks: List<VendiTask>,
    locationsById: Map<String, AppLocation> = emptyMap(),
    postPickupDestination: PostPickupDestination?,
    pendingMutationTaskIds: Set<String>,
    errorMessage: String?,
    taskActions: TaskCardActions,
    autoCalcCommission: Boolean,
    onPrepareCurrentInventoryTask: () -> Unit,
    onMarkCurrentTaskDone: () -> Unit,
    onCompleteCurrentInventoryTask: () -> Unit,
    onAdvanceTask: () -> Unit,
    onRefillQuantityChanged: (String, String) -> Unit,
    onRefillFinalStockChanged: (String, String) -> Unit,
    onRefillSourceSelected: (RefillInventorySourceMode, String?) -> Unit,
    onPickupQuantityChanged: (String, String) -> Unit,
    onFinishVisit: () -> Unit,
    onCancelTasks: (List<VendiTask>) -> Unit,
    onAddPhoto: (VendiTask) -> Unit,
    onRemovePhoto: (VendiTask) -> Unit,
    onApplySharedNotes: suspend (List<String>, String?) -> Boolean,
    modifier: Modifier = Modifier
) {
    var showCancelTasksSheet by remember(execution.stopId) { mutableStateOf(false) }
    var showStopActionsMenu by remember(execution.stopId) { mutableStateOf(false) }
    var cancelSelectionTaskIds by remember(execution.stopId) { mutableStateOf(emptySet<String>()) }
    val titleText = execution.displayTitleForDestination(title)
    val addressText = execution.addressTextForDestination()
    val warehouseDestinationContext = remember(execution.displayTasks, allTasks, locationsById, postPickupDestination) {
        execution.warehouseDestinationContext(allTasks, locationsById, postPickupDestination)
    }
    LaunchedEffect(execution.currentTaskId) {
        onPrepareCurrentInventoryTask()
    }
    var nowMillis by remember(execution.stopId, execution.currentTaskId) {
        mutableLongStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(execution.stopId, execution.currentTaskId) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val executionScope = remember(execution, allTasks, nowMillis) {
        ExecutionScopeResolver.resolve(
            execution = execution,
            allTasks = allTasks,
            nowEpochMillis = nowMillis
        )
    }
    val currentExecutionTasks = executionScope.currentExecutionTasks
    val cancelSheetState = TaskActionState(
        activeAction = if (showCancelTasksSheet) TaskActionKind.Cancel else null,
        tasks = currentExecutionTasks,
        selectedTaskIds = cancelSelectionTaskIds
    )
    val isWarehousePickupVisit = execution.destinationKind == WorkDestinationKind.Warehouse ||
        execution.displayTasks.any { it.type == TaskType.MachinePickupInventory } ||
        executionScope.currentExecutionTasks.any { it.type == TaskType.MachinePickupInventory }
    val headerMetrics = atDestinationHeaderMetrics(
        execution = execution,
        scope = executionScope,
        isWarehousePickupVisit = isWarehousePickupVisit,
        nowEpochMillis = nowMillis
    )
    val location = execution.locationId?.let(locationsById::get)
    val locationHoursDisplay = LocationHours.display(location)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AtDestinationHeader(
                title = titleText,
                address = addressText,
                execution = execution,
                metrics = headerMetrics,
                hoursDisplay = locationHoursDisplay,
                finishTitle = finishTitle,
                canFinish = TaskExecutionDisplay.canFinishVisit(executionScope.currentExecutionTasks),
                onFinishVisit = onFinishVisit
            )
            val topFinancialTasks = if (isWarehousePickupVisit) {
                warehouseDestinationContext?.tasks.orEmpty()
            } else {
                executionScope.currentExecutionTasks
            }
            if (topFinancialTasks.isNotEmpty()) {
                AtDestinationFinancialRows(tasks = topFinancialTasks)
            }

            warehouseDestinationContext?.let { context ->
                WarehouseDestinationContextCard(context = context)
            }

            val completedPickupTasks = completedPickupTasksForDisplay(
                execution = execution,
                scope = executionScope,
                isWarehousePickupVisit = isWarehousePickupVisit
            )
            val showsCompletedPickupAsPrimaryWork = isWarehousePickupVisit &&
                completedPickupTasks.isNotEmpty() &&
                executionScope.allTasksAreFinal &&
                execution.currentTaskId == null
            if (showsCompletedPickupAsPrimaryWork) {
                CompletedPickupWorkBlock(
                    tasks = completedPickupTasks,
                    primaryRefillTaskIds = executionScope.completedPickupRefillTaskIds,
                    primaryTaskIds = execution.taskIds,
                    photoActions = CompletedPickupPhotoActions(
                        pendingMutationTaskIds = pendingMutationTaskIds,
                        onAddPhoto = onAddPhoto,
                        onRemovePhoto = onRemovePhoto
                    )
                )
            } else {
                AtLocationTaskList(
                    machineSections = executionScope.machineSections,
                    primaryTaskIds = execution.taskIds,
                    aggregatePickupInventoryCards = isWarehousePickupVisit,
                    appLocation = location,
                    refillInventoryState = refillInventoryState,
                    pickupInventoryState = pickupInventoryState,
                    warehouses = warehouses,
                    pendingMutationTaskIds = pendingMutationTaskIds,
                    taskActions = taskActions,
                    autoCalcCommission = autoCalcCommission,
                    errorMessage = errorMessage,
                    errorTaskId = execution.currentTaskId,
                    onMarkCurrentTaskDone = onMarkCurrentTaskDone,
                    onCompleteCurrentInventoryTask = onCompleteCurrentInventoryTask,
                    onRefillQuantityChanged = onRefillQuantityChanged,
                    onRefillFinalStockChanged = onRefillFinalStockChanged,
                    onRefillSourceSelected = onRefillSourceSelected,
                    onPickupQuantityChanged = onPickupQuantityChanged,
                    onAddPhoto = onAddPhoto,
                    onRemovePhoto = onRemovePhoto
                )
            }
            if (!showsCompletedPickupAsPrimaryWork && completedPickupTasks.isNotEmpty()) {
                CompletedPickupWorkBlock(
                    tasks = completedPickupTasks,
                    primaryRefillTaskIds = TaskExecutionResolver.linkedRefillTaskIds(executionScope.currentExecutionTasks),
                    primaryTaskIds = execution.taskIds,
                    photoActions = CompletedPickupPhotoActions(
                        pendingMutationTaskIds = pendingMutationTaskIds,
                        onAddPhoto = onAddPhoto,
                        onRemovePhoto = onRemovePhoto
                    )
                )
            }
            if (executionScope.previousWorkCandidates.isNotEmpty()) {
                PreviousWorkBlock(tasks = executionScope.previousWorkCandidates)
            }
            SharedTaskNotesFooter(
                tasks = execution.displayTasks,
                focusKey = execution.stopId,
                placeholder = notesPlaceholder,
                onApplySharedNotes = onApplySharedNotes
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box {
                    Surface(
                        modifier = Modifier
                            .size(44.dp)
                            .clickable { showStopActionsMenu = true },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "⋮",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    val remainingTaskCount = TaskExecutionDisplay.remainingTaskCount(currentExecutionTasks)
                    DropdownMenu(
                        expanded = showStopActionsMenu,
                        onDismissRequest = { showStopActionsMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(TaskExecutionDisplay.cancelRemainingTitle(currentExecutionTasks)) },
                            enabled = remainingTaskCount > 0,
                            onClick = {
                                showStopActionsMenu = false
                                val actionableTasks = TaskExecutionDisplay.remainingTasks(currentExecutionTasks)
                                    .filter { TaskStateHelpers.isActionable(it.status) }
                                cancelSelectionTaskIds = TaskBulkSelectionRules.normalizedSelection(
                                allTasks = actionableTasks,
                                selectedTaskIds = actionableTasks.map { it.id }.toSet()
                                )
                                showCancelTasksSheet = true
                            }
                        )
                    }
                }
            }
            Spacer(
                modifier = Modifier
                    .height(16.dp)
            )
        }
        TaskActionSheet(
            state = cancelSheetState,
            onDismiss = { showCancelTasksSheet = false },
            onConfirm = {
                val selectedTasks = cancelSheetState.selectedTasks
                if (selectedTasks.isNotEmpty()) {
                    showCancelTasksSheet = false
                    onCancelTasks(selectedTasks)
                }
            },
            onTaskAssigneeSelected = { _, _ -> },
            onDateSelected = {},
            onQuickDateSelected = {},
            onTaskSelectionToggle = { taskId ->
                val nextSelection = cancelSelectionTaskIds.toMutableSet().also { ids ->
                    if (!ids.add(taskId)) ids.remove(taskId)
                }
                cancelSelectionTaskIds = TaskBulkSelectionRules.normalizedSelection(
                    allTasks = cancelSheetState.actionableTasks,
                    selectedTaskIds = nextSelection
                )
            }
        )
    }
}

@Composable
private fun WarehouseDestinationContextCard(context: WarehouseDestinationContext) {
    var isExpanded by remember(context.id) { mutableStateOf(false) }
    val hoursDisplay = LocationHours.display(context.appLocation)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, AppColors.border)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = context.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                DestinationPendingSummary(count = context.pendingCount)
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_left),
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(if (isExpanded) 90f else -90f),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
            if (context.addressLine != null || hoursDisplay != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    if (context.addressLine != null) {
                        Text(
                            text = context.addressLine,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    hoursDisplay?.let { display ->
                        LocationHoursLabel(display = display)
                    }
                }
            }
            if (isExpanded) {
                AtDestinationFinancialRows(tasks = context.tasks)
                context.machineGroups.forEach { group ->
                    WarehouseDestinationMachineRow(group = group)
                }
            }
        }
    }
}

@Composable
private fun AtDestinationFinancialRows(tasks: List<VendiTask>) {
    ReadOnlyFinancialBreakdownRows(
        tasks = tasks,
        grossLabel = "Gross",
        netLabel = "Net",
        showCommissionPercent = true
    )
}

@Composable
private fun DestinationPendingSummary(count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        if (count > 0) {
            Surface(
                modifier = Modifier.size(7.dp),
                shape = CircleShape,
                color = TaskStatusPresentation.indicatorColor(com.vendistri.operations.features.tasks.TaskStatus.Pending)
            ) {}
        }
        Text(
            text = "$count pending",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PreviousWorkBlock(tasks: List<VendiTask>) {
    if (tasks.isEmpty()) return
    val sections = remember(tasks) {
        tasks.groupBy { it.machine ?: it.machineName ?: "machine" }
            .map { (_, machineTasks) -> machineTasks.first().displayMachine to machineTasks }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Previous Work",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        sections.forEach { (machineName, machineTasks) ->
            Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = machineName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    machineTasks.forEach { task ->
                        PreviousWorkTaskChip(task = task)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviousWorkTaskChip(task: VendiTask) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(7.dp),
                shape = CircleShape,
                color = TaskStatusPresentation.indicatorColor(task.status)
            ) {}
            Text(
                text = taskTypeLabel(task.type),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            task.assigneeName?.takeIf { it.isNotBlank() }?.let { assignee ->
                Text(
                    text = assignee,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = TaskStatusPresentation.label(task.status),
                color = TaskStatusPresentation.indicatorColor(task.status),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            task.previousWorkTimeText()?.let { timeText ->
                Text(
                    text = timeText,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun VendiTask.previousWorkTimeText(): String? {
    val timestamp = doneAt ?: startedAt ?: return null
    return runCatching {
        DateTimeFormatter.ofPattern("h:mm a")
            .format(Instant.parse(timestamp).atZone(ZoneId.systemDefault()))
    }.getOrNull()
}

@Composable
private fun WarehouseDestinationMachineRow(group: TaskMachineGroup) {
    val pendingCount = group.tasks.count { !TaskStateHelpers.isFinal(it.status) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            if (pendingCount > 0) {
                Text(
                    text = "$pendingCount pending",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            group.tasks.forEach { task ->
                WarehouseDestinationTaskStatusRow(task = task)
            }
        }
    }
}

@Composable
private fun WarehouseDestinationTaskStatusRow(task: VendiTask) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(7.dp),
            shape = CircleShape,
            color = TaskStatusPresentation.indicatorColor(task.status)
        ) {}
        Text(
            text = taskTypeLabel(task.type),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun AtDestinationHeader(
    title: String,
    address: String?,
    execution: ActiveTaskExecution,
    metrics: ExecutionScopeMetrics,
    hoursDisplay: com.vendistri.operations.features.location.LocationHoursDisplay?,
    finishTitle: String,
    canFinish: Boolean,
    onFinishVisit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (address != null || hoursDisplay != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        if (address != null) {
                            Text(
                                text = address,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        hoursDisplay?.let { display ->
                            LocationHoursLabel(display = display)
                        }
                    }
                }
            }
            if (canFinish) {
                FinishVisitButton(title = finishTitle, onClick = onFinishVisit)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            VisitStatChip(
                title = "Time",
                value = formatTaskDuration(metrics.durationMinutes * 60.0),
                iconKind = RouteMetricIconKind.Time,
                tint = AppColors.statusPending,
                modifier = Modifier.weight(1f)
            )
            VisitStatChip(
                title = "Distance",
                value = "${oneDecimal(metrics.distanceMiles.coerceAtLeast(0.0))} mi",
                iconKind = RouteMetricIconKind.Distance,
                tint = AppColors.vendBlue,
                modifier = Modifier.weight(1f)
            )
            VisitStatChip(
                title = "Progress",
                value = TaskExecutionDisplay.progressText(execution),
                iconKind = RouteMetricIconKind.Checklist,
                tint = AppColors.statusDone,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FinishVisitButton(title: String, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "finish-pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 0.98f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "finish-pulse-scale"
    )
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.64f,
        targetValue = 0.36f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "finish-pulse-alpha"
    )
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(108.dp)
            .height(40.dp),
        shape = CircleShape,
        color = AppColors.vendBlue,
        shadowElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(3.dp)
                    .scale(pulseScale)
                    .graphicsLayer { alpha = pulseAlpha }
                    .border(2.dp, Color.White.copy(alpha = 0.62f), CircleShape)
            )
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 0.dp)
            )
        }
    }
}

@Composable
private fun VisitStatChip(
    title: String,
    value: String,
    iconKind: RouteMetricIconKind,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = tint.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.18f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RouteMetricIcon(kind = iconKind, size = 12.dp, tint = tint)
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = value,
                color = if (tint == AppColors.statusPending) MaterialTheme.colorScheme.onSurface else tint,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AtDestinationSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, AppColors.border)
    ) {
        Column(modifier = Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun AtDestinationMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

internal val ActiveTaskExecution.currentTask: VendiTask?
    get() = currentTaskId?.let { taskId -> displayTasks.firstOrNull { it.id == taskId } }

internal fun completedPickupTasksForDisplay(
    execution: ActiveTaskExecution,
    scope: ExecutionScopeDisplayModel,
    isWarehousePickupVisit: Boolean
): List<VendiTask> {
    if (!isWarehousePickupVisit) return scope.completedPickupTasks
    if (execution.currentTaskId != null) return emptyList()
    return scope.completedPickupTasks
}

internal fun warehousePickupHeaderMetrics(
    pickupTasks: List<VendiTask>,
    execution: ActiveTaskExecution,
    nowEpochMillis: Long = System.currentTimeMillis()
): ExecutionScopeMetrics {
    return ExecutionScopeResolver.metrics(
        tasks = pickupTasks,
        execution = execution.takeUnless { pickupTasks.all { task -> TaskStateHelpers.isFinal(task.status) } },
        nowEpochMillis = nowEpochMillis
    )
}

internal fun atDestinationHeaderMetrics(
    execution: ActiveTaskExecution,
    scope: ExecutionScopeDisplayModel,
    isWarehousePickupVisit: Boolean,
    nowEpochMillis: Long = System.currentTimeMillis()
): ExecutionScopeMetrics {
    return if (isWarehousePickupVisit) {
        val pickupMetricTasks = scope.currentExecutionTasks
            .filter { it.type == TaskType.MachinePickupInventory }
            .ifEmpty { execution.displayTasks.filter { it.type == TaskType.MachinePickupInventory } }
        warehousePickupHeaderMetrics(
            pickupTasks = pickupMetricTasks,
            execution = execution,
            nowEpochMillis = nowEpochMillis
        )
    } else {
        ExecutionScopeResolver.metrics(
            tasks = scope.currentExecutionTasks,
            execution = execution,
            nowEpochMillis = nowEpochMillis
        )
    }
}

internal data class WarehouseDestinationContext(
    val id: String,
    val title: String,
    val addressLine: String?,
    val appLocation: AppLocation?,
    val tasks: List<VendiTask>,
    val machineGroups: List<TaskMachineGroup>,
    val pendingCount: Int
)

internal fun ActiveTaskExecution.warehouseDestinationContext(
    allTasks: List<VendiTask>,
    locationsById: Map<String, AppLocation>,
    postPickupDestination: PostPickupDestination?
): WarehouseDestinationContext? {
    val pickupTasks = displayTasks.filter { it.type == TaskType.MachinePickupInventory }
    if (pickupTasks.isEmpty()) return null
    val destinationContext = PickupInventoryRouteContext.destinationLocationContext(
        pickupTasks = pickupTasks,
        allTasks = allTasks,
        savedStopId = postPickupDestination?.stopId,
        preferredRefillTaskId = postPickupDestination?.refillTaskId,
        savedSessionTaskIds = postPickupDestination?.sessionTaskIds.orEmpty(),
        fallbackTitle = title
    )
        ?: return null
    val addressLine = destinationContext.address
        ?.let(AddressFormatter::singleLineWithoutCountry)
        ?.takeIf { it.isNotBlank() }
    val contextTasks = TaskGroupingHelpers.uniqueTasksById(
        destinationContext.tasks.flatMap { task -> TaskBundleHelpers.unifiedServiceBundleTasks(allTasks, task) }
    )
    val linkedPickupTasks = pickupTasks.filter { pickupTask ->
        TaskExecutionResolver.linkedRefillTaskIds(pickupTask)
            .intersect(contextTasks.map { it.id }.toSet())
            .isNotEmpty()
    }
    val activeContextTasks = contextTasks.filter { !TaskStateHelpers.isFinal(it.status) }
    val machineGroups = TaskGroupingHelpers.groupByMachine(
        tasks = TaskGroupingHelpers.uniqueTasksById(activeContextTasks + linkedPickupTasks),
        lookupTasks = contextTasks
    )
    val pendingCount = TaskGroupingHelpers.uniqueTasksById(machineGroups.flatMap { it.tasks })
        .count { !TaskStateHelpers.isFinal(it.status) }
    return WarehouseDestinationContext(
        id = destinationContext.locationId ?: pickupTasks.first().id,
        title = destinationContext.title,
        addressLine = addressLine,
        appLocation = destinationContext.locationId?.let(locationsById::get),
        tasks = contextTasks,
        machineGroups = machineGroups,
        pendingCount = pendingCount
    )
}

private fun ActiveTaskExecution.displayTitleForDestination(fallbackTitle: String): String {
    val pickupTask = displayTasks.firstOrNull { it.type == TaskType.MachinePickupInventory }
    return if (destinationKind == WorkDestinationKind.Warehouse || pickupTask != null) {
        pickupTask?.warehouseName
            ?.takeIf { it.isNotBlank() }
            ?: title
                .takeIf { it.isNotBlank() }
            ?: fallbackTitle
    } else {
        title.takeIf { it.isNotBlank() } ?: fallbackTitle
    }
}

private fun ActiveTaskExecution.addressTextForDestination(): String? {
    val pickupTask = displayTasks.firstOrNull { it.type == TaskType.MachinePickupInventory }
    return if (destinationKind == WorkDestinationKind.Warehouse || pickupTask != null) {
        pickupTask?.warehouseAddress
            ?.let(AddressFormatter::singleLineWithoutCountry)
            ?.takeIf { it.isNotBlank() }
    } else {
        displayTasks.firstOrNull()?.locationAddress
            ?.let(AddressFormatter::singleLineWithoutCountry)
            ?.takeIf { it.isNotBlank() }
    }
}

private fun ActiveTaskExecution.currentSectionTitle(): String {
    return when (currentTask?.type) {
        TaskType.MachinePickupInventory -> "Pickup Inventory"
        TaskType.MachineRefill -> "Refill"
        TaskType.MachineCollection -> "Collection"
        TaskType.MachineClean -> "Clean"
        TaskType.MachineService -> "Service"
        else -> "Current task"
    }
}
