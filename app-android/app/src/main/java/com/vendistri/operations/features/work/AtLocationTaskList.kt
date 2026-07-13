package com.vendistri.operations.features.work

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vendistri.operations.R
import com.vendistri.operations.components.RemoteImagePreview
import com.vendistri.operations.components.PrimaryActionButton
import com.vendistri.operations.design.AppColors
import com.vendistri.operations.design.AppShapes
import com.vendistri.operations.features.location.AppLocation
import com.vendistri.operations.features.location.WarehouseOption
import com.vendistri.operations.features.pickup.PickupInventoryCompletionView
import com.vendistri.operations.features.pickup.PickupInventoryUiState
import com.vendistri.operations.features.refill.RefillInventoryCompletionView
import com.vendistri.operations.features.refill.RefillInventoryUiState
import com.vendistri.operations.features.tasks.CompactTaskButton
import com.vendistri.operations.features.tasks.RefillInventorySourceMode
import com.vendistri.operations.features.tasks.TaskCardActions
import com.vendistri.operations.features.tasks.TaskRowView
import com.vendistri.operations.features.tasks.TaskStateHelpers
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskStatusBadge
import com.vendistri.operations.features.tasks.TaskStatusMenu
import com.vendistri.operations.features.tasks.TaskStatusPresentation
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.TaskInventoryCompletionLine
import com.vendistri.operations.features.tasks.TaskPickupLine
import com.vendistri.operations.features.tasks.TaskPickupLineFormatters
import com.vendistri.operations.features.tasks.VendiTask
import com.vendistri.operations.features.tasks.formatTaskDuration
import com.vendistri.operations.features.tasks.oneDecimal
import com.vendistri.operations.features.tasks.taskTypeLabel

@Composable
internal fun AtLocationTaskList(
    machineSections: List<ExecutionScopeMachineSection>,
    primaryTaskIds: List<String> = emptyList(),
    aggregatePickupInventoryCards: Boolean = false,
    appLocation: AppLocation?,
    refillInventoryState: RefillInventoryUiState,
    pickupInventoryState: PickupInventoryUiState,
    warehouses: List<WarehouseOption>,
    pendingMutationTaskIds: Set<String>,
    taskActions: TaskCardActions,
    autoCalcCommission: Boolean,
    errorMessage: String?,
    errorTaskId: String?,
    onMarkCurrentTaskDone: () -> Unit,
    onCompleteCurrentInventoryTask: () -> Unit,
    onRefillQuantityChanged: (String, String) -> Unit,
    onRefillFinalStockChanged: (String, String) -> Unit,
    onRefillSourceSelected: (RefillInventorySourceMode, String?) -> Unit,
    onPickupQuantityChanged: (String, String) -> Unit,
    onAddPhoto: (VendiTask) -> Unit,
    onRemovePhoto: (VendiTask) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedTaskIds by remember { mutableStateOf(emptySet<String>()) }
    val displayModel = remember(machineSections, primaryTaskIds, aggregatePickupInventoryCards) {
        atLocationTaskListDisplay(
            machineSections = machineSections,
            primaryTaskIds = primaryTaskIds,
            aggregatePickupInventoryCards = aggregatePickupInventoryCards
        )
    }
    val currentTaskIds = remember(machineSections) {
        machineSections.flatMap { section ->
            section.childCards.filter { it.isCurrent }.map { it.task.id }
        }.toSet()
    }
    LaunchedEffect(currentTaskIds) {
        if (currentTaskIds.isNotEmpty()) {
            expandedTaskIds = expandedTaskIds + currentTaskIds
        }
    }
    LaunchedEffect(errorMessage, errorTaskId) {
        val targetTaskId = errorTaskId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (!errorMessage.isNullOrBlank()) {
            expandedTaskIds = expandedTaskIds + targetTaskId
        }
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (displayModel.aggregatePickupCards.isEmpty() && displayModel.machineSections.isEmpty()) {
            AtLocationSectionCard {
                Text(
                    text = "No tasks for this stop.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            return@Column
        }
        displayModel.aggregatePickupCards.forEach { card ->
            key(card.task.id) {
                AtLocationTaskCard(
                task = card.task,
                displayStatus = card.displayStatus,
                metrics = card.metrics,
                isCurrent = card.isCurrent,
                machineNameContext = null,
                isUpdating = card.task.id in pendingMutationTaskIds,
                appLocation = appLocation,
                taskActions = taskActions,
                autoCalcCommission = autoCalcCommission,
                errorMessage = errorMessage,
                errorTaskId = errorTaskId,
                refillInventoryState = refillInventoryState,
                pickupInventoryState = pickupInventoryState,
                warehouses = warehouses,
                onMarkCurrentTaskDone = onMarkCurrentTaskDone,
                onCompleteCurrentInventoryTask = onCompleteCurrentInventoryTask,
                onRefillQuantityChanged = onRefillQuantityChanged,
                onRefillFinalStockChanged = onRefillFinalStockChanged,
                onRefillSourceSelected = onRefillSourceSelected,
                onPickupQuantityChanged = onPickupQuantityChanged,
                onAddPhoto = { onAddPhoto(card.task) },
                onRemovePhoto = { onRemovePhoto(card.task) },
                isExpanded = card.task.id in expandedTaskIds,
                    onToggleExpanded = { expandedTaskIds = expandedTaskIds.toggled(card.task.id) }
                )
            }
        }
        displayModel.machineSections.forEach { section ->
            AtLocationMachineSectionCard(
                section = section,
                appLocation = appLocation,
                refillInventoryState = refillInventoryState,
                pickupInventoryState = pickupInventoryState,
                warehouses = warehouses,
                pendingMutationTaskIds = pendingMutationTaskIds,
                taskActions = taskActions,
                autoCalcCommission = autoCalcCommission,
                errorMessage = errorMessage,
                errorTaskId = errorTaskId,
                onMarkCurrentTaskDone = onMarkCurrentTaskDone,
                onCompleteCurrentInventoryTask = onCompleteCurrentInventoryTask,
                onRefillQuantityChanged = onRefillQuantityChanged,
                onRefillFinalStockChanged = onRefillFinalStockChanged,
                onRefillSourceSelected = onRefillSourceSelected,
                onPickupQuantityChanged = onPickupQuantityChanged,
                onAddPhoto = onAddPhoto,
                onRemovePhoto = onRemovePhoto,
                expandedTaskIds = expandedTaskIds,
                onExpandedTaskIdsChange = { expandedTaskIds = it }
            )
        }
    }
}

@Composable
private fun AtLocationMachineSectionCard(
    section: ExecutionScopeMachineSection,
    appLocation: AppLocation?,
    refillInventoryState: RefillInventoryUiState,
    pickupInventoryState: PickupInventoryUiState,
    warehouses: List<WarehouseOption>,
    pendingMutationTaskIds: Set<String>,
    taskActions: TaskCardActions,
    autoCalcCommission: Boolean,
    errorMessage: String?,
    errorTaskId: String?,
    onMarkCurrentTaskDone: () -> Unit,
    onCompleteCurrentInventoryTask: () -> Unit,
    onRefillQuantityChanged: (String, String) -> Unit,
    onRefillFinalStockChanged: (String, String) -> Unit,
    onRefillSourceSelected: (RefillInventorySourceMode, String?) -> Unit,
    onPickupQuantityChanged: (String, String) -> Unit,
    onAddPhoto: (VendiTask) -> Unit,
    onRemovePhoto: (VendiTask) -> Unit,
    expandedTaskIds: Set<String>,
    onExpandedTaskIdsChange: (Set<String>) -> Unit
) {
    val taskCount = section.childCards.size + if (section.serviceTask != null) 1 else 0
    val markAllDoneTasks = section.childCards
        .map { it.task }
        .filter { taskActions.canChangeStatus(it) && !TaskStateHelpers.isFinal(it.status) }
    val pendingChildMutationCount = section.childCards.count { it.task.id in pendingMutationTaskIds }
    val isMarkAllDoneUpdating = pendingChildMutationCount > 1
    val shouldShowMarkAllDone = section.isActive && (markAllDoneTasks.size > 1 || isMarkAllDoneUpdating)
    val machineDisplayStatus = section.machineDisplayStatus()
    AtLocationSectionCard(section = section) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = section.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$taskCount ${if (taskCount == 1) "task" else "tasks"} • ${formatTaskDuration(section.machineMetrics.durationMinutes * 60.0)} • ${oneDecimal(section.machineMetrics.distanceMiles.coerceAtLeast(0.0))} mi",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (shouldShowMarkAllDone) {
                CompactTaskButton(
                    text = if (isMarkAllDoneUpdating) "Saving..." else "Mark All Done",
                    enabled = !isMarkAllDoneUpdating
                ) {
                    taskActions.onMarkDone(markAllDoneTasks)
                }
            } else {
                MachineStatusBadge(status = machineDisplayStatus)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        section.serviceTask?.let { serviceTask ->
            ServiceHeader(
                task = serviceTask,
                metrics = section.serviceMetrics ?: ExecutionScopeMetrics.Zero,
                displayStatus = section.serviceDisplayStatus ?: serviceTask.status,
                badgeState = section.serviceBadgeState,
                completedChildCount = section.serviceCompletedChildCount,
                totalChildCount = section.serviceTotalChildCount
            )
            if (section.childCards.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        section.childCards.forEachIndexed { index, card ->
            val task = card.task
            key(task.id) {
                AtLocationTaskCard(
                task = task,
                displayStatus = card.displayStatus,
                metrics = card.metrics,
                isCurrent = card.isCurrent,
                machineNameContext = section.name,
                isUpdating = task.id in pendingMutationTaskIds,
                appLocation = appLocation,
                taskActions = taskActions,
                autoCalcCommission = autoCalcCommission,
                errorMessage = errorMessage,
                errorTaskId = errorTaskId,
                refillInventoryState = refillInventoryState,
                pickupInventoryState = pickupInventoryState,
                warehouses = warehouses,
                onMarkCurrentTaskDone = onMarkCurrentTaskDone,
                onCompleteCurrentInventoryTask = onCompleteCurrentInventoryTask,
                onRefillQuantityChanged = onRefillQuantityChanged,
                onRefillFinalStockChanged = onRefillFinalStockChanged,
                onRefillSourceSelected = onRefillSourceSelected,
                onPickupQuantityChanged = onPickupQuantityChanged,
                onAddPhoto = { onAddPhoto(task) },
                onRemovePhoto = { onRemovePhoto(task) },
                isExpanded = task.id in expandedTaskIds,
                    onToggleExpanded = { onExpandedTaskIdsChange(expandedTaskIds.toggled(task.id)) }
                )
            }
            if (index != section.childCards.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun AtLocationTaskCard(
    task: VendiTask,
    displayStatus: TaskStatus,
    metrics: ExecutionScopeMetrics,
    isCurrent: Boolean,
    machineNameContext: String?,
    isUpdating: Boolean,
    appLocation: AppLocation?,
    taskActions: TaskCardActions,
    autoCalcCommission: Boolean,
    errorMessage: String?,
    errorTaskId: String?,
    refillInventoryState: RefillInventoryUiState,
    pickupInventoryState: PickupInventoryUiState,
    warehouses: List<WarehouseOption>,
    onMarkCurrentTaskDone: () -> Unit,
    onCompleteCurrentInventoryTask: () -> Unit,
    onRefillQuantityChanged: (String, String) -> Unit,
    onRefillFinalStockChanged: (String, String) -> Unit,
    onRefillSourceSelected: (RefillInventorySourceMode, String?) -> Unit,
    onPickupQuantityChanged: (String, String) -> Unit,
    onAddPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    val canExpand = task.canExpand(displayStatus)
    val shouldShowDetails = isExpanded
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.card,
        color = atLocationTaskCardColor(displayStatus, isCurrent),
        border = BorderStroke(
            width = 1.dp,
            color = atLocationTaskCardBorder(displayStatus, isCurrent)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(if (canExpand) Modifier.clickable { onToggleExpanded() } else Modifier),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(8.dp),
                            shape = CircleShape,
                            color = TaskStatusPresentation.indicatorColor(displayStatus)
                        ) {}
                        Text(
                            text = taskTypeLabel(task.type),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${formatTaskDuration(metrics.durationMinutes * 60.0)} • ${oneDecimal(metrics.distanceMiles.coerceAtLeast(0.0))} mi",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    pickupCardSummary(task, displayStatus, machineNameContext)?.let { summary ->
                        Text(
                            text = summary,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AtLocationTaskStatusControl(
                        task = task,
                        displayStatus = displayStatus,
                        isCurrent = isCurrent,
                        isUpdating = isUpdating,
                        taskActions = taskActions,
                        onMarkCurrentTaskDone = onMarkCurrentTaskDone,
                        onCompleteCurrentInventoryTask = onCompleteCurrentInventoryTask
                    )
                    if (canExpand) {
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_left),
                            contentDescription = if (isExpanded) "Collapse task" else "Expand task",
                            modifier = Modifier
                                .size(28.dp)
                                .rotate(if (isExpanded) 90f else -90f)
                                .clickable { onToggleExpanded() }
                                .padding(6.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f)
                        )
                    }
                }
            }
            if (errorTaskId == task.id && !errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
            if (shouldShowDetails && canExpand) {
                TaskWorkDetails(
                    task = task,
                    displayStatus = displayStatus,
                    isCurrent = isCurrent,
                    isUpdating = isUpdating,
                    appLocation = appLocation,
                    taskActions = taskActions,
                    autoCalcCommission = autoCalcCommission,
                    refillInventoryState = refillInventoryState,
                    pickupInventoryState = pickupInventoryState,
                    warehouses = warehouses,
                    machineNameContext = machineNameContext,
                    onMarkCurrentTaskDone = onMarkCurrentTaskDone,
                    onCompleteCurrentInventoryTask = onCompleteCurrentInventoryTask,
                    onRefillQuantityChanged = onRefillQuantityChanged,
                    onRefillFinalStockChanged = onRefillFinalStockChanged,
                    onRefillSourceSelected = onRefillSourceSelected,
                    onPickupQuantityChanged = onPickupQuantityChanged
                )
            }
            if (shouldShowDetails && TaskStateHelpers.isCompleted(displayStatus)) {
                Spacer(modifier = Modifier.height(8.dp))
                TaskPhotoConfirmationSection(
                    task = task,
                    isUpdating = isUpdating,
                    onAddPhoto = onAddPhoto,
                    onRemovePhoto = onRemovePhoto
                )
            }
        }
    }
}

@Composable
private fun TaskWorkDetails(
    task: VendiTask,
    displayStatus: TaskStatus,
    isCurrent: Boolean,
    isUpdating: Boolean,
    appLocation: AppLocation?,
    taskActions: TaskCardActions,
    autoCalcCommission: Boolean,
    refillInventoryState: RefillInventoryUiState,
    pickupInventoryState: PickupInventoryUiState,
    warehouses: List<WarehouseOption>,
    machineNameContext: String?,
    onMarkCurrentTaskDone: () -> Unit,
    onCompleteCurrentInventoryTask: () -> Unit,
    onRefillQuantityChanged: (String, String) -> Unit,
    onRefillFinalStockChanged: (String, String) -> Unit,
    onRefillSourceSelected: (RefillInventorySourceMode, String?) -> Unit,
    onPickupQuantityChanged: (String, String) -> Unit
) {
    when (task.type) {
        TaskType.MachineCollection,
        TaskType.MachineRefund -> {
            Spacer(modifier = Modifier.height(8.dp))
            TaskRowView(
                task = task,
                showAssignee = false,
                showCompletedMetrics = false,
                isUpdating = isUpdating,
                taskActions = taskActions,
                appLocation = appLocation,
                autoCalcCommission = autoCalcCommission,
                showsTaskIdentity = false,
                showsStatusControl = false
            )
        }
        TaskType.MachineRefill -> {
            Spacer(modifier = Modifier.height(8.dp))
            if (isCurrent && !TaskStateHelpers.isFinal(displayStatus)) {
                RefillInventoryCompletionView(
                    state = refillInventoryState,
                    warehouses = warehouses,
                    canComplete = refillInventoryState.taskId == task.id,
                    onRefilledChanged = onRefillQuantityChanged,
                    onFinalStockChanged = onRefillFinalStockChanged,
                    onSourceSelected = onRefillSourceSelected,
                    onComplete = onCompleteCurrentInventoryTask
                )
            } else if (refillInventoryState.taskId == task.id && refillInventoryState.lines.isNotEmpty()) {
                RefillInventoryCompletionView(
                    state = refillInventoryState,
                    warehouses = warehouses,
                    canComplete = false,
                    onRefilledChanged = onRefillQuantityChanged,
                    onFinalStockChanged = onRefillFinalStockChanged,
                    onSourceSelected = onRefillSourceSelected,
                    onComplete = {}
                )
            } else {
                RefillInventorySourceSummary(task = task)
                CompletedInventorySummary(task = task)
            }
        }
        TaskType.MachinePickupInventory -> {
            if (isCurrent && !TaskStateHelpers.isFinal(displayStatus)) {
                Spacer(modifier = Modifier.height(8.dp))
                PickupInventoryCompletionView(
                    state = pickupInventoryState,
                    canComplete = pickupInventoryState.taskId == task.id,
                    onPickedUpChanged = onPickupQuantityChanged,
                    onComplete = onCompleteCurrentInventoryTask
                )
            } else {
                PickupInventoryLineSummary(task = task, displayStatus = displayStatus, machineNameContext = machineNameContext)
                CompletedInventorySummary(task = task)
            }
        }
        else -> {
            if (isCurrent && !TaskStateHelpers.isFinal(displayStatus)) {
                Spacer(modifier = Modifier.height(8.dp))
                PrimaryActionButton(
                    text = "Mark done",
                    onClick = onMarkCurrentTaskDone,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun CompletedInventorySummary(task: VendiTask) {
    val items = task.inventoryCompletion?.items.orEmpty()
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        items.forEach { item ->
            CompletedInventoryLineRow(item)
        }
    }
}

@Composable
private fun PickupInventoryLineSummary(
    task: VendiTask,
    displayStatus: TaskStatus,
    machineNameContext: String?
) {
    val lines = pickupLinesForMachine(task, machineNameContext)
    if (lines.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        lines
            .groupBy { it.product.id }
            .forEach { (_, productLines) ->
                val product = productLines.first().product
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = productTitle(product.name, product.brand, product.size),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = pickupQuantitySummary(productLines, displayStatus),
                            color = if (TaskStateHelpers.isFinal(displayStatus)) {
                                if (displayStatus == TaskStatus.Done) AppColors.statusDone else AppColors.statusError
                            } else {
                                AppColors.statusPending
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    productLines.forEach { line ->
                        Text(
                            text = pickupLineSubtitle(line, displayStatus),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
    }
}

private fun pickupCardSummary(task: VendiTask, displayStatus: TaskStatus, machineNameContext: String?): String? {
    if (task.type != TaskType.MachinePickupInventory) return null
    val lines = pickupLinesForMachine(task, machineNameContext)
    if (lines.isEmpty()) return null
    val productCount = lines.map { it.product.id }.distinct().size
    val quantity = if (TaskStateHelpers.isFinal(displayStatus)) {
        lines.sumOf { (it.pickedUpQuantity ?: 0).coerceAtLeast(0) }
    } else {
        lines.sumOf { it.suggestedQuantity.coerceAtLeast(0) }
    }
    val quantityLabel = if (TaskStateHelpers.isFinal(displayStatus)) "Picked up $quantity" else "Suggested +$quantity"
    return "$productCount ${if (productCount == 1) "product" else "products"} • $quantityLabel"
}

private fun pickupLinesForMachine(task: VendiTask, machineNameContext: String?): List<TaskPickupLine> {
    if (task.type != TaskType.MachinePickupInventory) return emptyList()
    val lines = task.pickupLines
    val normalizedMachineName = machineNameContext?.trim()?.takeIf { it.isNotBlank() } ?: return lines
    val machineLines = lines.filter { line ->
        line.machineName?.trim()?.equals(normalizedMachineName, ignoreCase = true) == true
    }
    return machineLines.ifEmpty { lines }
}

private fun pickupQuantitySummary(lines: List<TaskPickupLine>, displayStatus: TaskStatus): String {
    val quantity = if (TaskStateHelpers.isFinal(displayStatus)) {
        lines.sumOf { (it.pickedUpQuantity ?: 0).coerceAtLeast(0) }
    } else {
        lines.sumOf { it.suggestedQuantity.coerceAtLeast(0) }
    }
    return if (TaskStateHelpers.isFinal(displayStatus)) "Picked up $quantity" else "Suggested +$quantity"
}

private fun pickupLineSubtitle(line: TaskPickupLine, displayStatus: TaskStatus): String {
    val stockText = TaskPickupLineFormatters.stockText(line)
    val quantityText = if (TaskStateHelpers.isFinal(displayStatus)) "" else TaskPickupLineFormatters.suggestedText(line)
    return listOf(stockText, quantityText)
        .filter { it.isNotBlank() }
        .joinToString(" • ")
}

private fun productTitle(name: String, brand: String?, size: String?): String {
    return listOf(name, brand, size)
        .filter { it?.isNotBlank() == true }
        .joinToString(" • ")
}

@Composable
private fun CompletedInventoryLineRow(item: TaskInventoryCompletionLine) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = listOf(item.product.name, item.product.brand, item.product.size)
                    .filter { it?.isNotBlank() == true }
                    .joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Started ${item.stockBefore} • Final ${item.finalStock}",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            text = "+${item.refilledQuantity}",
            color = AppColors.statusDone,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RefillInventorySourceSummary(task: VendiTask) {
    val sourceText = when (task.inventorySourceMode) {
        RefillInventorySourceMode.Warehouse -> {
            "Inventory source: ${task.inventorySourceWarehouseName ?: "Warehouse"}"
        }
        RefillInventorySourceMode.Untracked -> "Inventory source: Untracked stock"
        null -> null
    }
    if (sourceText != null) {
        Text(
            text = sourceText,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ServiceHeader(
    task: VendiTask,
    metrics: ExecutionScopeMetrics,
    displayStatus: TaskStatus,
    badgeState: ExecutionScopeServiceBadgeState?,
    completedChildCount: Int,
    totalChildCount: Int
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(8.dp),
                    shape = CircleShape,
                    color = TaskStatusPresentation.indicatorColor(displayStatus)
                ) {}
                Text(
                    text = taskTypeLabel(task.type),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${formatTaskDuration(metrics.durationMinutes * 60.0)} • ${oneDecimal(metrics.distanceMiles.coerceAtLeast(0.0))} mi",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                if (totalChildCount > 0) {
                    Text(
                        text = "• $completedChildCount/$totalChildCount",
                        color = AppColors.statusDone,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        badgeState?.let { ServiceBundleBadge(state = it) }
    }
}

@Composable
private fun MachineStatusBadge(status: TaskStatus) {
    if (status == TaskStatus.Done) {
        Surface(modifier = Modifier.size(21.dp), shape = CircleShape, color = AppColors.statusDone) {
            Box(contentAlignment = Alignment.Center) {
                Text("✓", color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(7.dp),
            shape = CircleShape,
            color = TaskStatusPresentation.indicatorColor(status)
        ) {}
        Text(
            text = TaskStatusPresentation.label(status),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ServiceBundleBadge(state: ExecutionScopeServiceBadgeState) {
    val label = when (state) {
        ExecutionScopeServiceBadgeState.Done -> "Done"
        ExecutionScopeServiceBadgeState.Cancelled -> "Cancelled"
        ExecutionScopeServiceBadgeState.Error -> "Error"
        ExecutionScopeServiceBadgeState.Saving -> "Saving"
        ExecutionScopeServiceBadgeState.Pending -> "Pending"
    }
    val color = when (state) {
        ExecutionScopeServiceBadgeState.Done -> AppColors.statusDone
        ExecutionScopeServiceBadgeState.Cancelled,
        ExecutionScopeServiceBadgeState.Error -> AppColors.statusError
        ExecutionScopeServiceBadgeState.Saving,
        ExecutionScopeServiceBadgeState.Pending -> AppColors.statusPending
    }
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(modifier = Modifier.size(7.dp), shape = CircleShape, color = color) {}
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun AtLocationTaskStatusControl(
    task: VendiTask,
    displayStatus: TaskStatus,
    isCurrent: Boolean,
    isUpdating: Boolean,
    taskActions: TaskCardActions,
    onMarkCurrentTaskDone: () -> Unit,
    onCompleteCurrentInventoryTask: () -> Unit
) {
    if (displayStatus == TaskStatus.Done) {
        Surface(modifier = Modifier.size(21.dp), shape = CircleShape, color = AppColors.statusDone) {
            Box(contentAlignment = Alignment.Center) {
                Text("✓", color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
        }
        return
    }
    if (isCurrent &&
        !TaskStateHelpers.isFinal(displayStatus) &&
        taskActions.canChangeStatus(task)
    ) {
        TaskStatusMenu(
            status = displayStatus,
            isUpdating = isUpdating,
            onSelect = { status ->
                when {
                    status == TaskStatus.Done && (task.type == TaskType.MachineRefill || task.type == TaskType.MachinePickupInventory) -> {
                        onCompleteCurrentInventoryTask()
                    }
                    status == TaskStatus.Done -> onMarkCurrentTaskDone()
                    else -> taskActions.onStatusChange(task, status)
                }
            }
        )
    } else {
        TaskStatusBadge(displayStatus)
    }
}

@Composable
private fun TaskReadonlyDetails(task: VendiTask) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (!task.machineName.isNullOrBlank()) {
            Text(
                text = task.machineName,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = task.displayTitle,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun AtLocationSectionCard(
    section: ExecutionScopeMachineSection? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.card,
        color = sectionCardColor(section),
        border = BorderStroke(1.dp, sectionCardBorder(section))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            content = content
        )
    }
}

@Composable
internal fun TaskPhotoConfirmationSection(
    task: VendiTask,
    isUpdating: Boolean,
    onAddPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasPhoto = task.photoConfirmationAsset != null
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        task.photoConfirmationAsset?.url?.let { url ->
            RemoteImagePreview(
                url = url,
                contentDescription = "Task confirmation photo",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .clip(AppShapes.card),
                placeholder = {
                    PhotoPlaceholder(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(132.dp)
                    )
                }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (hasPhoto) {
                OutlinedButton(
                    onClick = onAddPhoto,
                    enabled = !isUpdating,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isUpdating) "Saving..." else "Replace photo")
                }
                OutlinedButton(
                    onClick = onRemovePhoto,
                    enabled = !isUpdating,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Remove")
                }
            } else {
                OutlinedButton(
                    onClick = onAddPhoto,
                    enabled = !isUpdating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isUpdating) "Saving..." else "Add photo")
                }
            }
        }
    }
}

@Composable
internal fun PhotoPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(AppShapes.card)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

@Composable
private fun sectionCardColor(section: ExecutionScopeMachineSection?): Color {
    return when {
        section == null -> MaterialTheme.colorScheme.surface
        section.hasErrorOrCancelled -> AppColors.statusError.copy(alpha = 0.08f)
        section.allDone -> AppColors.statusDone.copy(alpha = 0.08f)
        section.isActive -> AppColors.vendBlue.copy(alpha = 0.06f)
        else -> MaterialTheme.colorScheme.surface
    }
}

private fun sectionCardBorder(section: ExecutionScopeMachineSection?): Color {
    return when {
        section == null -> AppColors.border
        section.hasErrorOrCancelled -> AppColors.statusError.copy(alpha = 0.35f)
        section.allDone -> AppColors.statusDone.copy(alpha = 0.35f)
        section.isActive -> AppColors.vendBlue.copy(alpha = 0.42f)
        else -> AppColors.border
    }
}

private val ExecutionScopeMachineSection.allDone: Boolean
    get() {
        val serviceDone = serviceTask == null || serviceTask.status == TaskStatus.Done
        return serviceDone && childCards.isNotEmpty() && childCards.all { it.displayStatus == TaskStatus.Done }
    }

private val ExecutionScopeMachineSection.hasErrorOrCancelled: Boolean
    get() = serviceTask?.status == TaskStatus.Error ||
        serviceTask?.status == TaskStatus.Cancelled ||
        childCards.any { it.displayStatus == TaskStatus.Error || it.displayStatus == TaskStatus.Cancelled }

private fun ExecutionScopeMachineSection.machineDisplayStatus(): TaskStatus {
    val statuses = listOfNotNull(serviceDisplayStatus ?: serviceTask?.status) + childCards.map { it.displayStatus }
    return when {
        statuses.any { !TaskStateHelpers.isFinal(it) } -> TaskStatus.Pending
        statuses.any { it == TaskStatus.Error } -> TaskStatus.Error
        statuses.any { it == TaskStatus.Cancelled } -> TaskStatus.Cancelled
        statuses.isNotEmpty() -> TaskStatus.Done
        else -> TaskStatus.Pending
    }
}

@Composable
private fun atLocationTaskCardColor(displayStatus: TaskStatus, isCurrent: Boolean): Color {
    return when {
        displayStatus == TaskStatus.Done -> AppColors.statusDone.copy(alpha = 0.08f)
        displayStatus == TaskStatus.Cancelled -> AppColors.statusError.copy(alpha = 0.08f)
        displayStatus == TaskStatus.Error -> AppColors.statusError.copy(alpha = 0.10f)
        isCurrent -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surface
    }
}

private fun atLocationTaskCardBorder(displayStatus: TaskStatus, isCurrent: Boolean): Color {
    return when {
        displayStatus == TaskStatus.Done -> AppColors.statusDone.copy(alpha = 0.35f)
        displayStatus == TaskStatus.Cancelled || displayStatus == TaskStatus.Error -> AppColors.statusError.copy(alpha = 0.35f)
        isCurrent -> AppColors.vendBlue.copy(alpha = 0.45f)
        else -> AppColors.border
    }
}

private fun VendiTask.canExpand(displayStatus: TaskStatus): Boolean {
    return TaskStateHelpers.isFinal(displayStatus) ||
        type == com.vendistri.operations.features.tasks.TaskType.MachineCollection ||
        type == com.vendistri.operations.features.tasks.TaskType.MachineRefund ||
        type == com.vendistri.operations.features.tasks.TaskType.MachineRefill ||
        type == com.vendistri.operations.features.tasks.TaskType.MachinePickupInventory
}

internal data class AtLocationTaskListDisplay(
    val aggregatePickupCards: List<ExecutionScopeTaskCard>,
    val machineSections: List<ExecutionScopeMachineSection>
)

internal fun atLocationTaskListDisplay(
    machineSections: List<ExecutionScopeMachineSection>,
    primaryTaskIds: List<String>,
    aggregatePickupInventoryCards: Boolean
): AtLocationTaskListDisplay {
    val aggregatePickupCards = if (aggregatePickupInventoryCards) {
        stableTaskCards(
            machineSections
                .flatMap { it.childCards }
                .filter { it.task.type == TaskType.MachinePickupInventory },
            primaryTaskIds = primaryTaskIds
        )
    } else {
        emptyList()
    }
    val displaySections = machineSections.mapNotNull { section ->
        val childCards = section.childCards.filter { card ->
            when {
                card.task.type != TaskType.MachinePickupInventory -> true
                aggregatePickupInventoryCards -> false
                TaskStateHelpers.isFinal(card.displayStatus) -> false
                else -> true
            }
        }
        when {
            childCards.size == section.childCards.size -> section
            childCards.isEmpty() && section.serviceTask == null -> null
            else -> section.copy(
                childCards = childCards,
                serviceCompletedChildCount = childCards.count { TaskStateHelpers.isFinal(it.displayStatus) },
                serviceTotalChildCount = childCards.size,
                machineMetrics = section.serviceMetrics.orZero().plus(childCards.map { it.metrics })
            )
        }
    }
    return AtLocationTaskListDisplay(
        aggregatePickupCards = aggregatePickupCards,
        machineSections = displaySections
    )
}

private fun stableTaskCards(
    cards: List<ExecutionScopeTaskCard>,
    primaryTaskIds: List<String>
): List<ExecutionScopeTaskCard> {
    val primaryOrder = primaryTaskIds.withIndex().associate { it.value to it.index }
    val seen = mutableSetOf<String>()
    return cards
        .mapIndexed { index, card -> index to card }
        .filter { (_, card) -> seen.add(card.task.id) }
        .sortedWith(
            compareBy<Pair<Int, ExecutionScopeTaskCard>> { (_, card) -> primaryOrder[card.task.id] ?: Int.MAX_VALUE }
                .thenBy { it.first }
        )
        .map { it.second }
}

private fun ExecutionScopeMetrics?.orZero(): ExecutionScopeMetrics {
    return this ?: ExecutionScopeMetrics.Zero
}

private fun ExecutionScopeMetrics.plus(metrics: List<ExecutionScopeMetrics>): ExecutionScopeMetrics {
    return metrics.fold(this) { total, next -> total.adding(next) }
}

private fun Set<String>.toggled(taskId: String): Set<String> {
    return if (taskId in this) this - taskId else this + taskId
}
