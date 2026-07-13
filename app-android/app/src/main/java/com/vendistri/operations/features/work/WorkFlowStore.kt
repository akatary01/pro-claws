package com.vendistri.operations.features.work

import com.vendistri.operations.features.tasks.TaskStateHelpers
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WorkFlowStore {
    private val _state = MutableStateFlow(WorkUiState())
    val state: StateFlow<WorkUiState> = _state.asStateFlow()

    fun resetUserScopedState() {
        _state.value = WorkUiState()
    }

    fun restoreActiveSession(
        session: ActiveWorkSession?,
        phase: WorkPhase? = null,
        postPickupDestination: PostPickupDestination? = null,
        localActiveExecutionSession: LocalActiveExecutionSession? = null
    ) {
        _state.value = if (session == null) {
            WorkUiState(
                postPickupDestination = postPickupDestination,
                localActiveExecutionSession = localActiveExecutionSession
            )
        } else {
            val destinationKind = session.destinationKind
            val resolvedPhase = phase.restoreActiveRoutePhase(destinationKind)
            WorkUiState(
                phase = resolvedPhase,
                activeSession = session,
                localActiveExecutionSession = localActiveExecutionSession?.copy(phase = resolvedPhase),
                postPickupDestination = postPickupDestination
            )
        }
    }

    fun restoreActiveRoute(session: ActiveWorkSession, phase: WorkPhase, stop: GoStopPlan) {
        val resolvedPhase = phase.restoreActiveRoutePhase(stop.destinationKind)
        val localSession = _state.value.localActiveExecutionSession
        val restoredExecution = TaskExecutionPlanner.activeExecution(
            stop = stop,
            preferredTaskId = localSession?.currentTaskId
        ).let { execution ->
            execution.copy(
                distanceMiles = maxOf(execution.distanceMiles, localSession?.distanceMiles ?: 0.0),
                taskStartDistanceMilesByTaskId = localSession
                    ?.taskStartDistanceMilesByTaskId
                    .orEmpty()
                    .filterKeys { it in session.activeTaskIds }
            )
        }
        val restoredPhase = if (resolvedPhase.isNavigating && !restoredExecution.hasStartedRouteOwnerTask()) {
            WorkPhase.PreparingRoute
        } else {
            resolvedPhase
        }
        _state.value = _state.value.copy(
            phase = restoredPhase,
            selectedStop = stop,
            activeSession = session,
            selectedStopId = stop.id,
            localActiveExecutionSession = localSession?.copy(phase = restoredPhase),
            activeExecution = restoredExecution
        )
    }

    fun showSummary(plan: GoPlan, selectedStopId: String? = plan.suggestedStopId) {
        _state.value = _state.value.copy(
            phase = WorkPhase.Summary,
            goPlan = plan,
            selectedStopId = selectedStopId ?: plan.stops.firstOrNull()?.id,
            selectedStop = (selectedStopId ?: plan.stops.firstOrNull()?.id)?.let { stopId ->
                plan.stops.firstOrNull { it.id == stopId }
            },
            routeStartScopeDecision = null,
            selectedRouteStartScopeChoice = null,
            selectedLaterRefillTaskIds = emptySet(),
            activeSession = null,
            activeExecution = null,
            routePreview = null,
            errorMessage = null
        )
    }

    fun selectStop(stopId: String?) {
        val state = _state.value
        val stop = stopId?.let { id -> state.goPlan?.stops?.firstOrNull { it.id == id } }
        _state.value = state.copy(
            selectedStopId = stopId,
            selectedStop = stop,
            routeStartScopeDecision = null,
            selectedRouteStartScopeChoice = null,
            selectedLaterRefillTaskIds = emptySet(),
            routePreview = null
        )
    }

    fun rehydrateGoPlan(plan: GoPlan): Boolean {
        val state = _state.value
        if (state.goPlan == null) return false

        val currentSelectedStopId = state.selectedStopId
        val defaultStopId = plan.suggestedStopId ?: plan.stops.firstOrNull()?.id
        val nextSelectedStopId = when {
            currentSelectedStopId != null &&
                plan.stops.any { it.id == currentSelectedStopId || it.targetLocationId == currentSelectedStopId } -> {
                currentSelectedStopId
            }
            else -> defaultStopId
        }
        val nextSelectedStop = nextSelectedStopId?.let { stopId ->
            plan.stops.firstOrNull { it.id == stopId || it.targetLocationId == stopId }
        }

        _state.value = state.copy(
            goPlan = plan,
            selectedStopId = nextSelectedStopId,
            selectedStop = nextSelectedStop,
            routeStartScopeDecision = null,
            selectedRouteStartScopeChoice = null,
            selectedLaterRefillTaskIds = emptySet(),
            routePreview = null
        )
        return true
    }

    fun showRouteStartScopeDecision(decision: RouteStartScopeDecision) {
        _state.value = _state.value.copy(
            routeStartScopeDecision = decision,
            selectedRouteStartScopeChoice = decision.defaultChoice,
            selectedLaterRefillTaskIds = emptySet(),
            errorMessage = null
        )
    }

    fun clearRouteStartScopeDecision() {
        _state.value = _state.value.copy(
            routeStartScopeDecision = null,
            selectedRouteStartScopeChoice = null,
            selectedLaterRefillTaskIds = emptySet()
        )
    }

    fun selectRouteStartScopeChoice(choice: RouteStartScopeChoice) {
        _state.value = _state.value.copy(selectedRouteStartScopeChoice = choice, errorMessage = null)
    }

    fun toggleLaterRefillTask(taskId: String) {
        val selected = _state.value.selectedLaterRefillTaskIds
        _state.value = _state.value.copy(
            selectedLaterRefillTaskIds = if (taskId in selected) selected - taskId else selected + taskId,
            errorMessage = null
        )
    }

    fun returnToSummary() {
        val state = _state.value
        val plan = state.goPlan ?: return
        val selectedStopId = state.selectedStopId ?: state.selectedStop?.id ?: plan.suggestedStopId
        val selectedStop = selectedStopId?.let { stopId ->
            plan.stops.firstOrNull { it.id == stopId || it.targetLocationId == stopId }
        }
        _state.value = state.copy(
            phase = WorkPhase.Summary,
            selectedStopId = selectedStop?.id ?: selectedStopId,
            selectedStop = selectedStop,
            activeSession = null,
            activeExecution = null,
            routeStartScopeDecision = null,
            selectedRouteStartScopeChoice = null,
            selectedLaterRefillTaskIds = emptySet(),
            errorMessage = null,
            isLoading = false
        )
    }

    fun setError(message: String?) {
        _state.value = _state.value.copy(errorMessage = message)
    }

    fun setLoading(isLoading: Boolean) {
        _state.value = _state.value.copy(isLoading = isLoading)
    }

    fun setRoutePreview(routePreview: RoutePreview?) {
        _state.value = _state.value.copy(routePreview = routePreview)
    }

    fun prepareRoute(stop: GoStopPlan) {
        val taskIds = stop.tasks.map { it.id }.toSet()
        if (taskIds.isEmpty() || stop.coordinate == null) return
        val existing = _state.value
        val execution = TaskExecutionPlanner.activeExecution(stop).copy(
            distanceMiles = 0.0,
            taskStartDistanceMilesByTaskId = emptyMap()
        )
        _state.value = WorkUiState(
            phase = WorkPhase.PreparingRoute,
            goPlan = existing.goPlan,
            selectedStopId = stop.id,
            selectedStop = stop,
            routeStartScopeDecision = null,
            routePreview = existing.routePreview,
            postPickupDestination = existing.postPickupDestination,
            localActiveExecutionSession = null,
            activeSession = ActiveWorkSession(
                id = "location:${stop.id}",
                title = stop.title,
                locationId = stop.targetLocationId,
                activeTaskIds = taskIds,
                addressStreetLine = stop.addressStreetLine,
                addressCityStateZipLine = stop.addressCityStateZipLine,
                coordinate = stop.coordinate,
                destinationKind = stop.destinationKind
            ),
            activeExecution = execution
        )
    }

    fun startNavigation() {
        val state = _state.value
        val session = state.activeSession ?: return
        val navigationPhase = session.destinationKind.navigatingPhase
        _state.value = state.copy(
            phase = navigationPhase,
            activeSession = session,
            localActiveExecutionSession = state.localActiveExecutionSession?.copy(phase = navigationPhase)
        )
    }

    fun bindLocalActiveExecutionSession(deviceId: String, userId: String) {
        val state = _state.value
        val session = state.activeSession ?: return
        if (deviceId.isBlank() || userId.isBlank()) return
        _state.value = state.copy(
            localActiveExecutionSession = LocalActiveExecutionSession(
                deviceId = deviceId,
                userId = userId,
                stopId = state.selectedStop?.id ?: session.id,
                locationId = session.locationId,
                taskIds = session.activeTaskIds,
                currentTaskId = state.activeExecution?.currentTaskId,
                phase = state.phase,
                startedAtEpochMillis = state.localActiveExecutionSession?.startedAtEpochMillis
                    ?.takeIf { it > 0L }
                    ?: System.currentTimeMillis(),
                distanceMiles = state.activeExecution?.distanceMiles ?: state.localActiveExecutionSession?.distanceMiles ?: 0.0,
                taskStartDistanceMilesByTaskId = state.activeExecution?.taskStartDistanceMilesByTaskId
                    ?: state.localActiveExecutionSession?.taskStartDistanceMilesByTaskId.orEmpty(),
                postPickupDestination = state.postPickupDestination
            )
        )
    }

    fun setPostPickupDestination(refillTaskId: String?, stopId: String?, sessionTaskIds: Set<String>) {
        _state.value = _state.value.copy(
            postPickupDestination = PostPickupDestination(
                refillTaskId = refillTaskId,
                stopId = stopId,
                sessionTaskIds = sessionTaskIds
            )
        )
    }

    fun clearPostPickupDestination() {
        _state.value = _state.value.copy(postPickupDestination = null)
    }

    fun arriveAtLocation(distanceMiles: Double? = null) {
        val state = _state.value
        val stop = state.selectedStop ?: return
        val arrivedPhase = stop.destinationKind.arrivedPhase
        val execution = TaskExecutionPlanner.activeExecution(stop = stop).let { nextExecution ->
            val capturedDistance = maxOf(
                state.activeExecution?.distanceMiles ?: 0.0,
                distanceMiles ?: 0.0
            )
            val previousBaselines = state.activeExecution?.taskStartDistanceMilesByTaskId.orEmpty()
            val baselineTaskIds = listOfNotNull(nextExecution.currentTaskId, nextExecution.wrapperTaskId).distinct()
            val defaultBaseline = if (stop.destinationKind == WorkDestinationKind.Warehouse) 0.0 else capturedDistance
            nextExecution.copy(
                distanceMiles = capturedDistance,
                taskStartDistanceMilesByTaskId = previousBaselines.filterKeys { it in nextExecution.taskIds } +
                    baselineTaskIds
                        .filterNot { it in previousBaselines }
                        .associateWith { defaultBaseline }
            )
        }
        _state.value = state.copy(
            phase = arrivedPhase,
            localActiveExecutionSession = state.localActiveExecutionSession?.copy(
                phase = arrivedPhase,
                currentTaskId = execution.currentTaskId,
                distanceMiles = execution.distanceMiles,
                taskStartDistanceMilesByTaskId = execution.taskStartDistanceMilesByTaskId
            ),
            activeExecution = execution,
            errorMessage = null
        )
    }

    fun rehydrateActiveExecution(tasks: List<VendiTask>) {
        val state = _state.value
        val execution = state.activeExecution ?: return
        val hydratedTasksRaw = TaskExecutionResolver.hydratedTasks(
            stopTasks = execution.displayTasks,
            allTasks = tasks
        )
        val localCurrentTaskId = state.localActiveExecutionSession?.currentTaskId
        val activeCurrentTaskId = execution.currentTaskId ?: localCurrentTaskId
        val previousCurrentTask = activeCurrentTaskId?.let { taskId ->
            execution.displayTasks.firstOrNull { it.id == taskId }
        }
        val hydratedCurrentTask = activeCurrentTaskId?.let { taskId ->
            hydratedTasksRaw.firstOrNull { it.id == taskId }
        }
        val preservedWarehousePickupTask = previousCurrentTask
            ?.takeIf {
                localCurrentTaskId == activeCurrentTaskId &&
                    state.phase == WorkPhase.AtWarehouse &&
                    it.type == TaskType.MachinePickupInventory &&
                    (hydratedCurrentTask == null || TaskStateHelpers.isFinal(hydratedCurrentTask.status))
            }
            ?.copy(
                status = TaskStatus.Pending,
                startedAt = null,
                doneAt = null,
                duration = null,
                distance = null
            )
        val hydratedTasks = if (preservedWarehousePickupTask == null) {
            hydratedTasksRaw
        } else {
            val replaced = hydratedTasksRaw.map { task ->
                if (task.id == preservedWarehousePickupTask.id) preservedWarehousePickupTask else task
            }
            if (replaced.any { it.id == preservedWarehousePickupTask.id }) replaced else listOf(preservedWarehousePickupTask) + replaced
        }
        val machineGroups = TaskExecutionResolver.stableMachineGroups(
            groups = TaskExecutionResolver.orderedMachineGroups(hydratedTasks),
            previousGroups = execution.machineGroups
        )
        val orderedDisplayTasks = machineGroups.flatMap { it.tasks }
        val preferredMachineId = preferredExecutionMachineId(execution, orderedDisplayTasks)
        val currentTask = TaskExecutionResolver.currentExecutableTask(
            tasks = orderedDisplayTasks,
            preferredTaskId = activeCurrentTaskId,
            preferredMachineId = preferredMachineId
        )
        val progress = TaskExecutionResolver.progress(orderedDisplayTasks, currentTask?.id)
        val nextBaselines = execution.taskStartDistanceMilesByTaskId.filterKeys { taskId ->
            hydratedTasks.any { it.id == taskId }
        }
        _state.value = state.copy(
            activeExecution = execution.copy(
                taskIds = orderedDisplayTasks.map { it.id },
                displayTasks = orderedDisplayTasks,
                tasks = orderedDisplayTasks.map(::executionTaskItem),
                wrapperTaskId = TaskExecutionResolver.wrapperTask(currentTask, hydratedTasks)?.id
                    ?.takeIf { it != currentTask?.id },
                machineGroups = machineGroups,
                currentTaskId = currentTask?.id,
                currentTaskIndex = progress.current,
                totalTaskCount = progress.total,
                taskStartDistanceMilesByTaskId = nextBaselines
            ),
            localActiveExecutionSession = state.localActiveExecutionSession?.copy(
                currentTaskId = currentTask?.id,
                taskIds = orderedDisplayTasks.map { it.id }.toSet(),
                taskStartDistanceMilesByTaskId = nextBaselines
            )
        )
    }

    fun advanceToNextTask() {
        val state = _state.value
        val execution = state.activeExecution ?: return
        val currentTask = TaskExecutionResolver.currentExecutableTask(
            tasks = execution.displayTasks,
            preferredTaskId = null,
            preferredMachineId = execution.currentTaskId?.let { taskId ->
                execution.displayTasks.firstOrNull { it.id == taskId }?.machine
            }
        )
        val progress = TaskExecutionResolver.progress(execution.displayTasks, currentTask?.id)
        _state.value = state.copy(
            activeExecution = execution.copy(
                currentTaskId = currentTask?.id,
                currentTaskIndex = progress.current,
                totalTaskCount = progress.total,
                taskStartDistanceMilesByTaskId = currentTask?.id?.let { taskId ->
                    execution.taskStartDistanceMilesByTaskId + (taskId to execution.distanceMiles.coerceAtLeast(0.0))
                } ?: execution.taskStartDistanceMilesByTaskId
            )
        )
    }

    fun recordActiveExecutionDistanceSnapshot(distanceMiles: Double) {
        val state = _state.value
        val execution = state.activeExecution ?: return
        val capturedDistance = maxOf(execution.distanceMiles, distanceMiles.coerceAtLeast(0.0))
        if (capturedDistance == execution.distanceMiles) return
        _state.value = state.copy(
            activeExecution = execution.copy(distanceMiles = capturedDistance),
            localActiveExecutionSession = state.localActiveExecutionSession?.copy(distanceMiles = capturedDistance)
        )
    }

    fun currentMachineStartTasks(): List<VendiTask> {
        if (!_state.value.phase.isAtDestination) return emptyList()
        val execution = _state.value.activeExecution ?: return emptyList()
        val tasksById = execution.displayTasks.associateBy { it.id }
        return listOfNotNull(
            execution.wrapperTaskId?.let(tasksById::get),
            execution.currentTaskId?.let(tasksById::get)
        )
            .distinctBy { it.id }
            .filter { task ->
                task.startedAt == null &&
                    !TaskStateHelpers.isFinal(task.status) &&
                    (task.machine != null || task.type == TaskType.MachinePickupInventory)
            }
    }

    fun currentRouteStartTasks(): List<VendiTask> {
        val state = _state.value
        if (state.phase != WorkPhase.PreparingRoute && !state.phase.isNavigating) return emptyList()
        val execution = state.activeExecution ?: return emptyList()
        val tasksById = execution.displayTasks.associateBy { it.id }
        val routeOwnerTaskId = execution.wrapperTaskId ?: execution.currentTaskId
        return listOfNotNull(routeOwnerTaskId?.let(tasksById::get))
            .filter { task ->
                task.startedAt == null &&
                    !TaskStateHelpers.isFinal(task.status) &&
                    (task.machine != null || task.type == TaskType.MachinePickupInventory)
            }
    }

    fun recordTaskStartBaselines(taskIds: Collection<String>, baselineDistanceMiles: Double? = null) {
        if (taskIds.isEmpty()) return
        val state = _state.value
        val execution = state.activeExecution ?: return
        val baselineDistance = (baselineDistanceMiles ?: execution.distanceMiles).coerceAtLeast(0.0)
        val nextBaselineEntries = taskIds
            .distinct()
            .mapNotNull { taskId ->
                val existingBaseline = execution.taskStartDistanceMilesByTaskId[taskId]
                val shouldUpdate = baselineDistanceMiles != null ||
                    existingBaseline == null ||
                    (existingBaseline > 0.0 && baselineDistance > existingBaseline)
                if (shouldUpdate) taskId to baselineDistance else null
            }
        if (nextBaselineEntries.isEmpty()) return
        val nextBaselines = execution.taskStartDistanceMilesByTaskId + nextBaselineEntries
        _state.value = state.copy(
            activeExecution = execution.copy(
                taskStartDistanceMilesByTaskId = nextBaselines
            ),
            localActiveExecutionSession = state.localActiveExecutionSession?.copy(
                taskStartDistanceMilesByTaskId = nextBaselines
            )
        )
    }

    fun distanceToSendForTask(task: VendiTask, status: TaskStatus): Double? {
        if (task.machine == null && task.type != TaskType.MachinePickupInventory) return null
        val execution = _state.value.activeExecution ?: return null
        return TaskExecutionMetrics.distanceToSendForTask(task, status, execution)
    }

    fun remainingTasksForCurrentStop(): List<VendiTask> {
        val execution = _state.value.activeExecution ?: return emptyList()
        return TaskExecutionDisplay.remainingTasks(execution)
    }

    fun stopCurrentSession() {
        _state.value = WorkUiState()
    }
}

private fun preferredExecutionMachineId(execution: ActiveTaskExecution, tasks: List<VendiTask>): String? {
    val preferredTaskId = execution.currentTaskId ?: execution.wrapperTaskId
    val preferredMachineId = preferredTaskId?.let { taskId ->
        tasks.firstOrNull { it.id == taskId }?.machine
    }
    if (preferredMachineId != null && tasks.any { it.machine == preferredMachineId && !TaskStateHelpers.isFinal(it.status) }) {
        return preferredMachineId
    }
    return null
}

private fun executionTaskItem(task: VendiTask): ExecutionTaskItem {
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

private fun ActiveTaskExecution.hasStartedRouteOwnerTask(): Boolean {
    val routeOwnerTaskId = wrapperTaskId ?: currentTaskId
    return displayTasks.any { task -> task.id == routeOwnerTaskId && task.startedAt != null }
}

private fun WorkPhase?.restoreActiveRoutePhase(destinationKind: WorkDestinationKind): WorkPhase {
    return when (this) {
        WorkPhase.PreparingRoute -> WorkPhase.PreparingRoute
        destinationKind.navigatingPhase -> destinationKind.navigatingPhase
        destinationKind.arrivedPhase -> destinationKind.arrivedPhase
        WorkPhase.NavigatingToWarehouse,
        WorkPhase.NavigatingToLocation,
        WorkPhase.Summary,
        WorkPhase.Idle,
        null -> destinationKind.navigatingPhase
        WorkPhase.AtWarehouse,
        WorkPhase.AtLocation,
        WorkPhase.Completing -> destinationKind.arrivedPhase
    }
}
