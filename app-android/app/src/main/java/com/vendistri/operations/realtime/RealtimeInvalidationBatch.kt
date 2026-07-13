package com.vendistri.operations.realtime

import com.vendistri.operations.features.notifications.RealtimeEventPayload

data class RealtimeInvalidationBatch(
    val shouldReloadAuth: Boolean = false,
    val shouldReloadTasks: Boolean = false,
    val shouldReloadLocations: Boolean = false,
    val shouldRefreshInventoryEditors: Boolean = false,
    val requiresFullTaskReload: Boolean = false,
    val changedTaskIds: Set<String> = emptySet(),
    val deletedTaskIds: Set<String> = emptySet(),
    val changedMachineIds: Set<String> = emptySet(),
    val changedLocationIds: Set<String> = emptySet()
) {
    companion object {
        fun from(events: List<RealtimeEventPayload>): RealtimeInvalidationBatch {
            var reloadAuth = false
            var reloadTasks = false
            var reloadLocations = false
            var fullTaskReload = false
            var refreshInventoryEditors = false
            val changedTaskIds = linkedSetOf<String>()
            val deletedTaskIds = linkedSetOf<String>()
            val changedMachineIds = linkedSetOf<String>()
            val changedLocationIds = linkedSetOf<String>()

            events.forEach { event ->
                event.machineId?.takeIf(String::isNotBlank)?.let(changedMachineIds::add)
                event.locationId?.takeIf(String::isNotBlank)?.let(changedLocationIds::add)

                when (event.type) {
                    "task.changed" -> {
                        reloadTasks = true
                        val taskId = event.resolvedTaskId
                        if (taskId.isNullOrBlank()) {
                            if (event.machineId.isNullOrBlank() && event.locationId.isNullOrBlank()) {
                                fullTaskReload = true
                            }
                        } else if (event.isDelete) {
                            deletedTaskIds.add(taskId)
                            changedTaskIds.remove(taskId)
                        } else {
                            changedTaskIds.add(taskId)
                        }
                    }
                    "inventory.changed" -> {
                        reloadTasks = true
                        refreshInventoryEditors = true
                    }
                    "machine.changed",
                    "location.changed" -> {
                        reloadTasks = true
                        reloadLocations = true
                    }
                    "invite.changed",
                    "user.auth_changed",
                    "organization.members_changed",
                    "organization.changed" -> {
                        reloadAuth = true
                        reloadTasks = true
                        reloadLocations = true
                    }
                    else -> {
                        reloadTasks = true
                        fullTaskReload = true
                    }
                }
            }

            return RealtimeInvalidationBatch(
                shouldReloadAuth = reloadAuth,
                shouldReloadTasks = reloadTasks,
                shouldReloadLocations = reloadLocations,
                shouldRefreshInventoryEditors = refreshInventoryEditors,
                requiresFullTaskReload = fullTaskReload,
                changedTaskIds = changedTaskIds,
                deletedTaskIds = deletedTaskIds,
                changedMachineIds = changedMachineIds,
                changedLocationIds = changedLocationIds
            )
        }
    }
}
