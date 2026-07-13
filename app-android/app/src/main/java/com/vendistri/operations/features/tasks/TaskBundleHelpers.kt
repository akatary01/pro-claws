package com.vendistri.operations.features.tasks

object TaskBundleHelpers {
    fun bundledTasksForService(tasks: List<VendiTask>, serviceTask: VendiTask): List<VendiTask> {
        if (serviceTask.type != TaskType.MachineService) return emptyList()
        return tasks
            .filter { it.id != serviceTask.id && it.serviceTaskId == serviceTask.id }
            .sortedWith(serviceBundleComparator)
    }

    fun unifiedServiceBundleTasks(tasks: List<VendiTask>, task: VendiTask): List<VendiTask> {
        if (task.type == TaskType.MachineService) {
            return listOf(task) + bundledTasksForService(tasks, task)
        }
        val serviceTaskId = task.serviceTaskId ?: return listOf(task)
        val serviceTask = tasks.firstOrNull {
            it.id == serviceTaskId && it.type == TaskType.MachineService
        } ?: return listOf(task)
        return listOf(serviceTask) + bundledTasksForService(tasks, serviceTask)
    }

    fun expandedRefreshTaskIds(tasks: List<VendiTask>, taskIds: Set<String>): Set<String> {
        val byId = tasks.associateBy { it.id }
        val expanded = linkedSetOf<String>()
        taskIds.forEach { taskId ->
            expanded.add(taskId)
            byId[taskId]?.let { addConnectedRefreshTaskIds(tasks = tasks, task = it, expanded = expanded) }
        }
        return expanded
    }

    private fun addConnectedRefreshTaskIds(tasks: List<VendiTask>, task: VendiTask, expanded: MutableSet<String>) {
        expanded.add(task.id)
        when (task.type) {
            TaskType.MachineService -> {
                bundledTasksForService(tasks, task).forEach { expanded.add(it.id) }
            }
            TaskType.MachineRefill,
            TaskType.MachineClean,
            TaskType.MachineCollection -> {
                task.serviceTaskId
                    ?.let { serviceTaskId -> tasks.firstOrNull { it.id == serviceTaskId && it.type == TaskType.MachineService } }
                    ?.let { serviceTask ->
                        expanded.add(serviceTask.id)
                        bundledTasksForService(tasks, serviceTask).forEach { expanded.add(it.id) }
                    }
            }
            TaskType.MachinePickupInventory -> {
                linkedRefillTasks(tasks, task).forEach { refillTask ->
                    if (expanded.add(refillTask.id)) {
                        addConnectedRefreshTaskIds(tasks = tasks, task = refillTask, expanded = expanded)
                    }
                }
            }
            else -> Unit
        }
    }

    private fun linkedRefillTasks(tasks: List<VendiTask>, pickupTask: VendiTask): List<VendiTask> {
        val refillIds = (
            pickupTask.refillTaskIds +
                listOfNotNull(pickupTask.refillTaskId) +
                pickupTask.pickupLines.mapNotNull { it.refillTaskId }
            ).toSet()
        if (refillIds.isEmpty()) return emptyList()
        return tasks.filter { it.type == TaskType.MachineRefill && it.id in refillIds }
    }

    private val serviceBundleComparator = compareBy<VendiTask>(
        { taskTypeSortRank(it.type) },
        { it.createdAt.orEmpty() },
        { it.id }
    )
}

fun taskTypeSortRank(type: TaskType): Int {
    return when (type) {
        TaskType.MachineService -> 0
        TaskType.MachineCollection -> 1
        TaskType.MachineRefill -> 2
        TaskType.MachineClean -> 3
        TaskType.MachineRepair -> 4
        TaskType.MachineRefund -> 5
        TaskType.MachineInstall -> 6
        TaskType.MachineRemove -> 7
        TaskType.MachinePickupInventory -> 8
        TaskType.Default -> 9
        TaskType.Other -> 10
    }
}
