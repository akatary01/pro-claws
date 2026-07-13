package com.vendistri.operations.features.tasks.reschedule

import com.vendistri.operations.features.tasks.TaskBulkPrecheckItem
import com.vendistri.operations.features.tasks.TaskBulkPrecheckResult
import com.vendistri.operations.features.tasks.TaskBundleHelpers
import com.vendistri.operations.features.tasks.TaskStateHelpers
import com.vendistri.operations.features.tasks.TasksApi
import com.vendistri.operations.features.tasks.TasksStore
import com.vendistri.operations.features.tasks.VendiTask
import com.vendistri.operations.features.tasks.toLocalDatePrefix
import java.time.LocalDate

class TaskRescheduleStore(
    private val tasksStore: TasksStore,
    private val tasksApi: TasksApi
) {
    suspend fun nextServiceCadenceDate(locationId: String?, sourceDate: LocalDate): LocalDate? {
        return locationId?.takeIf { it.isNotBlank() }?.let {
            runCatching { tasksApi.fetchNextServiceCadenceDate(it, sourceDate) }.getOrNull()
        }
    }

    suspend fun reschedule(tasks: List<VendiTask>, selectedTaskIds: Set<String>, targetDate: LocalDate): RescheduleOutcome {
        val plans = plansToApply(tasks, selectedTaskIds, targetDate)
        if (plans.isEmpty()) return RescheduleOutcome.NoChanges

        val precheckResults = tasksApi.bulkPrecheck(precheckItems(tasks, plans))
        blockedMessage(precheckResults, tasks)?.let { return RescheduleOutcome.Blocked(it) }

        val ids = plans.map { it.taskId }
        tasksStore.rescheduleTasks(ids, targetDate.toString())
        return RescheduleOutcome.Saved
    }

    fun selectedIdsAfterToggle(tasks: List<VendiTask>, selectedTaskIds: Set<String>, taskId: String): Set<String> {
        return selectedIdsAfterToggleForTasks(tasks, selectedTaskIds, taskId)
    }

    companion object {
        fun selectedIdsAfterToggle(
            tasks: List<VendiTask>,
            selectedTaskIds: Set<String>,
            taskId: String
        ): Set<String> = selectedIdsAfterToggleForTasks(tasks, selectedTaskIds, taskId)
    }

    private fun plansToApply(tasks: List<VendiTask>, selectedTaskIds: Set<String>, targetDate: LocalDate): List<TaskReschedulePlan> {
        return tasks
            .filter { it.id in selectedTaskIds && TaskStateHelpers.isActionable(it.status) }
            .mapNotNull { task ->
                val originalDate = task.scheduledFor.toLocalDatePrefix() ?: return@mapNotNull null
                if (originalDate == targetDate) return@mapNotNull null
                TaskReschedulePlan(taskId = task.id, originalDate = originalDate, targetDate = targetDate)
            }
    }

    private fun precheckItems(tasks: List<VendiTask>, plans: List<TaskReschedulePlan>): List<TaskBulkPrecheckItem> {
        val tasksById = tasks.associateBy { it.id }
        return plans.mapNotNull { plan ->
            val task = tasksById[plan.taskId] ?: return@mapNotNull null
            val machineId = task.machine?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            TaskBulkPrecheckItem(
                type = task.type,
                machineId = machineId,
                scheduledFor = plan.targetDate,
                taskId = task.id
            )
        }
    }

    private fun blockedMessage(results: List<TaskBulkPrecheckResult>, tasks: List<VendiTask>): String? {
        val blocked = results.firstOrNull { !it.ok } ?: return null
        val machineName = tasks.firstOrNull { it.machine == blocked.machineId }?.machineName ?: "Machine"
        val reason = blocked.reason.orEmpty().lowercase()
        return when {
            blocked.existingTask != null && reason == "same_day_existing" ->
                "$machineName already has a ${blocked.existingTask.type.rawValue.replace('_', ' ')} task scheduled for ${blocked.existingTask.scheduledFor}."
            reason.contains("payment") ->
                "Collection tasks require a machine with at least one enabled payment method. Update $machineName and try again."
            reason.contains("inactive") ->
                "$machineName is inactive. Activate the machine before rescheduling."
            reason.contains("matching query does not exist") || reason.contains("unassigned") ->
                "$machineName is not assigned to a location. Assign it before rescheduling."
            else ->
                "These tasks cannot be rescheduled right now. Review the machine setup and try again."
        }
    }
}

private fun selectedIdsAfterToggleForTasks(
    tasks: List<VendiTask>,
    selectedTaskIds: Set<String>,
    taskId: String
): Set<String> {
    val task = tasks.firstOrNull { it.id == taskId } ?: return selectedTaskIds
    val linkedIds = TaskBundleHelpers.unifiedServiceBundleTasks(tasks, task).map { it.id }.toSet()
    return if (linkedIds.all { it in selectedTaskIds }) {
        selectedTaskIds - linkedIds
    } else {
        selectedTaskIds + linkedIds
    }
}

data class TaskReschedulePlan(
    val taskId: String,
    val originalDate: LocalDate,
    val targetDate: LocalDate
)

sealed interface RescheduleOutcome {
    data object Saved : RescheduleOutcome
    data object NoChanges : RescheduleOutcome
    data class Blocked(val message: String) : RescheduleOutcome
}
