package com.vendistri.operations.features.tasks

import com.vendistri.operations.features.location.Address
import org.json.JSONArray
import org.json.JSONObject

enum class TaskStatus(val rawValue: String) {
    Done("done"),
    Pending("pending"),
    Unassigned("unassigned"),
    Error("error"),
    Cancelled("cancelled");

    companion object {
        fun from(rawValue: String?): TaskStatus {
            return entries.firstOrNull { it.rawValue == rawValue } ?: Pending
        }
    }
}

enum class TaskType(val rawValue: String) {
    Default("default"),
    MachineRemove("machine_remove"),
    MachineInstall("machine_install"),
    MachineClean("machine_clean"),
    MachineRepair("machine_repair"),
    MachineRefill("machine_refill"),
    MachineRefund("machine_refund"),
    MachineCollection("machine_collection"),
    MachineService("machine_service"),
    MachinePickupInventory("machine_pickup_inventory"),
    Other("other");

    companion object {
        fun from(rawValue: String?): TaskType {
            return entries.firstOrNull { it.rawValue == rawValue } ?: Other
        }
    }
}

enum class CollectionInputMode(val rawValue: String) {
    Dollars("dollars"),
    Credits("credits");

    companion object {
        fun from(rawValue: String?): CollectionInputMode? {
            return entries.firstOrNull { it.rawValue == rawValue }
        }
    }
}

enum class CommissionPaymentType(val rawValue: String, val label: String) {
    Cash("cash", "Cash"),
    Check("check", "Check"),
    DirectDeposit("direct_deposit", "Direct Deposit");

    companion object {
        fun from(rawValue: String?): CommissionPaymentType? {
            return when (rawValue) {
                "ach" -> DirectDeposit
                else -> entries.firstOrNull { it.rawValue == rawValue }
            }
        }
    }
}

enum class RefillInventorySourceMode(val rawValue: String) {
    Warehouse("warehouse"),
    Untracked("untracked");

    companion object {
        fun from(rawValue: String?): RefillInventorySourceMode? {
            return entries.firstOrNull { it.rawValue == rawValue }
        }
    }
}

enum class TaskAssetType {
    PhotoConfirmation;

    companion object {
        fun from(rawValue: String?): TaskAssetType? {
            return when (rawValue?.lowercase()) {
                "confirmation_photo", "photo_confirmation" -> PhotoConfirmation
                else -> null
            }
        }
    }
}

data class TaskAsset(
    val id: String,
    val taskId: String?,
    val type: TaskAssetType,
    val url: String?,
    val createdAt: String?,
    val uploadedBy: String?
) {
    companion object {
        fun fromJson(json: JSONObject): TaskAsset? {
            val type = TaskAssetType.from(json.optNullableString("type")) ?: return null
            return TaskAsset(
                id = json.getString("id"),
                taskId = json.optNullableString("task_id") ?: json.optNullableString("taskId"),
                type = type,
                url = json.optNullableString("url"),
                createdAt = json.optNullableString("created_at") ?: json.optNullableString("createdAt"),
                uploadedBy = json.optNullableString("uploaded_by") ?: json.optNullableString("uploadedBy")
            )
        }
    }
}

data class TaskInventoryProduct(
    val id: String,
    val name: String,
    val brand: String?,
    val code: String?,
    val size: String?
) {
    companion object {
        fun fromJson(json: JSONObject): TaskInventoryProduct {
            return TaskInventoryProduct(
                id = json.getString("id"),
                name = json.optString("name"),
                brand = json.optNullableString("brand"),
                code = json.optNullableString("code"),
                size = json.optNullableString("size")
            )
        }
    }
}

data class TaskPickupLine(
    val id: String,
    val refillTaskId: String?,
    val machineName: String?,
    val product: TaskInventoryProduct,
    val currentStock: Int?,
    val capacity: Int?,
    val suggestedQuantity: Int,
    val warehouseAvailableStock: Int?,
    val pickedUpQuantity: Int?
) {
    companion object {
        fun fromJson(json: JSONObject): TaskPickupLine {
            return TaskPickupLine(
                id = json.getString("id"),
                refillTaskId = json.optNullableString("refill_task_id") ?: json.optNullableString("refillTaskId"),
                machineName = json.optNullableString("machine_name") ?: json.optNullableString("machineName"),
                product = TaskInventoryProduct.fromJson(json.getJSONObject("product")),
                currentStock = json.optNullableInt("current_stock") ?: json.optNullableInt("currentStock"),
                capacity = json.optNullableInt("capacity"),
                suggestedQuantity = json.optInt("suggested_quantity", json.optInt("suggestedQuantity", 0)),
                warehouseAvailableStock = json.optNullableInt("warehouse_available_stock")
                    ?: json.optNullableInt("warehouseAvailableStock"),
                pickedUpQuantity = json.optNullableInt("picked_up_quantity") ?: json.optNullableInt("pickedUpQuantity")
            )
        }
    }
}

data class TaskInventoryCompletionLine(
    val itemId: String,
    val product: TaskInventoryProduct,
    val stockBefore: Int,
    val refilledQuantity: Int,
    val finalStock: Int,
    val movementId: String?
) {
    companion object {
        fun fromJson(json: JSONObject): TaskInventoryCompletionLine {
            return TaskInventoryCompletionLine(
                itemId = json.optNullableString("itemId")
                    ?: json.optNullableString("item_id")
                    ?: json.getString("id"),
                product = TaskInventoryProduct.fromJson(json.getJSONObject("product")),
                stockBefore = json.optNullableInt("stockBefore")
                    ?: json.optNullableInt("stock_before")
                    ?: 0,
                refilledQuantity = json.optNullableInt("refilledQuantity")
                    ?: json.optNullableInt("refilled_quantity")
                    ?: 0,
                finalStock = json.optNullableInt("finalStock")
                    ?: json.optNullableInt("final_stock")
                    ?: 0,
                movementId = json.optNullableString("movementId")
                    ?: json.optNullableString("movement_id")
            )
        }
    }
}

data class TaskInventoryCompletion(
    val items: List<TaskInventoryCompletionLine>
) {
    companion object {
        fun fromJson(json: JSONObject?): TaskInventoryCompletion? {
            if (json == null) return null
            return TaskInventoryCompletion(
                items = json.optJSONArray("items")?.toJsonObjects()?.map(TaskInventoryCompletionLine::fromJson).orEmpty()
            )
        }
    }
}

data class VendiTask(
    val id: String,
    val type: TaskType,
    val status: TaskStatus,
    val isPublic: Boolean,
    val assignee: String?,
    val assigneeName: String?,
    val assigneeEmail: String?,
    val machine: String?,
    val machineName: String?,
    val collectionInputMode: CollectionInputMode?,
    val creditsPerDollar: Double?,
    val location: String?,
    val locationName: String?,
    val locationAddress: Address?,
    val scheduledFor: String,
    val createdAt: String?,
    val startedAt: String?,
    val doneAt: String?,
    val lastVisitAt: String? = null,
    val daysSinceLastVisit: Int? = null,
    val isLive: Boolean?,
    val duration: Double?,
    val notes: String?,
    val distance: Double?,
    val gross: Double?,
    val grossCash: Double?,
    val grossCard: Double?,
    val refunds: Double?,
    val commission: Double?,
    val commissionPaymentType: CommissionPaymentType? = null,
    val net: Double?,
    val includeRefundsInCommission: Boolean? = null,
    val serviceTaskId: String? = null,
    val refillTaskId: String?,
    val refillTaskIds: List<String>,
    val pickupLines: List<TaskPickupLine>,
    val inventoryCompletion: TaskInventoryCompletion? = null,
    val inventorySourceMode: RefillInventorySourceMode? = null,
    val inventorySourceWarehouseId: String? = null,
    val inventorySourceWarehouseName: String? = null,
    val warehouseId: String? = null,
    val warehouseName: String? = null,
    val warehouseAddress: Address? = null,
    val assets: List<TaskAsset> = emptyList()
) {
    val photoConfirmationAsset: TaskAsset?
        get() = assets.firstOrNull { it.type == TaskAssetType.PhotoConfirmation }

    val displayTitle: String
        get() = taskTypeLabel(type)

    val displayLocation: String
        get() = locationName ?: "No location"

    val displayMachine: String
        get() = machineName ?: "No machine"

    fun countsPickupInventoryStock(displayStatus: TaskStatus? = null): Boolean {
        return type == TaskType.MachinePickupInventory && (displayStatus ?: status) == TaskStatus.Done
    }

    fun isLinkedToRefillTask(refillTaskId: String): Boolean {
        return refillTaskId in refillTaskIds ||
            this.refillTaskId == refillTaskId ||
            pickupLines.any { it.refillTaskId == refillTaskId }
    }

    fun effectivePickedUpQuantity(line: TaskPickupLine, displayStatus: TaskStatus? = null): Int {
        if (!countsPickupInventoryStock(displayStatus)) return 0
        return maxOf(line.pickedUpQuantity ?: 0, 0)
    }

    fun effectivePickedUpQuantityForRefill(refillTaskId: String, displayStatus: TaskStatus? = null): Int {
        if (!countsPickupInventoryStock(displayStatus)) return 0
        return pickupLines
            .filter { it.refillTaskId == refillTaskId }
            .sumOf { effectivePickedUpQuantity(it, displayStatus) }
    }

    companion object {
        fun fromJson(json: JSONObject): VendiTask {
            val pickupLines = json.optJSONArray("pickup_lines")
                ?: json.optJSONArray("pickupLines")
            val refillTaskIds = json.optJSONArray("refill_task_ids")
                ?: json.optJSONArray("refillTaskIds")
            val assets = json.optJSONArray("assets")

            return VendiTask(
                id = json.getString("id"),
                type = TaskType.from(json.optString("type")),
                status = TaskStatus.from(json.optString("status")),
                isPublic = json.optBoolean("is_public", json.optBoolean("isPublic")),
                assignee = json.optNullableString("assignee"),
                assigneeName = json.optNullableString("assignee_name") ?: json.optNullableString("assigneeName"),
                assigneeEmail = json.optNullableString("assignee_email") ?: json.optNullableString("assigneeEmail"),
                machine = json.optNullableString("machine"),
                machineName = json.optNullableString("machine_name") ?: json.optNullableString("machineName"),
                collectionInputMode = CollectionInputMode.from(
                    json.optNullableString("collection_input_mode") ?: json.optNullableString("collectionInputMode")
                ),
                creditsPerDollar = json.optNullableDouble("credits_per_dollar") ?: json.optNullableDouble("creditsPerDollar"),
                location = json.optNullableString("location"),
                locationName = json.optNullableString("location_name") ?: json.optNullableString("locationName"),
                locationAddress = Address.fromJson(
                    json.optJSONObject("location_address") ?: json.optJSONObject("locationAddress")
                ),
                scheduledFor = json.optString("scheduled_for", json.optString("scheduledFor")),
                createdAt = json.optNullableString("created_at") ?: json.optNullableString("createdAt"),
                startedAt = json.optNullableString("started_at") ?: json.optNullableString("startedAt"),
                doneAt = json.optNullableString("done_at") ?: json.optNullableString("doneAt"),
                lastVisitAt = json.optNullableString("last_visit_at") ?: json.optNullableString("lastVisitAt"),
                daysSinceLastVisit = json.optNullableInt("days_since_last_visit")
                    ?: json.optNullableInt("daysSinceLastVisit"),
                isLive = json.optNullableBoolean("is_live") ?: json.optNullableBoolean("isLive"),
                duration = json.optNullableDouble("duration"),
                notes = json.optNullableString("notes"),
                distance = json.optNullableDouble("distance"),
                gross = json.optNullableDouble("gross"),
                grossCash = json.optNullableDouble("gross_cash") ?: json.optNullableDouble("grossCash"),
                grossCard = json.optNullableDouble("gross_card") ?: json.optNullableDouble("grossCard"),
                refunds = json.optNullableDouble("refunds"),
                commission = json.optNullableDouble("commission"),
                commissionPaymentType = CommissionPaymentType.from(
                    json.optNullableString("commission_payment_type") ?: json.optNullableString("commissionPaymentType")
                ),
                net = json.optNullableDouble("net"),
                includeRefundsInCommission = json.optNullableBoolean("include_refunds_in_commission")
                    ?: json.optNullableBoolean("includeRefundsInCommission"),
                serviceTaskId = json.optNullableString("service_task_id") ?: json.optNullableString("serviceTaskId"),
                refillTaskId = json.optNullableString("refill_task_id") ?: json.optNullableString("refillTaskId"),
                refillTaskIds = refillTaskIds?.toStringList().orEmpty(),
                pickupLines = pickupLines?.toJsonObjects()?.map(TaskPickupLine::fromJson).orEmpty(),
                inventoryCompletion = TaskInventoryCompletion.fromJson(
                    json.optJSONObject("inventoryCompletion") ?: json.optJSONObject("inventory_completion")
                ),
                inventorySourceMode = RefillInventorySourceMode.from(
                    json.optNullableString("inventorySourceMode") ?: json.optNullableString("inventory_source_mode")
                ),
                inventorySourceWarehouseId = json.optNullableString("inventorySourceWarehouseId")
                    ?: json.optNullableString("inventory_source_warehouse_id"),
                inventorySourceWarehouseName = json.optNullableString("inventorySourceWarehouseName")
                    ?: json.optNullableString("inventory_source_warehouse_name"),
                warehouseId = json.optNullableString("warehouseId") ?: json.optNullableString("warehouse_id"),
                warehouseName = json.optNullableString("warehouseName") ?: json.optNullableString("warehouse_name"),
                warehouseAddress = Address.fromJson(
                    json.optJSONObject("warehouseAddress") ?: json.optJSONObject("warehouse_address")
                ),
                assets = assets?.toJsonObjects()?.mapNotNull(TaskAsset::fromJson).orEmpty()
            )
        }

        fun listFromJson(rawJson: String): List<VendiTask> {
            return JSONArray(rawJson).toJsonObjects().map(::fromJson)
        }
    }
}

data class TaskSummary(
    val open: Int = 0,
    val unassigned: Int = 0,
    val inProgress: Int = 0,
    val completed: Int = 0,
    val cancelled: Int = 0
) {
    companion object {
        fun fromTasks(tasks: List<VendiTask>): TaskSummary {
            return TaskSummary(
                open = tasks.count { it.status == TaskStatus.Pending },
                unassigned = tasks.count { it.status == TaskStatus.Unassigned },
                inProgress = tasks.count { it.isLive == true },
                completed = tasks.count { it.status == TaskStatus.Done },
                cancelled = tasks.count { it.status == TaskStatus.Cancelled || it.status == TaskStatus.Error }
            )
        }
    }
}

fun taskTypeLabel(type: TaskType): String {
    return when (type) {
        TaskType.Default -> "Task"
        TaskType.MachineRemove -> "Remove"
        TaskType.MachineInstall -> "Install"
        TaskType.MachineClean -> "Clean"
        TaskType.MachineRepair -> "Repair"
        TaskType.MachineRefill -> "Refill"
        TaskType.MachineRefund -> "Refund"
        TaskType.MachineCollection -> "Collection"
        TaskType.MachineService -> "Service"
        TaskType.MachinePickupInventory -> "Pickup Inventory"
        TaskType.Other -> "Task"
    }
}

fun taskStatusLabel(status: TaskStatus): String {
    return TaskStatusPresentation.label(status)
}

internal fun JSONObject.optNullableString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return optString(name).ifBlank { null }
}

internal fun JSONObject.optNullableInt(name: String): Int? {
    if (!has(name) || isNull(name)) return null
    return optInt(name)
}

internal fun JSONObject.optNullableDouble(name: String): Double? {
    if (!has(name) || isNull(name)) return null
    return optDouble(name)
}

internal fun JSONObject.optNullableBoolean(name: String): Boolean? {
    if (!has(name) || isNull(name)) return null
    return optBoolean(name)
}

internal fun JSONArray.toJsonObjects(): List<JSONObject> {
    return List(length()) { index -> getJSONObject(index) }
}

internal fun JSONArray.toStringList(): List<String> {
    return List(length()) { index -> getString(index) }
}
