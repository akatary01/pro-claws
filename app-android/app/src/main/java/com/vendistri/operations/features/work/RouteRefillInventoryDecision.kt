package com.vendistri.operations.features.work

import com.vendistri.operations.features.tasks.TaskStateHelpers
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.TaskScheduleDate
import com.vendistri.operations.features.tasks.VendiTask

object RouteRefillInventoryDecision {
    fun anchorTask(
        stop: GoStopPlan?,
        allTasks: List<VendiTask>,
        currentUserId: String?,
        bypassedTaskIds: Set<String>,
        selectedTaskIds: Set<String> = emptySet()
    ): VendiTask? {
        if (stop == null) return null
        val currentTask = TaskExecutionResolver.currentExecutableTask(stop.tasks) ?: return null
        val liveTask = allTasks.firstOrNull { it.id == currentTask.id } ?: currentTask
        return liveTask.takeIf {
            isEligibleRefillTask(
                task = it,
                currentUserId = currentUserId,
                scheduledFor = liveTask.scheduledFor,
                bypassedTaskIds = bypassedTaskIds,
                selectedTaskIds = selectedTaskIds
            )
        }
    }

    fun eligibleTasks(
        plan: GoPlan?,
        anchorTask: VendiTask,
        allTasks: List<VendiTask>,
        currentUserId: String?,
        bypassedTaskIds: Set<String>,
        selectedTaskIds: Set<String> = emptySet()
    ): List<VendiTask> {
        val sourceTasks = listOf(anchorTask) + plan?.tasks.orEmpty()
        val seenTaskIds = mutableSetOf<String>()
        val eligible = sourceTasks.mapNotNull { task ->
            val liveTask = allTasks.firstOrNull { it.id == task.id } ?: task
            if (!seenTaskIds.add(liveTask.id)) return@mapNotNull null
            liveTask.takeIf {
                isEligibleRefillTask(
                    task = it,
                    currentUserId = currentUserId,
                    scheduledFor = anchorTask.scheduledFor,
                    bypassedTaskIds = bypassedTaskIds,
                    selectedTaskIds = selectedTaskIds
                )
            }
        }
        return eligible.ifEmpty { listOf(anchorTask) }
    }

    fun eligibleTasks(
        plan: GoStopPlan?,
        anchorTask: VendiTask,
        allTasks: List<VendiTask>,
        currentUserId: String?,
        bypassedTaskIds: Set<String>,
        selectedTaskIds: Set<String> = emptySet()
    ): List<VendiTask> {
        val goPlan = plan?.let {
            GoPlan(
                generatedAtEpochMillis = 0L,
                tasks = it.tasks,
                stops = listOf(it),
                suggestedStopId = it.id
            )
        }
        return eligibleTasks(
            plan = goPlan,
            anchorTask = anchorTask,
            allTasks = allTasks,
            currentUserId = currentUserId,
            bypassedTaskIds = bypassedTaskIds,
            selectedTaskIds = selectedTaskIds
        )
    }

    private fun isEligibleRefillTask(
        task: VendiTask,
        currentUserId: String?,
        scheduledFor: String,
        bypassedTaskIds: Set<String>,
        selectedTaskIds: Set<String>
    ): Boolean {
        if (task.type != TaskType.MachineRefill) return false
        val scheduledDate = TaskScheduleDate.parse(scheduledFor) ?: return false
        if (!TaskScheduleDate.isSameDay(task.scheduledFor, scheduledDate)) return false
        if (TaskStateHelpers.isFinal(task.status)) return false
        if (!isAssignedToCurrentUser(task, currentUserId) && !selectedTaskIds.contains(task.id)) return false
        if (task.inventoryCompletion != null) return false
        if (bypassedTaskIds.contains(task.id)) return false
        return true
    }

    private fun isAssignedToCurrentUser(task: VendiTask, currentUserId: String?): Boolean {
        return !currentUserId.isNullOrBlank() && task.assignee == currentUserId
    }
}
