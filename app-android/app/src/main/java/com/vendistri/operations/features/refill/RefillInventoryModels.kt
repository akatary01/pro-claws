package com.vendistri.operations.features.refill

import com.vendistri.operations.features.location.Address
import com.vendistri.operations.features.tasks.RefillInventorySourceMode
import com.vendistri.operations.features.tasks.TaskInventoryProduct
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask
import org.json.JSONArray
import org.json.JSONObject

data class RefillInventoryLine(
    val itemId: String,
    val product: TaskInventoryProduct,
    val currentStock: Int,
    val capacity: Int,
    val suggestedRefill: Int,
    val pickedUpQuantity: Int?,
    val warehouseAvailableStock: Int?,
    val refilledText: String,
    val finalStockText: String
)

data class RefillInventoryUiState(
    val taskId: String? = null,
    val lines: List<RefillInventoryLine> = emptyList(),
    val sourceMode: RefillInventorySourceMode = RefillInventorySourceMode.Warehouse,
    val selectedWarehouseId: String? = null,
    val selectedWarehouseName: String? = null,
    val pickupSourceSummary: String? = null,
    val isLoading: Boolean = false,
    val isSavingSource: Boolean = false,
    val isCompleting: Boolean = false,
    val errorMessage: String? = null,
    val invalidRefilledItemIds: Set<String> = emptySet(),
    val invalidFinalStockItemIds: Set<String> = emptySet()
) {
    val hasCompletedPickupCoverage: Boolean
        get() = pickupSourceSummary != null
}

data class RefillInventoryCompletionLine(
    val itemId: String,
    val refilledQuantity: Int,
    val finalStock: Int
)

data class RefillInventorySuggestionLine(
    val itemId: String,
    val product: TaskInventoryProduct,
    val currentStock: Int,
    val capacity: Int,
    val suggestedRefill: Int,
    val warehouseAvailableStock: Int?,
    val pickedUpQuantity: Int?
) {
    companion object {
        fun fromJson(json: JSONObject): RefillInventorySuggestionLine {
            return RefillInventorySuggestionLine(
                itemId = json.optNullableString("itemId")
                    ?: json.optNullableString("item_id")
                    ?: json.getString("id"),
                product = TaskInventoryProduct.fromJson(json.getJSONObject("product")),
                currentStock = json.optInt("currentStock", json.optInt("current_stock")),
                capacity = json.optInt("capacity"),
                suggestedRefill = json.optInt("suggestedRefill", json.optInt("suggested_refill")),
                warehouseAvailableStock = if (json.has("warehouseAvailableStock") && !json.isNull("warehouseAvailableStock")) {
                    json.optInt("warehouseAvailableStock")
                } else if (json.has("warehouse_available_stock") && !json.isNull("warehouse_available_stock")) {
                    json.optInt("warehouse_available_stock")
                } else {
                    null
                },
                pickedUpQuantity = if (json.has("pickedUpQuantity") && !json.isNull("pickedUpQuantity")) {
                    json.optInt("pickedUpQuantity")
                } else if (json.has("picked_up_quantity") && !json.isNull("picked_up_quantity")) {
                    json.optInt("picked_up_quantity")
                } else {
                    null
                }
            )
        }
    }
}

data class RefillInventorySuggestions(
    val taskId: String,
    val machineId: String?,
    val warehouseId: String?,
    val warehouseName: String?,
    val warehouseAddress: Address?,
    val items: List<RefillInventorySuggestionLine>
) {
    companion object {
        fun fromJson(json: JSONObject): RefillInventorySuggestions {
            val items = json.optJSONArray("items") ?: JSONArray()
            return RefillInventorySuggestions(
                taskId = json.optNullableString("taskId")
                    ?: json.optNullableString("task_id")
                    ?: json.optNullableString("id")
                    ?: "",
                machineId = json.optNullableString("machineId") ?: json.optNullableString("machine_id"),
                warehouseId = json.optNullableString("warehouseId") ?: json.optNullableString("warehouse_id"),
                warehouseName = json.optNullableString("warehouseName") ?: json.optNullableString("warehouse_name"),
                warehouseAddress = Address.fromJson(
                    json.optJSONObject("warehouseAddress") ?: json.optJSONObject("warehouse_address")
                ),
                items = List(items.length()) { index ->
                    RefillInventorySuggestionLine.fromJson(items.getJSONObject(index))
                }
            )
        }

        fun fromJson(rawJson: String): RefillInventorySuggestions {
            return fromJson(JSONObject(rawJson))
        }
    }
}

data class AggregateRefillInventorySuggestion(
    val warehouseId: String?,
    val warehouseName: String?,
    val warehouseAddress: Address?
) {
    companion object {
        fun fromJson(rawJson: String): AggregateRefillInventorySuggestion {
            return fromJson(JSONObject(rawJson))
        }

        fun fromJson(json: JSONObject): AggregateRefillInventorySuggestion {
            return AggregateRefillInventorySuggestion(
                warehouseId = json.optNullableString("warehouseId") ?: json.optNullableString("warehouse_id"),
                warehouseName = json.optNullableString("warehouseName") ?: json.optNullableString("warehouse_name"),
                warehouseAddress = Address.fromJson(
                    json.optJSONObject("warehouseAddress") ?: json.optJSONObject("warehouse_address")
                )
            )
        }
    }
}

data class RefillInventoryCoverage(
    val coveredTaskIds: List<String>,
    val remainingTaskIds: List<String>
) {
    companion object {
        fun fromJson(json: JSONObject?): RefillInventoryCoverage {
            if (json == null) return RefillInventoryCoverage(emptyList(), emptyList())
            return RefillInventoryCoverage(
                coveredTaskIds = (
                    json.optJSONArray("coveredTaskIds") ?: json.optJSONArray("covered_task_ids")
                    )?.toStringList().orEmpty(),
                remainingTaskIds = (
                    json.optJSONArray("remainingTaskIds") ?: json.optJSONArray("remaining_task_ids")
                    )?.toStringList().orEmpty()
            )
        }
    }
}

data class RefillInventoryContext(
    val tasks: List<VendiTask>,
    val suggestions: List<RefillInventorySuggestions>,
    val aggregateSuggestion: AggregateRefillInventorySuggestion,
    val coverage: RefillInventoryCoverage
) {
    companion object {
        fun fromJson(rawJson: String): RefillInventoryContext {
            val json = JSONObject(rawJson)
            val taskItems = json.optJSONArray("tasks") ?: JSONArray()
            val suggestionItems = json.optJSONArray("suggestions") ?: JSONArray()
            return RefillInventoryContext(
                tasks = List(taskItems.length()) { index ->
                    VendiTask.fromJson(taskItems.getJSONObject(index))
                },
                suggestions = List(suggestionItems.length()) { index ->
                    RefillInventorySuggestions.fromJson(suggestionItems.getJSONObject(index))
                },
                aggregateSuggestion = AggregateRefillInventorySuggestion.fromJson(
                    json.optJSONObject("aggregateSuggestion") ?: json.optJSONObject("aggregate_suggestion") ?: JSONObject()
                ),
                coverage = RefillInventoryCoverage.fromJson(json.optJSONObject("coverage"))
            )
        }
    }
}

enum class PickupWarehouseStockStatus {
    Available,
    Partial,
    None
}

data class PickupInventoryStockSummary(
    val available: Int?,
    val needed: Int,
    val pickedUp: Int
) {
    val status: PickupWarehouseStockStatus?
        get() = when {
            available == null -> null
            needed <= 0 || available >= needed -> PickupWarehouseStockStatus.Available
            available > 0 -> PickupWarehouseStockStatus.Partial
            else -> PickupWarehouseStockStatus.None
        }

    val overageMessage: String?
        get() = available?.takeIf { pickedUp > it }?.let { availableStock ->
            WarehouseStockValidation.overageMessage(quantityLabel = "Picked up", available = availableStock)
        }

    val stockLineText: String?
        get() = available?.let { "Warehouse stock: $it available - $needed needed" }
}

object WarehouseStockValidation {
    fun overageMessage(quantityLabel: String, available: Int): String {
        return "$quantityLabel quantity exceeds available warehouse stock of $available."
    }
}

object RefillInventorySourceSummaryResolver {
    fun pickupSourceSummary(
        refillTaskId: String,
        refillLines: List<RefillInventoryLine>,
        allTasks: List<VendiTask>,
        fallbackWarehouseName: String?
    ): String? {
        if (refillLines.none { maxOf(it.suggestedRefill, 0) > 0 }) return null
        val coverageByProductId = completedPickupCoverageByProductId(refillTaskId, allTasks)
        val isComplete = refillLines.all { line ->
            val needed = maxOf(line.suggestedRefill, 0)
            needed == 0 || (coverageByProductId[line.product.id] ?: 0) >= needed
        }
        if (!isComplete) return null
        val warehouseName = allTasks.firstOrNull { task ->
            task.countsPickupInventoryStock() &&
                task.isLinkedToRefillTask(refillTaskId) &&
                task.effectivePickedUpQuantityForRefill(refillTaskId) > 0 &&
                !task.warehouseName.isNullOrBlank()
        }?.warehouseName ?: fallbackWarehouseName ?: "Warehouse"
        return "Pickup: $warehouseName"
    }

    private fun completedPickupCoverageByProductId(
        refillTaskId: String,
        allTasks: List<VendiTask>
    ): Map<String, Int> {
        val quantities = mutableMapOf<String, Int>()
        allTasks
            .filter { it.type == TaskType.MachinePickupInventory }
            .filter { it.countsPickupInventoryStock() && it.isLinkedToRefillTask(refillTaskId) }
            .forEach { pickupTask ->
                pickupTask.pickupLines
                    .filter { it.refillTaskId == refillTaskId }
                    .forEach { line ->
                        quantities[line.product.id] = (quantities[line.product.id] ?: 0) +
                            pickupTask.effectivePickedUpQuantity(line)
                    }
            }
        return quantities
    }
}

private fun JSONObject.optNullableString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return optString(name).ifBlank { null }
}

private fun JSONArray.toStringList(): List<String> {
    return List(length()) { index -> optString(index) }.filter { it.isNotBlank() }
}
