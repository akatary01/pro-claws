package com.vendistri.operations.features.work

import com.vendistri.operations.features.location.WarehouseOption
import com.vendistri.operations.features.refill.AggregateRefillInventorySuggestion
import com.vendistri.operations.features.refill.RefillInventorySuggestionLine
import com.vendistri.operations.features.refill.RefillInventorySuggestions
import com.vendistri.operations.features.tasks.VendiTask

enum class RefillDecisionAction {
    RouteToLocation,
    RouteToWarehouse,
    UseWarehouseStock,
    UseUntrackedStock
}

data class RefillDecisionPlan(
    val task: VendiTask,
    val suggestions: RefillInventorySuggestions,
    val isRemaining: Boolean,
    val isIncluded: Boolean = isRemaining
) {
    val remainingNeed: Int
        get() = suggestions.items.sumOf { maxOf(it.suggestedRefill - (it.pickedUpQuantity ?: 0), 0) }

    val pickedUpQuantity: Int
        get() = suggestions.items.sumOf { it.pickedUpQuantity ?: 0 }

    val hasRecommendedRefill: Boolean
        get() = suggestions.items.any { it.suggestedRefill > 0 }
}

data class RefillDecisionUiState(
    val anchorTask: VendiTask? = null,
    val plans: List<RefillDecisionPlan> = emptyList(),
    val warehouses: List<WarehouseOption> = emptyList(),
    val aggregateSuggestion: AggregateRefillInventorySuggestion? = null,
    val recommendedWarehouseId: String? = null,
    val selectedWarehouseId: String? = null,
    val routePreview: RoutePreview? = null,
    val warehouseRoutePreview: RoutePreview? = null,
    val pendingStop: GoStopPlan? = null,
    val selectedTaskIds: Set<String> = emptySet(),
    val selectedAction: RefillDecisionAction = RefillDecisionAction.RouteToLocation,
    val isApplying: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val isVisible: Boolean
        get() = anchorTask != null && plans.isNotEmpty()

    val includedTasks: List<VendiTask>
        get() = plans.filter { it.isIncluded }.map { it.task }

    val includedPlans: List<RefillDecisionPlan>
        get() = plans.filter { it.isRemaining && it.isIncluded }

    val metricPlans: List<RefillDecisionPlan>
        get() = if (plans.any { it.isRemaining }) {
            plans.filter { !it.isRemaining || it.isIncluded }
        } else {
            plans
        }

    val hasRemainingPlans: Boolean
        get() = plans.any { it.isRemaining }

    val currentStopRemainingPlans: List<RefillDecisionPlan>
        get() {
            val selectedLocationId = pendingStop?.targetLocationId ?: return emptyList()
            return plans.filter { plan ->
                plan.isRemaining && plan.task.location == selectedLocationId
            }
        }

    val hasIncludedCurrentStopPlan: Boolean
        get() = currentStopRemainingPlans.any { it.isIncluded }

    val currentStopSelectionRequired: Boolean
        get() = currentStopRemainingPlans.isNotEmpty() && !hasIncludedCurrentStopPlan

    val currentStopSelectionRequiredMessage: String?
        get() {
            if (!currentStopSelectionRequired) return null
            val locationName = pendingStop?.title ?: anchorTask?.locationName ?: "this stop"
            return "Select at least one machine from $locationName to start this route."
        }

    val coveredPickupPlan: RefillDecisionPlan?
        get() {
            val selectedLocationId = pendingStop?.targetLocationId ?: return null
            return plans.firstOrNull { plan ->
                !plan.isRemaining && plan.task.location == selectedLocationId
            }
        }

    val canContinueWithoutPickup: Boolean
        get() {
            pendingStop?.targetLocationId ?: return !hasRemainingPlans
            return coveredPickupPlan != null
        }

    val isPickupAlreadyCovered: Boolean
        get() = includedPlans.isEmpty() && canContinueWithoutPickup

    val canApply: Boolean
        get() {
            if (currentStopSelectionRequired) return false
            return when (selectedAction) {
                RefillDecisionAction.RouteToLocation -> isPickupAlreadyCovered || !hasRemainingPlans || includedPlans.isNotEmpty()
                RefillDecisionAction.UseUntrackedStock -> !hasRemainingPlans || includedPlans.isNotEmpty()
                RefillDecisionAction.RouteToWarehouse,
                RefillDecisionAction.UseWarehouseStock -> !selectedWarehouseId.isNullOrBlank() && (!hasRemainingPlans || includedPlans.isNotEmpty())
            }
        }

    val remainingUncoveredUnits: Int
        get() = includedPlans.sumOf { it.remainingNeed }

    val selectedSuggestions: RefillInventorySuggestions?
        get() = includedPlans.firstOrNull()?.suggestions ?: plans.firstOrNull()?.suggestions

    val selectedWarehouse: WarehouseOption?
        get() = warehouses.firstOrNull { it.id == selectedWarehouseId }

    val warehouseAvailabilityLines: List<WarehouseAvailabilityLine>
        get() {
            val byProduct = linkedMapOf<String, WarehouseAvailabilityAccumulator>()
            includedPlans.forEach { plan ->
                plan.suggestions.items.forEach { item ->
                    val needed = maxOf(item.suggestedRefill - (item.pickedUpQuantity ?: 0), 0)
                    if (needed <= 0) return@forEach
                    val existing = byProduct[item.product.id]
                    byProduct[item.product.id] = WarehouseAvailabilityAccumulator(
                        productId = item.product.id,
                        productName = item.product.name,
                        needed = (existing?.needed ?: 0) + needed,
                        available = maxOf(existing?.available ?: 0, item.warehouseAvailableStock ?: 0)
                    )
                }
            }
            return byProduct.values
                .map { WarehouseAvailabilityLine(it.productId, it.productName, it.available, it.needed) }
                .sortedBy { it.productName.lowercase() }
        }

    val breakdownLocations: List<RefillBreakdownLocation>
        get() = plans
            .groupBy { it.task.location ?: it.task.locationName ?: "unknown" }
            .map { (locationId, locationPlans) ->
                RefillBreakdownLocation(
                    id = locationId,
                    name = locationPlans.mapNotNull { it.task.locationName }.firstOrNull() ?: "Location",
                    machines = locationPlans.map { plan ->
                        RefillBreakdownMachine(
                            id = plan.task.machine ?: plan.task.id,
                            taskId = plan.task.id,
                            name = plan.task.machineName ?: "Machine",
                            isIncluded = plan.isIncluded,
                            isRemaining = plan.isRemaining,
                            items = plan.suggestions.items.map { it.toBreakdownItem() }
                        )
                    }.sortedBy { it.name.lowercase() }
                )
            }
            .sortedBy { it.name.lowercase() }

    val breakdownMachineCount: Int
        get() = metricPlans
            .filter { it.hasRecommendedRefill }
            .map { it.task.machine ?: it.task.machineName ?: it.task.id }
            .toSet()
            .size

    val breakdownProductCount: Int
        get() = metricPlans
            .flatMap { it.suggestions.items }
            .filter { refillBreakdownQuantity(it) > 0 }
            .map { it.product.id }
            .toSet()
            .size

    val breakdownQuantity: Int
        get() = metricPlans.sumOf { plan -> plan.suggestions.items.sumOf(::refillBreakdownQuantity) }
}

data class WarehouseAvailabilityLine(
    val productId: String,
    val productName: String,
    val available: Int,
    val needed: Int
) {
    val status: WarehouseStockStatus
        get() = when {
            needed <= 0 || available >= needed -> WarehouseStockStatus.Available
            available > 0 -> WarehouseStockStatus.Partial
            else -> WarehouseStockStatus.None
        }
}

enum class WarehouseStockStatus {
    Available,
    Partial,
    None
}

data class RefillBreakdownItem(
    val id: String,
    val productId: String,
    val productName: String,
    val currentStock: Int,
    val capacity: Int,
    val needed: Int,
    val pickedUp: Int
)

data class RefillBreakdownMachine(
    val id: String,
    val taskId: String,
    val name: String,
    val isIncluded: Boolean,
    val isRemaining: Boolean,
    val items: List<RefillBreakdownItem>
) {
    val needed: Int
        get() = if (isIncluded) items.sumOf { it.needed } else 0

    val pickedUp: Int
        get() = items.sumOf { it.pickedUp }
}

data class RefillBreakdownLocation(
    val id: String,
    val name: String,
    val machines: List<RefillBreakdownMachine>
) {
    val machineCount: Int
        get() = machines.count { it.isIncluded || !it.isRemaining }

    val needed: Int
        get() = machines.sumOf { it.needed }

    val pickedUp: Int
        get() = machines.sumOf { it.pickedUp }
}

private data class WarehouseAvailabilityAccumulator(
    val productId: String,
    val productName: String,
    val available: Int,
    val needed: Int
)

private fun RefillInventorySuggestionLine.toBreakdownItem(): RefillBreakdownItem {
    val pickedUp = pickedUpQuantity ?: 0
    return RefillBreakdownItem(
        id = itemId,
        productId = product.id,
        productName = product.name,
        currentStock = currentStock,
        capacity = capacity,
        needed = maxOf(suggestedRefill - pickedUp, 0),
        pickedUp = pickedUp
    )
}

private fun refillBreakdownQuantity(item: RefillInventorySuggestionLine): Int {
    val pickedUp = item.pickedUpQuantity ?: 0
    return pickedUp + maxOf(item.suggestedRefill - pickedUp, 0)
}
