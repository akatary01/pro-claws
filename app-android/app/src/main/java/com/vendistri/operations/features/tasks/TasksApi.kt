package com.vendistri.operations.features.tasks

import com.vendistri.operations.network.ApiTransport
import com.vendistri.operations.network.HttpMethod
import com.vendistri.operations.network.MultipartFile
import com.vendistri.operations.features.map.LocationCoordinate
import com.vendistri.operations.features.pickup.PickupInventoryCompletionLine
import com.vendistri.operations.features.refill.AggregateRefillInventorySuggestion
import com.vendistri.operations.features.refill.RefillInventoryCompletionLine
import com.vendistri.operations.features.refill.RefillInventoryContext
import com.vendistri.operations.features.refill.RefillInventorySuggestions
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate

class TasksApi(
    private val apiClient: ApiTransport
) {
    suspend fun fetchTasks(filters: TaskFetchFilters = TaskFetchFilters()): List<VendiTask> {
        val endpoint = "/task/machine/all${filters.queryString()}"
        return VendiTask.listFromJson(apiClient.request(HttpMethod.Get, endpoint).body)
    }

    suspend fun fetchContactTasks(filters: TaskFetchFilters = TaskFetchFilters()): List<VendiTask> {
        val endpoint = "/task/machine/contact/list${filters.queryString()}"
        return VendiTask.listFromJson(apiClient.request(HttpMethod.Get, endpoint).body)
    }

    suspend fun fetchTask(taskId: String): VendiTask {
        val encodedId = taskId.urlEncoded()
        return VendiTask.fromJson(
            org.json.JSONObject(apiClient.request(HttpMethod.Get, "/task?id=$encodedId").body)
        )
    }

    suspend fun updateTaskStatus(taskId: String, status: TaskStatus) {
        val encodedId = taskId.urlEncoded()
        apiClient.request(HttpMethod.Put, "/task/status/update?id=$encodedId&status=${status.rawValue}")
    }

    suspend fun startMachineTask(task: VendiTask, coordinate: LocationCoordinate) {
        val segment = taskTypeSegment(task.type) ?: return
        apiClient.request(
            method = HttpMethod.Put,
            path = "/task/machine/$segment/start?id=${task.id.urlEncoded()}&lat=${coordinate.latitude}&lng=${coordinate.longitude}"
        )
    }

    suspend fun updateMachineTaskStatus(
        task: VendiTask,
        status: TaskStatus,
        distanceMiles: Double? = null
    ) {
        val segment = taskTypeSegment(task.type) ?: return
        val distanceQuery = distanceMiles?.let { "&distance=$it" }.orEmpty()
        apiClient.request(
            method = HttpMethod.Put,
            path = "/task/machine/$segment/status/update?id=${task.id.urlEncoded()}&status=${status.rawValue}$distanceQuery"
        )
    }

    suspend fun fetchRefillInventorySuggestions(taskId: String, warehouseId: String? = null): RefillInventorySuggestions {
        val encodedId = taskId.urlEncoded()
        val warehouseQuery = warehouseId?.takeIf { it.isNotBlank() }?.let { "&warehouseId=${it.urlEncoded()}" }.orEmpty()
        return RefillInventorySuggestions.fromJson(
            apiClient.request(
                HttpMethod.Get,
                "/task/machine/machine_refill/inventory/suggestions?id=$encodedId$warehouseQuery"
            ).body
        )
    }

    suspend fun fetchAggregateRefillInventorySuggestion(taskIds: List<String>): AggregateRefillInventorySuggestion {
        return AggregateRefillInventorySuggestion.fromJson(
            apiClient.request(
                method = HttpMethod.Post,
                path = "/task/machine/machine_refill/inventory/suggestions/aggregate",
                body = JSONObject()
                    .put("taskIds", taskIds.toJsonArray())
                    .toString()
            ).body
        )
    }

    suspend fun fetchRefillInventoryContext(taskIds: List<String>, warehouseId: String? = null): RefillInventoryContext {
        val body = JSONObject().put("taskIds", taskIds.toJsonArray())
        warehouseId?.takeIf { it.isNotBlank() }?.let { body.put("warehouseId", it) }
        return RefillInventoryContext.fromJson(
            apiClient.request(
                method = HttpMethod.Post,
                path = "/task/machine/machine_refill/inventory/context",
                body = body.toString()
            ).body
        )
    }

    suspend fun completeRefillTaskWithInventory(
        taskId: String,
        lines: List<RefillInventoryCompletionLine>,
        distanceMiles: Double? = null
    ): VendiTask {
        val encodedId = taskId.urlEncoded()
        return VendiTask.fromJson(
            org.json.JSONObject(
                apiClient.request(
                    method = HttpMethod.Post,
                    path = "/task/machine/machine_refill/complete?id=$encodedId",
                    body = JSONObject()
                        .put("items", JSONArray(lines.map(RefillInventoryCompletionLine::toJson)))
                        .putNullable("distance", distanceMiles)
                        .toString()
                ).body
            )
        )
    }

    suspend fun completePickupInventoryTask(
        taskId: String,
        lines: List<PickupInventoryCompletionLine>,
        distanceMiles: Double? = null
    ): VendiTask {
        val encodedId = taskId.urlEncoded()
        return VendiTask.fromJson(
            org.json.JSONObject(
                apiClient.request(
                    method = HttpMethod.Post,
                    path = "/task/machine/machine_pickup_inventory/complete?id=$encodedId",
                    body = JSONObject()
                        .put("items", JSONArray(lines.map(PickupInventoryCompletionLine::toJson)))
                        .putNullable("distance", distanceMiles)
                        .toString()
                ).body
            )
        )
    }

    suspend fun uploadTaskPhotoConfirmation(
        taskId: String,
        fileName: String,
        mimeType: String,
        fileData: ByteArray
    ) {
        apiClient.requestMultipart(
            method = HttpMethod.Post,
            path = "/task/asset/confirmation_photo/add?task_id=${taskId.urlEncoded()}",
            file = MultipartFile(
                fieldName = "file",
                fileName = fileName,
                mimeType = mimeType,
                data = fileData
            )
        )
    }

    suspend fun removeTaskPhotoConfirmation(assetId: String) {
        apiClient.request(
            method = HttpMethod.Delete,
            path = "/task/asset/delete?id=${assetId.urlEncoded()}"
        )
    }

    suspend fun setRefillInventorySource(
        taskId: String,
        warehouseId: String?,
        sourceMode: RefillInventorySourceMode
    ): VendiTask {
        val encodedId = taskId.urlEncoded()
        return VendiTask.fromJson(
            org.json.JSONObject(
                apiClient.request(
                    method = HttpMethod.Put,
                    path = "/task/machine/machine_refill/inventory/source?id=$encodedId",
                    body = JSONObject()
                        .put("sourceMode", sourceMode.rawValue)
                        .putNullable("warehouseId", warehouseId)
                        .toString()
                ).body
            )
        )
    }

    suspend fun createPickupInventoryTaskForRefill(taskId: String, warehouseId: String): VendiTask {
        val encodedId = taskId.urlEncoded()
        return VendiTask.fromJson(
            org.json.JSONObject(
                apiClient.request(
                    method = HttpMethod.Post,
                    path = "/task/machine/machine_refill/inventory/pickup?id=$encodedId",
                    body = JSONObject()
                        .put("warehouseId", warehouseId)
                        .toString()
                ).body
            )
        )
    }

    suspend fun createPickupInventoryTaskForRefills(taskIds: List<String>, warehouseId: String): VendiTask {
        return VendiTask.fromJson(
            org.json.JSONObject(
                apiClient.request(
                    method = HttpMethod.Post,
                    path = "/task/machine/machine_refill/inventory/pickup/aggregate",
                    body = JSONObject()
                        .put("warehouseId", warehouseId)
                        .put("taskIds", taskIds.toJsonArray())
                        .toString()
                ).body
            )
        )
    }

    suspend fun claimTask(taskId: String) {
        val encodedId = taskId.urlEncoded()
        apiClient.request(HttpMethod.Put, "/task/claim?id=$encodedId")
    }

    suspend fun bulkUpdateStatus(taskIds: List<String>, status: TaskStatus, cascadeBundle: Boolean = false): List<VendiTask> {
        val ids = taskIds.distinct().filter { it.isNotBlank() }
        if (ids.isEmpty()) return emptyList()
        return VendiTask.listFromJson(
            apiClient.request(
                method = HttpMethod.Put,
                path = "/task/bulk/status",
                body = JSONObject()
                    .put("ids", ids.toJsonArray())
                    .put("status", status.rawValue)
                    .put("cascadeBundle", cascadeBundle)
                    .toString()
            ).body
        )
    }

    suspend fun bulkCancel(taskIds: List<String>): List<VendiTask> {
        val ids = taskIds.distinct().filter { it.isNotBlank() }
        if (ids.isEmpty()) return emptyList()
        return VendiTask.listFromJson(
            apiClient.request(
                method = HttpMethod.Post,
                path = "/task/bulk/cancel",
                body = idsBody(ids).toString()
            ).body
        )
    }

    suspend fun bulkDelete(taskIds: List<String>) {
        val ids = taskIds.distinct().filter { it.isNotBlank() }
        if (ids.isEmpty()) return
        apiClient.request(
            method = HttpMethod.Delete,
            path = "/task/bulk/delete",
            body = idsBody(ids).toString()
        )
    }

    suspend fun bulkAssign(taskIds: List<String>, assigneeId: String?): List<VendiTask> {
        val ids = taskIds.distinct().filter { it.isNotBlank() }
        if (ids.isEmpty()) return emptyList()
        return VendiTask.listFromJson(
            apiClient.request(
                method = HttpMethod.Put,
                path = "/task/bulk/edit",
                body = idsBody(ids)
                    .putNullable("assignee", assigneeId)
                    .toString()
            ).body
        )
    }

    suspend fun bulkUpdateTaskNotes(taskIds: List<String>, notes: String?): List<VendiTask> {
        val ids = taskIds.distinct().filter { it.isNotBlank() }
        if (ids.isEmpty()) return emptyList()
        return VendiTask.listFromJson(
            apiClient.request(
                method = HttpMethod.Put,
                path = "/task/bulk/edit",
                body = idsBody(ids)
                    .put("notes", notes.orEmpty())
                    .toString()
            ).body
        )
    }

    suspend fun updateCollectionFinancials(
        taskId: String,
        gross: Double?,
        grossCash: Double?,
        grossCard: Double?,
        refunds: Double?,
        commission: Double?,
        commissionPaymentType: CommissionPaymentType?,
        net: Double?,
        includeRefundsInCommission: Boolean?
    ) {
        editMachineTask(
            taskId = taskId,
            type = TaskType.MachineCollection,
            body = JSONObject()
                .putNullable("gross", gross)
                .putNullable("grossCash", grossCash)
                .putNullable("grossCard", grossCard)
                .putNullable("refunds", refunds)
                .putNullable("commission", commission)
                .putNullable("commissionPaymentType", commissionPaymentType?.rawValue)
                .putNullable("net", net)
                .putNullable("includeRefundsInCommission", includeRefundsInCommission)
        )
    }

    suspend fun updateRefundFinancials(taskId: String, refunds: Double?) {
        editMachineTask(
            taskId = taskId,
            type = TaskType.MachineRefund,
            body = JSONObject().putNullable("refunds", refunds)
        )
    }

    suspend fun bulkReschedule(taskIds: List<String>, scheduledFor: String, assigneeId: String? = null): List<VendiTask> {
        val ids = taskIds.distinct().filter { it.isNotBlank() }
        if (ids.isEmpty()) return emptyList()
        val body = idsBody(ids)
            .put("scheduledFor", scheduledFor)
        if (!assigneeId.isNullOrBlank()) {
            body.put("assignee", assigneeId)
        }
        return VendiTask.listFromJson(
            apiClient.request(
                method = HttpMethod.Put,
                path = "/task/bulk/edit",
                body = body.toString()
            ).body
        )
    }

    suspend fun bulkCreate(items: List<TaskCreateRequest>): List<VendiTask> {
        if (items.isEmpty()) return emptyList()
        val createdAt = Instant.now().toString()
        val body = JSONObject()
            .put("items", JSONArray(items.map { it.toJson(createdAt) }))
        return VendiTask.listFromJson(
            apiClient.request(
                method = HttpMethod.Post,
                path = "/task/bulk/create",
                body = body.toString()
            ).body
        )
    }

    suspend fun bulkPrecheck(items: List<TaskBulkPrecheckItem>): List<TaskBulkPrecheckResult> {
        if (items.isEmpty()) return emptyList()
        val body = JSONObject()
            .put("items", JSONArray(items.map(TaskBulkPrecheckItem::toJson)))
        return JSONArray(
            apiClient.request(
                method = HttpMethod.Post,
                path = "/task/bulk/precheck",
                body = body.toString()
            ).body
        ).toJsonObjects().map(TaskBulkPrecheckResult::fromJson)
    }

    suspend fun fetchNextServiceCadenceDate(locationId: String, after: LocalDate): LocalDate? {
        if (locationId.isBlank()) return null
        val raw = JSONObject(
            apiClient.request(
                method = HttpMethod.Get,
                path = "/location/service/cadence/next?id=${locationId.urlEncoded()}&after=$after"
            ).body
        ).optNullableString("nextScheduledFor") ?: return null
        return TaskScheduleDate.parse(raw)
    }

    private suspend fun editMachineTask(taskId: String, type: TaskType, body: JSONObject) {
        val segment = taskTypeSegment(type) ?: return
        apiClient.request(
            method = HttpMethod.Put,
            path = "/task/machine/$segment/edit?id=${taskId.urlEncoded()}",
            body = body.toString()
        )
    }
}

private fun taskTypeSegment(type: TaskType): String? {
    return when (type) {
        TaskType.MachineService -> "machine_service"
        TaskType.MachineCollection -> "machine_collection"
        TaskType.MachineRefill -> "machine_refill"
        TaskType.MachineRefund -> "machine_refund"
        TaskType.MachineClean -> "machine_clean"
        TaskType.MachineRepair -> "machine_repair"
        TaskType.MachineInstall -> "machine_install"
        TaskType.MachineRemove -> "machine_remove"
        TaskType.MachinePickupInventory -> "machine_pickup_inventory"
        TaskType.Default, TaskType.Other -> null
    }
}

data class TaskCreateRequest(
    val type: TaskType,
    val machineId: String,
    val scheduledFor: LocalDate,
    val assigneeId: String?,
    val notes: String?,
    val childTaskTypes: List<TaskType>?
)

private fun TaskCreateRequest.toJson(createdAt: String): JSONObject {
    val payload = JSONObject()
        .put("type", type.rawValue)
        .put("status", if (assigneeId == null) TaskStatus.Unassigned.rawValue else TaskStatus.Pending.rawValue)
        .put("machine", machineId)
        .put("notes", notes ?: JSONObject.NULL)
        .put("createdAt", createdAt)
        .put("scheduledFor", scheduledFor.toString())
        .put("isPublic", false)

    if (!assigneeId.isNullOrBlank()) {
        payload.put("assignee", assigneeId)
    }
    if (type == TaskType.MachineCollection) {
        payload
            .put("gross", 0.0)
            .put("grossCash", 0.0)
            .put("grossCard", 0.0)
            .put("refunds", 0.0)
            .put("includeRefundsInCommission", JSONObject.NULL)
    }
    if (type == TaskType.MachineRefund) {
        payload.put("refunds", 0.0)
    }
    if (type == TaskType.MachineService && childTaskTypes != null) {
        payload.put("childTaskTypes", JSONArray(childTaskTypes.map { it.rawValue }))
    }
    return payload
}

private fun TaskBulkPrecheckItem.toJson(): JSONObject {
    return JSONObject()
        .put("type", type.rawValue)
        .put("machineId", machineId)
        .put("scheduledFor", scheduledFor.toString())
        .also { payload -> taskId?.let { payload.put("taskId", it) } }
}

data class TaskFetchFilters(
    val machineId: String? = null,
    val locationId: String? = null,
    val scheduledFrom: String? = null,
    val scheduledTo: String? = null,
    val statuses: List<TaskStatus> = emptyList(),
    val taskIds: List<String> = emptyList(),
    val refillTaskIds: List<String> = emptyList()
) {
    val isScoped: Boolean
        get() = machineId != null ||
            locationId != null ||
            scheduledFrom != null ||
            scheduledTo != null ||
            statuses.isNotEmpty() ||
            taskIds.isNotEmpty() ||
            refillTaskIds.isNotEmpty()

    fun queryString(): String {
        val items = buildList {
            machineId?.let { add("machine_id" to it) }
            locationId?.let { add("location_id" to it) }
            scheduledFrom?.let { add("scheduled_from" to it) }
            scheduledTo?.let { add("scheduled_to" to it) }
            if (statuses.isNotEmpty()) add("status" to statuses.joinToString(",") { it.rawValue })
            if (taskIds.isNotEmpty()) add("task_ids" to taskIds.joinToString(","))
            if (refillTaskIds.isNotEmpty()) add("refill_task_ids" to refillTaskIds.joinToString(","))
        }
        if (items.isEmpty()) return ""
        return items.joinToString(prefix = "?", separator = "&") { (key, value) ->
            "${key.urlEncoded()}=${value.urlEncoded()}"
        }
    }
}

private fun String.urlEncoded(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

private fun idsBody(ids: List<String>): JSONObject {
    return JSONObject().put("ids", ids.toJsonArray())
}

private fun List<String>.toJsonArray(): JSONArray {
    return JSONArray(this)
}

private fun RefillInventoryCompletionLine.toJson(): JSONObject {
    return JSONObject()
        .put("itemId", itemId)
        .put("refilledQuantity", refilledQuantity)
        .put("finalStock", finalStock)
}

private fun PickupInventoryCompletionLine.toJson(): JSONObject {
    return JSONObject()
        .put("lineId", lineId)
        .put("pickedUpQuantity", pickedUpQuantity)
}

private fun JSONObject.putNullable(name: String, value: Any?): JSONObject {
    return put(name, value ?: JSONObject.NULL)
}
