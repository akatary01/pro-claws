package com.vendistri.operations.features.work

import com.vendistri.operations.features.location.WarehouseOption
import com.vendistri.operations.features.refill.RefillInventoryContext
import com.vendistri.operations.features.refill.RefillInventorySuggestions
import com.vendistri.operations.features.tasks.VendiTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class RefillDecisionStore {
    private val _state = MutableStateFlow(RefillDecisionUiState())
    val state: StateFlow<RefillDecisionUiState> = _state.asStateFlow()

    fun reset() {
        _state.value = RefillDecisionUiState()
    }

    fun prepare(
        stop: GoStopPlan?,
        plan: GoPlan?,
        allTasks: List<VendiTask>,
        currentUserId: String?,
        warehouses: List<WarehouseOption>,
        selectedTaskIds: Set<String>,
        context: RefillInventoryContext,
        pendingStop: GoStopPlan?,
        routePreview: RoutePreview?,
        warehouseRoutePreview: RoutePreview?,
        bypassedTaskIds: Set<String> = emptySet()
    ) {
        val anchorTask = RouteRefillInventoryDecision.anchorTask(
            stop = stop,
            allTasks = allTasks,
            currentUserId = currentUserId,
            bypassedTaskIds = bypassedTaskIds,
            selectedTaskIds = selectedTaskIds
        )
        if (anchorTask == null) {
            reset()
            return
        }
        val tasks = RouteRefillInventoryDecision.eligibleTasks(
            plan = plan,
            anchorTask = anchorTask,
            allTasks = allTasks,
            currentUserId = currentUserId,
            bypassedTaskIds = bypassedTaskIds,
            selectedTaskIds = selectedTaskIds
        )
        val contextTasksById = context.tasks.associateBy { it.id }
        val suggestionsByTaskId = context.suggestions.associateBy { it.taskId }
        val remainingTaskIds = context.coverage.remainingTaskIds.toSet()
        val selectedLocationId = stop?.targetLocationId
        val plans = tasks.mapNotNull { task ->
            val liveTask = contextTasksById[task.id] ?: allTasks.firstOrNull { it.id == task.id } ?: task
            val suggestions = suggestionsByTaskId[liveTask.id] ?: emptySuggestions(liveTask)
            val hasRecommendedRefill = suggestions.items.any { it.suggestedRefill > 0 }
            val isCurrentLocationTask = selectedLocationId != null && liveTask.location == selectedLocationId
            if (!hasRecommendedRefill && !isCurrentLocationTask) return@mapNotNull null
            RefillDecisionPlan(
                task = liveTask,
                suggestions = suggestions,
                // Coverage can conservatively report the refill task as remaining even
                // when the machine has no configured refill items. Keep the current
                // location in the breakdown, but never create pickup work for zero need.
                isRemaining = liveTask.id in remainingTaskIds && hasRecommendedRefill
            )
        }.sortedWith(compareBy({ it.task.locationName ?: it.task.machineName ?: it.task.id }, { it.task.id }))
        if (plans.isEmpty()) {
            reset()
            return
        }
        val recommendedWarehouseId = context.aggregateSuggestion.warehouseId ?: plans.firstOrNull()?.suggestions?.warehouseId
        val existingWarehouseId = existingWarehouseId(plans.map { it.task })
        val selectedWarehouseId = recommendedWarehouseId ?: existingWarehouseId ?: warehouses.firstOrNull()?.id
        val existingSourceMode = plans.mapNotNull { it.task.inventorySourceMode }.distinct().singleOrNull()
        _state.value = RefillDecisionUiState(
            anchorTask = anchorTask,
            plans = plans.map { planItem ->
                planItem.copy(isIncluded = planItem.isRemaining)
            },
            warehouses = warehouses,
            aggregateSuggestion = context.aggregateSuggestion,
            recommendedWarehouseId = recommendedWarehouseId,
            selectedWarehouseId = selectedWarehouseId,
            routePreview = routePreview,
            warehouseRoutePreview = warehouseRoutePreview,
            pendingStop = pendingStop,
            selectedTaskIds = selectedTaskIds,
            selectedAction = initialAction(
                plans = plans,
                selectedWarehouseId = selectedWarehouseId,
                existingSourceMode = existingSourceMode
            )
        )
    }

    fun prepareFallback(
        anchorTask: VendiTask?,
        tasks: List<VendiTask>,
        warehouses: List<WarehouseOption>,
        pendingStop: GoStopPlan?,
        routePreview: RoutePreview?,
        warehouseRoutePreview: RoutePreview?,
        errorMessage: String
    ) {
        val anchor = anchorTask ?: tasks.firstOrNull()
        if (anchor == null || tasks.isEmpty()) {
            reset()
            return
        }
        val plans = tasks
            .distinctBy { it.id }
            .map { task ->
                RefillDecisionPlan(
                    task = task,
                    suggestions = emptySuggestions(task),
                    isRemaining = true,
                    isIncluded = true
                )
            }
            .sortedWith(compareBy({ it.task.locationName ?: it.task.machineName ?: it.task.id }, { it.task.id }))
        _state.value = RefillDecisionUiState(
            anchorTask = anchor,
            plans = plans,
            warehouses = warehouses,
            selectedWarehouseId = warehouses.firstOrNull()?.id,
            routePreview = routePreview,
            warehouseRoutePreview = warehouseRoutePreview,
            pendingStop = pendingStop,
            selectedTaskIds = tasks.map { it.id }.toSet(),
            selectedAction = RefillDecisionAction.UseUntrackedStock,
            errorMessage = errorMessage
        )
    }

    fun selectAction(action: RefillDecisionAction) {
        _state.update { state ->
            if (state.isPickupAlreadyCovered) {
                state.copy(selectedAction = RefillDecisionAction.RouteToLocation, errorMessage = null)
            } else {
                state.copy(selectedAction = action, errorMessage = null).withValidSelectedAction()
            }
        }
    }

    fun selectWarehouse(warehouseId: String) {
        _state.update { it.copy(selectedWarehouseId = warehouseId, errorMessage = null) }
    }

    fun replaceContext(context: RefillInventoryContext) {
        val current = _state.value
        val currentPlansById = current.plans.associateBy { it.task.id }
        val contextTasksById = context.tasks.associateBy { it.id }
        val suggestionsByTaskId = context.suggestions.associateBy { it.taskId }
        val remainingTaskIds = context.coverage.remainingTaskIds.toSet()
        val nextPlans = current.plans.mapNotNull { plan ->
            val liveTask = contextTasksById[plan.task.id] ?: plan.task
            val suggestions = suggestionsByTaskId[liveTask.id] ?: plan.suggestions
            val hasRecommendedRefill = suggestions.items.any { it.suggestedRefill > 0 }
            val previous = currentPlansById[liveTask.id]
            RefillDecisionPlan(
                task = liveTask,
                suggestions = suggestions,
                isRemaining = liveTask.id in remainingTaskIds && hasRecommendedRefill,
                isIncluded = previous?.isIncluded ?: (liveTask.id in remainingTaskIds)
            )
        }
        _state.value = current.copy(
            plans = nextPlans,
            aggregateSuggestion = context.aggregateSuggestion,
            recommendedWarehouseId = context.aggregateSuggestion.warehouseId ?: current.recommendedWarehouseId
        ).withValidSelectedAction()
    }

    fun setRoutePreviews(routePreview: RoutePreview?, warehouseRoutePreview: RoutePreview?) {
        _state.update { it.copy(routePreview = routePreview, warehouseRoutePreview = warehouseRoutePreview) }
    }

    fun toggleTaskInclusion(taskId: String) {
        _state.update { state ->
            state.copy(
                plans = state.plans.map { plan ->
                    if (plan.task.id == taskId && plan.isRemaining) {
                        plan.copy(isIncluded = !plan.isIncluded)
                    } else {
                        plan
                    }
                },
                errorMessage = null
            ).withValidSelectedAction()
        }
    }

    fun setLoading(isLoading: Boolean) {
        _state.update { it.copy(isLoading = isLoading) }
    }

    fun setWarehouses(warehouses: List<WarehouseOption>) {
        _state.update { state ->
            val selectedWarehouseId = state.selectedWarehouseId
                ?.takeIf { selectedId -> warehouses.any { it.id == selectedId } }
                ?: state.recommendedWarehouseId
                    ?.takeIf { recommendedId -> warehouses.any { it.id == recommendedId } }
                ?: warehouses.firstOrNull()?.id
            state.copy(warehouses = warehouses, selectedWarehouseId = selectedWarehouseId)
        }
    }

    fun setApplying(isApplying: Boolean) {
        _state.update { it.copy(isApplying = isApplying) }
    }

    fun setError(message: String?) {
        _state.update { it.copy(errorMessage = message) }
    }

    private fun existingWarehouseId(tasks: List<VendiTask>): String? {
        val warehouseIds = tasks.mapNotNull { it.inventorySourceWarehouseId?.takeIf(String::isNotBlank) }.distinct()
        return warehouseIds.singleOrNull()
    }

    private fun emptySuggestions(task: VendiTask): RefillInventorySuggestions {
        return RefillInventorySuggestions(
            taskId = task.id,
            machineId = task.machine,
            warehouseId = null,
            warehouseName = null,
            warehouseAddress = null,
            items = emptyList()
        )
    }

    private fun initialAction(
        plans: List<RefillDecisionPlan>,
        selectedWarehouseId: String?,
        existingSourceMode: com.vendistri.operations.features.tasks.RefillInventorySourceMode?
    ): RefillDecisionAction {
        if (existingSourceMode == com.vendistri.operations.features.tasks.RefillInventorySourceMode.Untracked) {
            return RefillDecisionAction.UseUntrackedStock
        }
        if (plans.any { it.isRemaining }) {
            return if (selectedWarehouseId == null) RefillDecisionAction.UseUntrackedStock else RefillDecisionAction.RouteToWarehouse
        }
        return RefillDecisionAction.RouteToLocation
    }

    private fun RefillDecisionUiState.withValidSelectedAction(): RefillDecisionUiState {
        if (isPickupAlreadyCovered) {
            return copy(selectedAction = RefillDecisionAction.RouteToLocation, errorMessage = null)
        }
        if (selectedAction != RefillDecisionAction.RouteToLocation || includedPlans.isEmpty()) {
            return this
        }
        return copy(
            selectedAction = if (selectedWarehouseId == null) {
                RefillDecisionAction.UseUntrackedStock
            } else {
                RefillDecisionAction.RouteToWarehouse
            },
            errorMessage = null
        )
    }
}
