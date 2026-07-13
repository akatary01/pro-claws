package com.vendistri.operations.features.tasks

import org.json.JSONObject
import java.time.LocalDate

data class TaskBulkPrecheckItem(
    val type: TaskType,
    val machineId: String,
    val scheduledFor: LocalDate,
    val taskId: String? = null
)

data class TaskBulkPrecheckExistingTask(
    val id: String,
    val type: TaskType,
    val scheduledFor: String,
    val machineId: String,
    val locationId: String?
) {
    companion object {
        fun fromJson(json: JSONObject): TaskBulkPrecheckExistingTask {
            return TaskBulkPrecheckExistingTask(
                id = json.getString("id"),
                type = TaskType.from(json.optString("type")),
                scheduledFor = json.optString("scheduledFor", json.optString("scheduled_for")),
                machineId = json.optString("machineId", json.optString("machine_id")),
                locationId = json.optNullableString("locationId") ?: json.optNullableString("location_id")
            )
        }
    }
}

data class TaskBulkPrecheckResult(
    val ok: Boolean,
    val reason: String?,
    val existingTask: TaskBulkPrecheckExistingTask?,
    val type: TaskType,
    val machineId: String,
    val scheduledFor: String
) {
    companion object {
        fun fromJson(json: JSONObject): TaskBulkPrecheckResult {
            return TaskBulkPrecheckResult(
                ok = json.optBoolean("ok"),
                reason = json.optNullableString("reason"),
                existingTask = json.optJSONObject("existingTask")?.let(TaskBulkPrecheckExistingTask::fromJson)
                    ?: json.optJSONObject("existing_task")?.let(TaskBulkPrecheckExistingTask::fromJson),
                type = TaskType.from(json.optString("type")),
                machineId = json.optString("machineId", json.optString("machine_id")),
                scheduledFor = json.optString("scheduledFor", json.optString("scheduled_for"))
            )
        }
    }
}
