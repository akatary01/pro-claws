package com.vendistri.operations.features.work

import com.vendistri.operations.features.tasks.TaskBundleHelpers
import com.vendistri.operations.features.tasks.TaskMachineGroup
import com.vendistri.operations.features.tasks.TaskStateHelpers
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.VendiTask

enum class RouteStartScopeChoice {
    SelectedMachine,
    FullStop
}

data class RouteStartScopeOption(
    val choice: RouteStartScopeChoice,
    val title: String,
    val subtitle: String,
    val taskIds: Set<String>,
    val claimTaskIds: Set<String>,
    val claimTasks: List<VendiTask> = emptyList()
)

data class RouteStartScopeDecision(
    val stopTitle: String,
    val selectedMachineOption: RouteStartScopeOption,
    val fullStopOption: RouteStartScopeOption,
    val defaultChoice: RouteStartScopeChoice,
    val requiresChoice: Boolean,
    val requiresConfirmation: Boolean,
    val laterRefillTasks: List<VendiTask> = emptyList()
) {
    fun option(choice: RouteStartScopeChoice): RouteStartScopeOption {
        return when (choice) {
            RouteStartScopeChoice.SelectedMachine -> selectedMachineOption
            RouteStartScopeChoice.FullStop -> fullStopOption
        }
    }
}

object RouteStartScopeResolver {
    fun decision(
        stop: GoStopPlan,
        selectedTask: VendiTask?,
        allTasks: List<VendiTask>,
        currentUserId: String?,
        status: (VendiTask) -> TaskStatus = { it.status }
    ): RouteStartScopeDecision? {
        val stopTasks = activeTasks(
            TaskExecutionResolver.hydratedTasks(stop.tasks, allTasks),
            status
        )
        if (stopTasks.isEmpty()) return null

        val hasAnchorTask = (
            selectedTask
            ?.let { selected -> allTasks.firstOrNull { it.id == selected.id } ?: selected }
            ?: TaskExecutionResolver.currentExecutableTask(stopTasks, status)
            ?: stopTasks.firstOrNull()
            ) != null
        if (!hasAnchorTask) return null

        val assignedTasks = expandedServiceBundleTasks(
            tasks = stopTasks.filter { isAssignedToCurrentUser(it, currentUserId) },
            allTasks = stopTasks,
            status = status
        )
        val fullStopTasks = expandedServiceBundleTasks(
            tasks = stopTasks,
            allTasks = stopTasks,
            status = status
        )
        if (fullStopTasks.isEmpty()) return null

        val assignedIds = assignedTasks.map { it.id }.toSet()
        val fullIds = fullStopTasks.map { it.id }.toSet()
        val otherTasks = fullStopTasks.filter { it.id !in assignedIds }
        val hasOtherMachineTasks = otherTasks.isNotEmpty() && fullStopTasks.map(::machineKey).toSet().size > 1
        val hasOtherWorkNotAssignedToCurrentUser = otherTasks.any { !isAssignedToCurrentUser(it, currentUserId) }
        val hasAssignedWork = assignedTasks.isNotEmpty()
        val hasClaimableWork = fullStopTasks.any(::isClaimable)
        val requiresChoice = hasAssignedWork && (
            hasClaimableWork ||
                (hasOtherMachineTasks && (hasOtherWorkNotAssignedToCurrentUser || hasMixedOwnership(fullStopTasks, currentUserId)))
            )
        val requiresConfirmation = requiresChoice || hasClaimableWork

        val selectedOption = RouteStartScopeOption(
            choice = RouteStartScopeChoice.SelectedMachine,
            title = "Start assigned tasks only",
            subtitle = summaryText(assignedTasks, status),
            taskIds = assignedIds,
            claimTaskIds = emptySet()
        )
        val fullOption = RouteStartScopeOption(
            choice = RouteStartScopeChoice.FullStop,
            title = "Start all tasks at ${stop.title}",
            subtitle = summaryText(fullStopTasks, status),
            taskIds = fullIds,
            claimTaskIds = fullStopTasks.filter(::isClaimable).map { it.id }.toSet(),
            claimTasks = fullStopTasks.filter(::isClaimable)
        )

        return RouteStartScopeDecision(
            stopTitle = stop.title,
            selectedMachineOption = selectedOption,
            fullStopOption = fullOption,
            defaultChoice = if (requiresChoice) RouteStartScopeChoice.SelectedMachine else RouteStartScopeChoice.FullStop,
            requiresChoice = requiresChoice,
            requiresConfirmation = requiresConfirmation
        )
    }

    fun expandedTaskIds(
        tasks: List<VendiTask>,
        allTasks: List<VendiTask>,
        status: (VendiTask) -> TaskStatus = { it.status }
    ): Set<String> {
        return expandedServiceBundleTasks(tasks, allTasks, status).map { it.id }.toSet()
    }

    private fun activeTasks(tasks: List<VendiTask>, status: (VendiTask) -> TaskStatus): List<VendiTask> {
        return tasks.filter { !TaskStateHelpers.isFinal(status(it)) }
    }

    private fun expandedServiceBundleTasks(
        tasks: List<VendiTask>,
        allTasks: List<VendiTask>,
        status: (VendiTask) -> TaskStatus
    ): List<VendiTask> {
        val allActiveTasks = activeTasks(allTasks, status)
        val seenTaskIds = mutableSetOf<String>()
        val expanded = mutableListOf<VendiTask>()
        tasks.forEach { task ->
            TaskBundleHelpers.unifiedServiceBundleTasks(allActiveTasks, task).forEach { bundledTask ->
                if (!TaskStateHelpers.isFinal(status(bundledTask)) && seenTaskIds.add(bundledTask.id)) {
                    expanded.add(bundledTask)
                }
            }
        }
        return TaskExecutionResolver.orderedDisplayTasks(expanded, status)
    }

    private fun summaryText(tasks: List<VendiTask>, status: (VendiTask) -> TaskStatus): String {
        val groups = TaskExecutionResolver.orderedMachineGroups(tasks, status)
        val taskCount = tasks.size
        return "$taskCount ${plural("task", taskCount)} for ${machineSummaryText(groups)}"
    }

    private fun machineSummaryText(groups: List<TaskMachineGroup>): String {
        val names = groups.map { it.name }
        return when (names.size) {
            0 -> "Machine"
            1 -> names[0]
            2 -> "${names[0]} and ${names[1]}"
            else -> names.dropLast(1).joinToString(", ") + ", and " + names.last()
        }
    }

    private fun plural(word: String, count: Int): String {
        return if (count == 1) word else "${word}s"
    }

    private fun isAssignedToCurrentUser(task: VendiTask, currentUserId: String?): Boolean {
        return !currentUserId.isNullOrBlank() && task.assignee == currentUserId && task.status != TaskStatus.Unassigned
    }

    private fun hasMixedOwnership(tasks: List<VendiTask>, currentUserId: String?): Boolean {
        val hasAssigned = tasks.any { isAssignedToCurrentUser(it, currentUserId) }
        val hasUnassigned = tasks.any(::isClaimable)
        return hasAssigned && hasUnassigned
    }

    private fun isClaimable(task: VendiTask): Boolean {
        return task.assignee == null || task.status == TaskStatus.Unassigned
    }

    private fun machineKey(task: VendiTask): String {
        return task.machine ?: task.machineName ?: task.id
    }
}
