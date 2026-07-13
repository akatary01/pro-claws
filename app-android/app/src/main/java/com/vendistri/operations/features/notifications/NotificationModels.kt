package com.vendistri.operations.features.notifications

import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.VendiTask
import com.vendistri.operations.features.tasks.optNullableString
import com.vendistri.operations.features.tasks.taskStatusLabel
import com.vendistri.operations.features.tasks.taskTypeLabel
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

enum class AppNotificationKind {
    TaskCreated,
    TaskAssigned,
    TaskStatusChanged,
    TaskEdited,
    TaskDeleted
}

data class AppNotificationItem(
    val id: String,
    val kind: AppNotificationKind,
    val title: String,
    val subtitle: String,
    val createdAt: Instant,
    val isRead: Boolean,
    val taskId: String?,
    val locationId: String?,
    val scheduledFor: String?,
    val taskStatus: TaskStatus?,
    val assigneeId: String?
)

data class RealtimeEventPayload(
    val type: String?,
    val id: String?,
    val taskId: String?,
    val machineId: String?,
    val locationVisitId: String?,
    val locationId: String?,
    val reason: String?
) {
    val resolvedTaskId: String?
        get() = taskId ?: id

    val isDelete: Boolean
        get() = reason == "delete" || reason == "deleted"

    companion object {
        fun fromJson(rawJson: String): RealtimeEventPayload? {
            val json = runCatching { JSONObject(rawJson) }.getOrNull() ?: return null
            return RealtimeEventPayload(
                type = json.optNullableString("type"),
                id = json.optNullableString("id"),
                taskId = json.optNullableString("taskId") ?: json.optNullableString("task_id"),
                machineId = json.optNullableString("machineId") ?: json.optNullableString("machine_id"),
                locationVisitId = json.optNullableString("locationVisitId") ?: json.optNullableString("location_visit_id"),
                locationId = json.optNullableString("locationId") ?: json.optNullableString("location_id"),
                reason = json.optNullableString("reason")
            )
        }
    }
}

object AppNotificationFactory {
    fun make(
        event: RealtimeEventPayload,
        task: VendiTask?,
        locationName: String?,
        currentUserId: String?
    ): AppNotificationItem? {
        val snapshot = NotificationTaskSnapshot(task, locationName)
        return when (event.type to event.reason) {
            "task.changed" to "create" -> item(
                kind = AppNotificationKind.TaskCreated,
                title = "${snapshot.typeTitle} created",
                event = event,
                snapshot = snapshot
            )
            "task.changed" to "assign" -> {
                val assignedToCurrentUser = snapshot.assigneeId?.trim() == currentUserId?.trim()
                item(
                    kind = AppNotificationKind.TaskAssigned,
                    title = if (assignedToCurrentUser) {
                        "${snapshot.typeTitle} assigned to you"
                    } else {
                        "${snapshot.typeTitle} assigned"
                    },
                    event = event,
                    snapshot = snapshot
                )
            }
            "task.changed" to "status" -> item(
                kind = AppNotificationKind.TaskStatusChanged,
                title = snapshot.status?.let { "${snapshot.typeTitle} marked ${taskStatusLabel(it)}" }
                    ?: "${snapshot.typeTitle} status changed",
                event = event,
                snapshot = snapshot
            )
            "task.changed" to "edit",
            "task.changed" to "prefill" -> item(
                kind = AppNotificationKind.TaskEdited,
                title = if (snapshot.status == TaskStatus.Unassigned && snapshot.assigneeId == null) {
                    "${snapshot.typeTitle} unassigned"
                } else {
                    "${snapshot.typeTitle} updated"
                },
                event = event,
                snapshot = snapshot
            )
            "task.changed" to "delete",
            "task.changed" to "deleted" -> item(
                kind = AppNotificationKind.TaskDeleted,
                title = "${snapshot.typeTitle} deleted",
                event = event,
                snapshot = snapshot
            )
            else -> null
        }
    }

    private fun item(
        kind: AppNotificationKind,
        title: String,
        event: RealtimeEventPayload,
        snapshot: NotificationTaskSnapshot
    ): AppNotificationItem {
        return AppNotificationItem(
            id = UUID.randomUUID().toString(),
            kind = kind,
            title = title,
            subtitle = snapshot.subtitle,
            createdAt = Instant.now(),
            isRead = false,
            taskId = event.resolvedTaskId,
            locationId = snapshot.locationId ?: event.locationId,
            scheduledFor = snapshot.scheduledFor,
            taskStatus = snapshot.status,
            assigneeId = snapshot.assigneeId
        )
    }
}

private data class NotificationTaskSnapshot(
    val task: VendiTask?,
    val fallbackLocationName: String?
) {
    val typeTitle: String = task?.type?.let(::taskTypeLabel) ?: "Task"
    val status: TaskStatus? = task?.status
    val assigneeId: String? = task?.assignee
    val locationId: String? = task?.location
    val scheduledFor: String? = task?.scheduledFor

    val subtitle: String = run {
        val machineName = task?.machineName?.trim().orEmpty()
        val locationName = task?.locationName?.trim().takeUnless { it.isNullOrEmpty() }
            ?: fallbackLocationName?.trim()
        when {
            machineName.isNotEmpty() && !locationName.isNullOrEmpty() -> "$machineName - $locationName"
            machineName.isNotEmpty() -> machineName
            !locationName.isNullOrEmpty() -> locationName
            else -> "Task update"
        }
    }
}
