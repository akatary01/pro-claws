package com.vendistri.operations.features.work

import com.vendistri.operations.features.tasks.TaskGroupingHelpers
import com.vendistri.operations.features.tasks.TaskScheduleDate
import com.vendistri.operations.features.tasks.TaskStateHelpers
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask

data class PreviousWorkScope(
    val currentUserId: String?,
    val canViewAllAssignees: Boolean
)

object PreviousWorkResolver {
    fun previousWork(
        currentTasks: List<VendiTask>,
        allTasks: List<VendiTask>,
        completedPickupTasks: List<VendiTask> = emptyList(),
        scope: PreviousWorkScope = PreviousWorkScope(currentUserId = null, canViewAllAssignees = true)
    ): List<VendiTask> {
        val firstTask = currentTasks.firstOrNull() ?: return emptyList()
        val locationId = firstTask.location ?: return emptyList()
        val scheduledDate = TaskScheduleDate.parse(firstTask.scheduledFor) ?: return emptyList()
        val excludedTaskIds = (currentTasks + completedPickupTasks).map { it.id }.toSet()

        return TaskGroupingHelpers.uniqueTasksById(allTasks)
            .filter { task ->
                task.id !in excludedTaskIds &&
                    task.location == locationId &&
                    TaskScheduleDate.isSameDay(task.scheduledFor, scheduledDate) &&
                    task.type != TaskType.MachinePickupInventory &&
                    TaskStateHelpers.isFinal(task.status) &&
                    (scope.canViewAllAssignees || task.assignee == scope.currentUserId)
            }
            .sortedWith(
                compareByDescending<VendiTask> { it.doneAt.orEmpty() }
                    .thenBy { it.displayTitle }
                    .thenBy { it.id }
            )
    }
}
