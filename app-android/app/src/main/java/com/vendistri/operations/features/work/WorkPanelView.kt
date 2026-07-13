package com.vendistri.operations.features.work

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vendistri.operations.components.PrimaryActionButton
import com.vendistri.operations.features.location.WarehouseOption
import com.vendistri.operations.features.pickup.PickupInventoryUiState
import com.vendistri.operations.features.refill.RefillInventoryUiState
import com.vendistri.operations.features.tasks.RefillInventorySourceMode
import com.vendistri.operations.features.tasks.TaskCardActions
import com.vendistri.operations.features.tasks.TaskSummary
import com.vendistri.operations.features.tasks.VendiTask

@Composable
fun WorkPanelView(
    state: WorkUiState,
    taskSummary: TaskSummary,
    refillDecisionState: RefillDecisionUiState,
    refillInventoryState: RefillInventoryUiState,
    pickupInventoryState: PickupInventoryUiState,
    warehouses: List<WarehouseOption>,
    allTasks: List<VendiTask> = emptyList(),
    pendingMutationTaskIds: Set<String>,
    taskActions: TaskCardActions,
    autoCalcCommission: Boolean,
    onStartNavigation: () -> Unit = {},
    onRefillDecisionActionSelected: (RefillDecisionAction) -> Unit = {},
    onRefillDecisionWarehouseSelected: (String) -> Unit = {},
    onApplyRefillDecision: () -> Unit = {},
    onArriveAtLocation: () -> Unit = {},
    onPrepareCurrentInventoryTask: () -> Unit = {},
    onMarkCurrentTaskDone: () -> Unit = {},
    onCompleteCurrentInventoryTask: () -> Unit = {},
    onAdvanceTask: () -> Unit = {},
    onRefillQuantityChanged: (String, String) -> Unit = { _, _ -> },
    onRefillFinalStockChanged: (String, String) -> Unit = { _, _ -> },
    onRefillSourceSelected: (RefillInventorySourceMode, String?) -> Unit = { _, _ -> },
    onPickupQuantityChanged: (String, String) -> Unit = { _, _ -> },
    onFinishVisit: () -> Unit = {},
    onCancelTasks: (List<VendiTask>) -> Unit = {},
    onAddPhoto: (VendiTask) -> Unit = {},
    onRemovePhoto: (VendiTask) -> Unit = {},
    onApplySharedNotes: suspend (List<String>, String?) -> Boolean = { _, _ -> false },
    onStopSession: () -> Unit = {}
) {
    Column {
        Text(
            text = "Work",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(12.dp))
        WorkMetricRow(label = "Phase", value = state.phaseLabel)
        WorkMetricRow(label = "Open tasks", value = taskSummary.open.toString())
        WorkMetricRow(label = "Active tasks", value = taskSummary.inProgress.toString())
        state.activeSession?.let { session ->
            WorkMetricRow(label = "Current stop", value = session.title)
            WorkMetricRow(label = "Tasks in stop", value = session.activeTaskIds.size.toString())
            session.addressText?.let { WorkMetricRow(label = "Address", value = it) }
        }
        if (state.phase == WorkPhase.PreparingRoute && refillDecisionState.isVisible) {
            Spacer(modifier = Modifier.height(12.dp))
            RefillDecisionView(
                state = refillDecisionState,
                onActionSelected = onRefillDecisionActionSelected,
                onWarehouseSelected = onRefillDecisionWarehouseSelected,
                onApply = onApplyRefillDecision
            )
        }
        state.activeExecution?.let { execution ->
            Spacer(modifier = Modifier.height(8.dp))
            when (state.destinationKind) {
                WorkDestinationKind.Warehouse -> AtWarehouseExecutionView(
                    execution = execution,
                    refillInventoryState = refillInventoryState,
                    pickupInventoryState = pickupInventoryState,
                    warehouses = warehouses,
                    allTasks = allTasks,
                    postPickupDestination = state.postPickupDestination,
                    pendingMutationTaskIds = pendingMutationTaskIds,
                    errorMessage = state.errorMessage,
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
                    onApplySharedNotes = onApplySharedNotes
                )
                WorkDestinationKind.Location -> AtLocationExecutionView(
                    execution = execution,
                    refillInventoryState = refillInventoryState,
                    pickupInventoryState = pickupInventoryState,
                    warehouses = warehouses,
                    allTasks = allTasks,
                    postPickupDestination = state.postPickupDestination,
                    pendingMutationTaskIds = pendingMutationTaskIds,
                    errorMessage = state.errorMessage,
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
                    onApplySharedNotes = onApplySharedNotes
                )
            }
        }
        state.errorMessage?.let {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (state.activeSession == null) {
            PrimaryActionButton(
                text = "Select a map stop to start",
                onClick = {},
                enabled = false
            )
        } else {
            WorkPhaseActions(
                phase = state.phase,
                destinationKind = state.destinationKind,
                hasRefillDecision = refillDecisionState.isVisible,
                onStartNavigation = onStartNavigation,
                onArriveAtLocation = onArriveAtLocation
            )
            TextButton(onClick = onStopSession) {
                Text("Stop session")
            }
        }
    }
}

@Composable
private fun WorkPhaseActions(
    phase: WorkPhase,
    destinationKind: WorkDestinationKind,
    hasRefillDecision: Boolean,
    onStartNavigation: () -> Unit,
    onArriveAtLocation: () -> Unit
) {
    when (phase) {
        WorkPhase.PreparingRoute -> PrimaryActionButton(
            text = if (hasRefillDecision) {
                "Choose refill route option"
            } else {
                "Start ${destinationKind.routeNoun}"
            },
            onClick = onStartNavigation,
            enabled = !hasRefillDecision
        )
        WorkPhase.NavigatingToWarehouse,
        WorkPhase.NavigatingToLocation -> {
            PrimaryActionButton(
                text = "I'm here",
                onClick = onArriveAtLocation
            )
        }
        WorkPhase.AtWarehouse,
        WorkPhase.AtLocation -> OutlinedButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("At ${destinationKind.placeNoun}")
        }
        WorkPhase.Summary -> OutlinedButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Review route")
        }
        WorkPhase.Completing -> OutlinedButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Completing")
        }
        WorkPhase.Idle -> Unit
    }
}

@Composable
private fun WorkMetricRow(label: String, value: String) {
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

private val WorkPhase.label: String
    get() = when (this) {
        WorkPhase.Idle -> "Idle"
        WorkPhase.Summary -> "Summary"
        WorkPhase.PreparingRoute -> "Preparing route"
        WorkPhase.NavigatingToWarehouse -> "Navigating to warehouse"
        WorkPhase.AtWarehouse -> "At warehouse"
        WorkPhase.NavigatingToLocation -> "Navigating to location"
        WorkPhase.AtLocation -> "At location"
        WorkPhase.Completing -> "Completing"
    }

private val WorkUiState.destinationKind: WorkDestinationKind
    get() = activeExecution?.destinationKind
        ?: selectedStop?.destinationKind
        ?: activeSession?.destinationKind
        ?: WorkDestinationKind.Location

private val WorkUiState.phaseLabel: String
    get() = when (phase) {
        WorkPhase.PreparingRoute -> "Preparing ${destinationKind.routeNoun}"
        WorkPhase.NavigatingToWarehouse,
        WorkPhase.NavigatingToLocation -> "Navigating to ${destinationKind.placeNoun}"
        WorkPhase.AtWarehouse,
        WorkPhase.AtLocation -> "At ${destinationKind.placeNoun}"
        else -> phase.label
    }

private val WorkDestinationKind.routeNoun: String
    get() = when (this) {
        WorkDestinationKind.Location -> "route"
        WorkDestinationKind.Warehouse -> "warehouse route"
    }

private val WorkDestinationKind.placeNoun: String
    get() = when (this) {
        WorkDestinationKind.Location -> "location"
        WorkDestinationKind.Warehouse -> "warehouse"
    }

private val ActiveWorkSession.addressText: String?
    get() = listOfNotNull(addressStreetLine, addressCityStateZipLine)
        .filter { it.isNotBlank() }
        .joinToString(", ")
        .ifBlank { null }
