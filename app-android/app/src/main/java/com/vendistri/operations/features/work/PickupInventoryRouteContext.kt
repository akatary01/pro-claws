package com.vendistri.operations.features.work

import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.TaskStateHelpers
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.VendiTask

data class PickupInventoryPostPickupRoute(
    val stopId: String,
    val taskIds: Set<String>
)

data class PickupInventoryDestinationLocationContext(
    val locationId: String?,
    val title: String,
    val address: com.vendistri.operations.features.location.Address?,
    val tasks: List<VendiTask>
)

object PickupInventoryRouteContext {
    fun linkedRefillTaskIds(pickupTasks: List<VendiTask>, allTasks: List<VendiTask>): List<String> {
        val explicitIds = pickupTasks
            .filter { it.type == TaskType.MachinePickupInventory }
            .flatMap { task ->
                task.refillTaskIds + listOfNotNull(task.refillTaskId) + task.pickupLines.mapNotNull { it.refillTaskId }
            }
            .distinct()
        if (explicitIds.isNotEmpty()) return explicitIds

        val pickupTask = pickupTasks.firstOrNull { it.type == TaskType.MachinePickupInventory } ?: return emptyList()
        return allTasks
            .filter { task ->
                task.type == TaskType.MachineRefill &&
                    task.location == pickupTask.location &&
                    task.scheduledFor == pickupTask.scheduledFor &&
                    task.inventorySourceWarehouseId == pickupTask.warehouseId &&
                    (pickupTask.machine == null || task.machine == pickupTask.machine)
            }
            .map { it.id }
            .distinct()
    }

    fun destinationTask(pickupTask: VendiTask, allTasks: List<VendiTask>, preferredRefillTaskId: String?): VendiTask? {
        val linkedIds = linkedRefillTaskIds(listOf(pickupTask), allTasks)
        val preferredIds = listOfNotNull(preferredRefillTaskId) + linkedIds
        return preferredIds.firstNotNullOfOrNull { taskId -> allTasks.firstOrNull { it.id == taskId } }
    }

    fun destinationLocationContext(
        pickupTasks: List<VendiTask>,
        allTasks: List<VendiTask>,
        savedStopId: String?,
        preferredRefillTaskId: String?,
        savedSessionTaskIds: Set<String> = emptySet(),
        fallbackTitle: String? = null
    ): PickupInventoryDestinationLocationContext? {
        val primaryPickupTask = pickupTasks.firstOrNull { it.type == TaskType.MachinePickupInventory } ?: return null
        val savedSessionTasks = savedSessionTaskIds
            .takeIf { it.isNotEmpty() }
            ?.let { taskIds -> allTasks.filter { it.id in taskIds } }
            .orEmpty()
        val linkedRefillTaskIds = linkedRefillTaskIds(pickupTasks, allTasks)
        val destinationTask = preferredRefillTaskId
            ?.let { taskId -> allTasks.firstOrNull { it.id == taskId } }
            ?: savedSessionTasks.firstOrNull { it.type != TaskType.MachinePickupInventory }
            ?: linkedRefillTaskIds.firstNotNullOfOrNull { taskId -> allTasks.firstOrNull { it.id == taskId } }
            ?: destinationTask(
                pickupTask = primaryPickupTask,
                allTasks = allTasks,
                preferredRefillTaskId = preferredRefillTaskId
            )
        val destinationLocationId = savedStopId
            ?: destinationTask?.location
            ?: primaryPickupTask.location
        val destinationTasks = if (savedSessionTaskIds.isNotEmpty()) {
            savedSessionTasks
                .filter { it.type != TaskType.MachinePickupInventory }
                .ifEmpty { linkedRefillTaskIds.mapNotNull { taskId -> allTasks.firstOrNull { it.id == taskId } } }
                .filter { task ->
                    task.type != TaskType.MachinePickupInventory &&
                        (destinationLocationId == null || task.location == destinationLocationId)
                }
        } else {
            allTasks.filter { task ->
                task.location == destinationLocationId &&
                    task.type != TaskType.MachinePickupInventory &&
                    task.status != TaskStatus.Cancelled &&
                    task.status != TaskStatus.Error
            }
        }
        val uniqueTasks = com.vendistri.operations.features.tasks.TaskGroupingHelpers.uniqueTasksById(destinationTasks)
        val titleTask = uniqueTasks.firstOrNull() ?: destinationTask ?: primaryPickupTask
        val title = titleTask.locationName
            ?: fallbackTitle
            ?: primaryPickupTask.locationName
            ?: "Location"
        return PickupInventoryDestinationLocationContext(
            locationId = destinationLocationId,
            title = title,
            address = titleTask.locationAddress ?: primaryPickupTask.locationAddress,
            tasks = uniqueTasks
        )
    }

    fun postPickupRoute(
        pickupTasks: List<VendiTask>,
        allTasks: List<VendiTask>,
        preferredRefillTaskId: String?,
        savedStopId: String?,
        savedSessionTaskIds: Set<String> = emptySet()
    ): PickupInventoryPostPickupRoute? {
        val primaryPickupTask = pickupTasks.firstOrNull { it.type == TaskType.MachinePickupInventory } ?: return null
        val linkedRefillTaskIds = linkedRefillTaskIds(pickupTasks, allTasks)
        val activeLinkedRefillTasks = linkedRefillTaskIds
            .mapNotNull { taskId -> allTasks.firstOrNull { it.id == taskId } }
            .filter { task -> task.type == TaskType.MachineRefill && !TaskStateHelpers.isFinal(task.status) }
        val preferredId = preferredRefillTaskId
            ?.takeIf { candidateId -> activeLinkedRefillTasks.any { it.id == candidateId } }
        val destinationTask = destinationTask(
            pickupTask = primaryPickupTask,
            allTasks = allTasks,
            preferredRefillTaskId = preferredId
        )
            ?.takeIf { !TaskStateHelpers.isFinal(it.status) }
            ?: activeLinkedRefillTasks.firstOrNull()
        val stopId = savedStopId
            ?: destinationTask?.location
            ?: activeLinkedRefillTasks.firstOrNull()?.location
            ?: return null
        val expandedLinkedTaskIds = RouteStartScopeResolver.expandedTaskIds(
            tasks = activeLinkedRefillTasks.ifEmpty { listOfNotNull(destinationTask) },
            allTasks = allTasks
        )
        val taskIds = savedSessionTaskIds
            .takeIf { it.isNotEmpty() }
            ?.let { it + expandedLinkedTaskIds }
            ?: expandedLinkedTaskIds.takeIf { it.isNotEmpty() }
            ?: activeLinkedRefillTasks.map { it.id }.toSet()
        return PickupInventoryPostPickupRoute(
            stopId = stopId,
            taskIds = taskIds
        )
    }
}
