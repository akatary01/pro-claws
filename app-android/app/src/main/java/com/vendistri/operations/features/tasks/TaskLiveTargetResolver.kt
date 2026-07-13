package com.vendistri.operations.features.tasks

data class LiveTaskTarget(
    val activeTask: VendiTask,
    val navigateTask: VendiTask,
    val timerStartedAt: String?,
    val activeTaskCount: Int
)

object TaskLiveTargetResolver {
    fun isDirectlyLive(task: VendiTask, displayStatus: TaskStatus = task.status): Boolean {
        if (task.isLive == true) return true
        return task.startedAt != null && !TaskStateHelpers.isFinal(displayStatus)
    }

    fun effectiveLiveTaskIds(
        scopedTasks: List<VendiTask>,
        allTasks: List<VendiTask> = scopedTasks,
        displayStatus: (VendiTask) -> TaskStatus = { it.status }
    ): Set<String> {
        val scopedTaskIds = scopedTasks.map { it.id }.toSet()
        if (scopedTaskIds.isEmpty()) return emptySet()
        return allTasks
            .filter { isDirectlyLive(it, displayStatus(it)) }
            .flatMap { activeTask ->
                activeWorkScope(allTasks, activeTask)
                    .filter { it.id in scopedTaskIds }
                    .map { it.id }
            }
            .toSet()
    }

    fun target(
        scopedTasks: List<VendiTask>,
        allTasks: List<VendiTask> = scopedTasks,
        displayStatus: (VendiTask) -> TaskStatus = { it.status }
    ): LiveTaskTarget? {
        val scopedTaskIds = scopedTasks.map { it.id }.toSet()
        if (scopedTaskIds.isEmpty()) return null
        val seenKeys = mutableSetOf<String>()
        return allTasks
            .sortedByDescending { it.startedAt.orEmpty() }
            .firstNotNullOfOrNull { task ->
                val key = serviceBundleGroupKey(task) ?: "task:${task.id}"
                if (!seenKeys.add(key) || !isDirectlyLive(task, displayStatus(task))) {
                    return@firstNotNullOfOrNull null
                }
                val scope = activeWorkScope(allTasks, task)
                if (scope.none { it.id in scopedTaskIds }) return@firstNotNullOfOrNull null
                val serviceTask = if (task.type == TaskType.MachinePickupInventory) {
                    null
                } else {
                    scope.firstOrNull { it.type == TaskType.MachineService }
                }
                LiveTaskTarget(
                    activeTask = task,
                    navigateTask = serviceTask ?: task,
                    timerStartedAt = scope
                        .filter { isDirectlyLive(it, displayStatus(it)) }
                        .mapNotNull { it.startedAt }
                        .minOrNull(),
                    activeTaskCount = scope.count { isDirectlyLive(it, displayStatus(it)) }
                )
            }
    }

    private fun serviceBundleGroupKey(task: VendiTask): String? {
        if (task.type == TaskType.MachineService) return "service:${task.id}"
        if (task.type in serviceBundleChildTypes) {
            return task.serviceTaskId?.let { "service:$it" }
        }
        return null
    }

    private fun activeWorkScope(tasks: List<VendiTask>, activeTask: VendiTask): List<VendiTask> {
        val byId = linkedMapOf<String, VendiTask>()
        fun add(task: VendiTask?) {
            if (task != null) byId[task.id] = task
        }

        fun addServiceBundle(task: VendiTask) {
            TaskBundleHelpers.unifiedServiceBundleTasks(tasks, task).forEach(::add)
        }

        add(activeTask)
        addServiceBundle(activeTask)
        if (activeTask.type == TaskType.MachinePickupInventory) {
            refillTasksForPickup(tasks, activeTask).forEach { refillTask ->
                add(refillTask)
                addServiceBundle(refillTask)
            }
        }
        return byId.values.sortedWith(
            compareBy<VendiTask> { taskTypeSortRank(it.type) }
                .thenBy { it.createdAt.orEmpty() }
                .thenBy { it.id }
        )
    }

    private fun refillTasksForPickup(tasks: List<VendiTask>, pickupTask: VendiTask): List<VendiTask> {
        val refillIds = (
            pickupTask.refillTaskIds +
                listOfNotNull(pickupTask.refillTaskId) +
                pickupTask.pickupLines.mapNotNull { it.refillTaskId }
            ).toSet()
        if (refillIds.isEmpty()) return emptyList()
        return tasks.filter { it.type == TaskType.MachineRefill && it.id in refillIds }
    }

    private val serviceBundleChildTypes = setOf(
        TaskType.MachineRefill,
        TaskType.MachineClean,
        TaskType.MachineCollection
    )
}
