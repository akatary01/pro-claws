package com.vendistri.operations.features.tasks

internal object TaskRowActionPolicy {
    fun canClaim(task: VendiTask): Boolean {
        return TaskStateHelpers.isActionable(task.status) && (task.status == TaskStatus.Unassigned || task.assignee == null)
    }

    fun canUseSimpleDoneAction(task: VendiTask): Boolean {
        return task.status == TaskStatus.Pending &&
            task.assignee != null &&
            task.type != TaskType.MachineRefill &&
            task.type != TaskType.MachinePickupInventory
    }

    fun claimableTasks(tasks: List<VendiTask>): List<VendiTask> {
        return tasks.filter(::canClaim)
    }

    fun simpleDoneTasks(tasks: List<VendiTask>): List<VendiTask> {
        return tasks.filter(::canUseSimpleDoneAction)
    }

    fun assigneeSummary(tasks: List<VendiTask>): String? {
        val assigneeLines = tasks
            .filter { TaskStateHelpers.isActionable(it.status) }
            .mapNotNull(TaskStateHelpers::assigneeLine)
            .distinct()
        return when (assigneeLines.size) {
            0 -> null
            1 -> assigneeLines.first()
            else -> "Mixed assignees"
        }
    }
}
