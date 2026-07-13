package com.vendistri.operations.features.tasks

internal sealed class TaskMachineAssigneeDisplay {
    data class Summary(val text: String) : TaskMachineAssigneeDisplay()
    data object Mixed : TaskMachineAssigneeDisplay()
    data object None : TaskMachineAssigneeDisplay()
}

internal object TaskMachineAssigneeHelper {
    fun display(tasks: List<VendiTask>): TaskMachineAssigneeDisplay {
        val assignedTasks = tasks.filter { it.assignee != null }
        if (assignedTasks.isEmpty()) {
            return TaskMachineAssigneeDisplay.Summary("Unassigned")
        }

        if (assignedTasks.size != tasks.size) return TaskMachineAssigneeDisplay.Mixed

        val firstAssignee = assignedTasks.firstOrNull()?.assignee ?: return TaskMachineAssigneeDisplay.None
        if (assignedTasks.any { it.assignee != firstAssignee }) return TaskMachineAssigneeDisplay.Mixed

        val assigneeText = assignedTasks.firstOrNull()?.let { task ->
            task.assigneeName ?: task.assigneeEmail ?: task.assignee
        } ?: "Unassigned"
        return TaskMachineAssigneeDisplay.Summary("Assignee: $assigneeText")
    }

    fun showsPerTaskAssignee(tasks: List<VendiTask>): Boolean {
        return display(tasks) is TaskMachineAssigneeDisplay.Mixed
    }
}

internal object TaskMachineVisitMetricFormatter {
    fun visitMetricText(tasks: List<VendiTask>): String? {
        val task = tasks.firstOrNull { it.type == TaskType.MachineService }
            ?: tasks.firstOrNull { it.type != TaskType.MachinePickupInventory }
            ?: tasks.firstOrNull()
        val days = task?.daysSinceLastVisit ?: return null
        return "Last visit $days ${if (days == 1) "day" else "days"}"
    }
}
