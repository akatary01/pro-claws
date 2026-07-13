package com.vendistri.operations.features.work

import com.vendistri.operations.features.map.LocationCoordinate
import com.vendistri.operations.features.tasks.TaskFinancialHelpers
import com.vendistri.operations.features.tasks.TaskMachineGroup
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask

enum class WorkPhase {
    Idle,
    Summary,
    PreparingRoute,
    NavigatingToWarehouse,
    AtWarehouse,
    NavigatingToLocation,
    AtLocation,
    Completing
}

val WorkPhase.isNavigating: Boolean
    get() = this == WorkPhase.NavigatingToWarehouse || this == WorkPhase.NavigatingToLocation

val WorkPhase.isAtDestination: Boolean
    get() = this == WorkPhase.AtWarehouse || this == WorkPhase.AtLocation

enum class GoNodeType {
    Location,
    Pickup,
    Dropoff,
    Validation
}

enum class WorkDestinationKind {
    Location,
    Warehouse;

    val navigatingPhase: WorkPhase
        get() = when (this) {
            Location -> WorkPhase.NavigatingToLocation
            Warehouse -> WorkPhase.NavigatingToWarehouse
        }

    val arrivedPhase: WorkPhase
        get() = when (this) {
            Location -> WorkPhase.AtLocation
            Warehouse -> WorkPhase.AtWarehouse
        }
}

data class GoNode(
    val id: String,
    val type: GoNodeType,
    val title: String,
    val subtitle: String?,
    val coordinate: LocationCoordinate,
    val locationId: String?,
    val taskIds: List<String>
)

data class GoStopPlan(
    val id: String,
    val targetLocationId: String,
    val title: String,
    val addressStreetLine: String?,
    val addressCityStateZipLine: String?,
    val tasks: List<VendiTask>,
    val nodes: List<GoNode>,
    val machineGroups: List<TaskMachineGroup>,
    val gross: Double,
    val refunds: Double,
    val commission: Double,
    val net: Double
) {
    val primaryNode: GoNode?
        get() = nodes.firstOrNull()

    val coordinate: LocationCoordinate?
        get() = primaryNode?.coordinate

    val machineCount: Int
        get() = machineGroups.size

    val taskCount: Int
        get() = tasks.size

    val destinationKind: WorkDestinationKind
        get() = when (primaryNode?.type) {
            GoNodeType.Pickup -> WorkDestinationKind.Warehouse
            else -> WorkDestinationKind.Location
        }
}

data class GoSummary(
    val locations: Int,
    val machines: Int,
    val tasks: Int,
    val gross: Double,
    val refunds: Double,
    val commission: Double,
    val net: Double
)

data class GoPlan(
    val generatedAtEpochMillis: Long,
    val tasks: List<VendiTask>,
    val stops: List<GoStopPlan>,
    val suggestedStopId: String?
) {
    val summary: GoSummary
        get() {
            val financials = TaskFinancialHelpers.sumTaskFinancials(tasks)
            return GoSummary(
                locations = stops.size,
                machines = tasks.mapNotNull { it.machine }.toSet().size,
                tasks = tasks.size,
                gross = financials.gross,
                refunds = financials.refunds,
                commission = financials.commission,
                net = financials.net
            )
        }
}

data class RoutePreview(
    val distanceMiles: Double,
    val expectedTravelSeconds: Double
) {
    fun adding(next: RoutePreview): RoutePreview {
        return RoutePreview(
            distanceMiles = distanceMiles + next.distanceMiles,
            expectedTravelSeconds = expectedTravelSeconds + next.expectedTravelSeconds
        )
    }
}

data class ActiveWorkSession(
    val id: String,
    val title: String,
    val locationId: String?,
    val activeTaskIds: Set<String>,
    val addressStreetLine: String? = null,
    val addressCityStateZipLine: String? = null,
    val coordinate: LocationCoordinate? = null,
    val destinationKind: WorkDestinationKind = WorkDestinationKind.Location
)

data class ExecutionTaskItem(
    val id: String,
    val type: TaskType,
    val status: TaskStatus,
    val machineId: String?,
    val machineName: String?,
    val startedAt: String?,
    val doneAt: String?,
    val isWrapper: Boolean
)

data class ActiveTaskExecution(
    val stopId: String,
    val title: String,
    val locationId: String?,
    val destinationKind: WorkDestinationKind,
    val taskIds: List<String>,
    val wrapperTaskId: String?,
    val displayTasks: List<VendiTask>,
    val tasks: List<ExecutionTaskItem>,
    val machineGroups: List<TaskMachineGroup>,
    val currentTaskId: String?,
    val currentTaskIndex: Int,
    val totalTaskCount: Int,
    val taskStartDistanceMilesByTaskId: Map<String, Double> = emptyMap(),
    val distanceMiles: Double = 0.0,
    val gross: Double,
    val refunds: Double,
    val commission: Double,
    val net: Double
)

data class PostPickupDestination(
    val refillTaskId: String?,
    val stopId: String?,
    val sessionTaskIds: Set<String>
)

data class LocalActiveExecutionSession(
    val deviceId: String,
    val userId: String,
    val stopId: String,
    val locationId: String?,
    val taskIds: Set<String>,
    val currentTaskId: String?,
    val phase: WorkPhase,
    val startedAtEpochMillis: Long,
    val distanceMiles: Double = 0.0,
    val taskStartDistanceMilesByTaskId: Map<String, Double> = emptyMap(),
    val postPickupDestination: PostPickupDestination? = null
)

data class WorkUiState(
    val phase: WorkPhase = WorkPhase.Idle,
    val goPlan: GoPlan? = null,
    val selectedStopId: String? = null,
    val selectedTaskId: String? = null,
    val routePreview: RoutePreview? = null,
    val activeSession: ActiveWorkSession? = null,
    val selectedStop: GoStopPlan? = null,
    val routeStartScopeDecision: RouteStartScopeDecision? = null,
    val selectedRouteStartScopeChoice: RouteStartScopeChoice? = null,
    val selectedLaterRefillTaskIds: Set<String> = emptySet(),
    val activeExecution: ActiveTaskExecution? = null,
    val localActiveExecutionSession: LocalActiveExecutionSession? = null,
    val postPickupDestination: PostPickupDestination? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
