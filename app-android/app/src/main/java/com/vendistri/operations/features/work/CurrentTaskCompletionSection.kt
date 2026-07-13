package com.vendistri.operations.features.work

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vendistri.operations.features.location.WarehouseOption
import com.vendistri.operations.features.pickup.PickupInventoryCompletionView
import com.vendistri.operations.features.pickup.PickupInventoryUiState
import com.vendistri.operations.features.refill.RefillInventoryCompletionView
import com.vendistri.operations.features.refill.RefillInventoryUiState
import com.vendistri.operations.features.tasks.RefillInventorySourceMode
import com.vendistri.operations.features.tasks.TaskStateHelpers
import com.vendistri.operations.features.tasks.TaskStatusPresentation
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask
import com.vendistri.operations.features.tasks.formatTaskDuration
import com.vendistri.operations.features.tasks.oneDecimal
import kotlinx.coroutines.delay

@Composable
internal fun CurrentTaskCompletionSection(
    execution: ActiveTaskExecution,
    currentTask: VendiTask?,
    refillInventoryState: RefillInventoryUiState,
    pickupInventoryState: PickupInventoryUiState,
    warehouses: List<WarehouseOption>,
    onMarkCurrentTaskDone: () -> Unit,
    onCompleteCurrentInventoryTask: () -> Unit,
    onAdvanceTask: () -> Unit,
    onRefillQuantityChanged: (String, String) -> Unit,
    onRefillFinalStockChanged: (String, String) -> Unit,
    onRefillSourceSelected: (RefillInventorySourceMode, String?) -> Unit,
    onPickupQuantityChanged: (String, String) -> Unit,
    pendingMutationTaskIds: Set<String>,
    onAddPhoto: (VendiTask) -> Unit,
    onRemovePhoto: (VendiTask) -> Unit
) {
    if (currentTask == null) {
        OutlinedButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("All tasks done")
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = currentTask.executionSectionTitle(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                CurrentTaskSubtitle(task = currentTask, execution = execution)
            }
            CurrentTaskStatus(task = currentTask)
        }
        Spacer(modifier = Modifier.height(2.dp))
        when (currentTask.type) {
            TaskType.MachineRefill -> RefillTaskCompletionSection(
                currentTask = currentTask,
                refillInventoryState = refillInventoryState,
                warehouses = warehouses,
                onCompleteCurrentInventoryTask = onCompleteCurrentInventoryTask,
                onRefillQuantityChanged = onRefillQuantityChanged,
                onRefillFinalStockChanged = onRefillFinalStockChanged,
                onRefillSourceSelected = onRefillSourceSelected
            )
            TaskType.MachinePickupInventory -> PickupTaskCompletionSection(
                currentTask = currentTask,
                pickupInventoryState = pickupInventoryState,
                onCompleteCurrentInventoryTask = onCompleteCurrentInventoryTask,
                onPickupQuantityChanged = onPickupQuantityChanged
            )
            else -> SimpleTaskCompletionSection(
                currentTask = currentTask,
                onMarkCurrentTaskDone = onMarkCurrentTaskDone
            )
        }
        if (TaskStateHelpers.isCompleted(currentTask.status)) {
            Spacer(modifier = Modifier.height(4.dp))
            TaskPhotoConfirmationSection(
                task = currentTask,
                isUpdating = currentTask.id in pendingMutationTaskIds,
                onAddPhoto = { onAddPhoto(currentTask) },
                onRemovePhoto = { onRemovePhoto(currentTask) },
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun CurrentTaskSubtitle(task: VendiTask, execution: ActiveTaskExecution) {
    if (task.type == TaskType.MachinePickupInventory) {
        var nowMillis by remember(task.id) { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(task.id, execution.distanceMiles) {
            while (true) {
                nowMillis = System.currentTimeMillis()
                delay(1_000L)
            }
        }
        val metrics = TaskExecutionMetrics.taskMetrics(
            task = task,
            displayStatus = task.status,
            execution = execution,
            nowEpochMillis = nowMillis
        )
        Text(
            text = "${formatTaskDuration(metrics.durationMinutes * 60.0)} • ${oneDecimal(metrics.distanceMiles.coerceAtLeast(0.0))} mi",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            style = MaterialTheme.typography.bodySmall
        )
    } else {
        Text(
            text = task.displayMachine,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun CurrentTaskStatus(task: VendiTask) {
    if (task.status == com.vendistri.operations.features.tasks.TaskStatus.Done) {
        Surface(
            modifier = Modifier.size(21.dp),
            shape = CircleShape,
            color = TaskStatusPresentation.indicatorColor(task.status)
        ) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "✓",
                    color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(7.dp),
            shape = CircleShape,
            color = TaskStatusPresentation.indicatorColor(task.status)
        ) {}
        Text(
            text = TaskStatusPresentation.label(task.status),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun VendiTask.executionSectionTitle(): String {
    return when (type) {
        TaskType.MachinePickupInventory -> "Pickup Inventory"
        TaskType.MachineRefill -> "Refill Inventory"
        else -> displayTitle
    }
}

@Composable
private fun RefillTaskCompletionSection(
    currentTask: VendiTask,
    refillInventoryState: RefillInventoryUiState,
    warehouses: List<WarehouseOption>,
    onCompleteCurrentInventoryTask: () -> Unit,
    onRefillQuantityChanged: (String, String) -> Unit,
    onRefillFinalStockChanged: (String, String) -> Unit,
    onRefillSourceSelected: (RefillInventorySourceMode, String?) -> Unit
) {
    RefillInventoryCompletionView(
        state = refillInventoryState,
        warehouses = warehouses,
        canComplete = !TaskStateHelpers.isFinal(currentTask.status),
        onRefilledChanged = onRefillQuantityChanged,
        onFinalStockChanged = onRefillFinalStockChanged,
        onSourceSelected = onRefillSourceSelected,
        onComplete = onCompleteCurrentInventoryTask
    )
}

@Composable
private fun PickupTaskCompletionSection(
    currentTask: VendiTask,
    pickupInventoryState: PickupInventoryUiState,
    onCompleteCurrentInventoryTask: () -> Unit,
    onPickupQuantityChanged: (String, String) -> Unit
) {
    val displayState = if (
        pickupInventoryState.taskId != currentTask.id &&
        !TaskStateHelpers.isFinal(currentTask.status)
    ) {
        pickupInventoryState.copy(isLoading = true, errorMessage = null)
    } else {
        pickupInventoryState
    }
    PickupInventoryCompletionView(
        state = displayState,
        canComplete = !TaskStateHelpers.isFinal(currentTask.status),
        onPickedUpChanged = onPickupQuantityChanged,
        onComplete = onCompleteCurrentInventoryTask
    )
}

@Composable
private fun SimpleTaskCompletionSection(
    currentTask: VendiTask,
    onMarkCurrentTaskDone: () -> Unit
) {
    com.vendistri.operations.components.PrimaryActionButton(
        text = "Mark done",
        onClick = onMarkCurrentTaskDone,
        modifier = Modifier.fillMaxWidth(),
        enabled = !TaskStateHelpers.isFinal(currentTask.status)
    )
}
