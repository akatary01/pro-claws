package com.vendistri.operations.features.work

import com.vendistri.operations.features.tasks.TaskStateHelpers
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask
import com.vendistri.operations.features.tasks.formatTaskDuration
import com.vendistri.operations.features.tasks.money
import com.vendistri.operations.features.tasks.oneDecimal

object TaskExecutionDisplay {
    fun progressText(execution: ActiveTaskExecution): String {
        val total = execution.displayTasks.size
        if (total == 0) return "0/0"
        val completed = execution.displayTasks.count { TaskStateHelpers.isFinal(it.status) }
        return "${completed.coerceAtMost(total)}/$total"
    }

    fun timeText(
        execution: ActiveTaskExecution,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): String {
        return formatTaskDuration(TaskExecutionMetrics.totals(execution, nowEpochMillis).durationMinutes * 60.0)
    }

    fun distanceText(
        execution: ActiveTaskExecution,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): String {
        return "${oneDecimal(TaskExecutionMetrics.totals(execution, nowEpochMillis).distanceMiles.coerceAtLeast(0.0))} mi"
    }

    fun netText(execution: ActiveTaskExecution): String {
        return "$${money(execution.net)}"
    }

    fun canFinishVisit(execution: ActiveTaskExecution): Boolean {
        return canFinishVisit(execution.displayTasks)
    }

    fun canFinishVisit(tasks: List<VendiTask>): Boolean {
        return tasks.isNotEmpty() &&
            tasks.filter { !TaskStateHelpers.isFinal(it.status) }.none { it.type != TaskType.MachineService }
    }

    fun remainingTasks(execution: ActiveTaskExecution): List<VendiTask> {
        return remainingTasks(execution.displayTasks)
    }

    fun remainingTasks(tasks: List<VendiTask>): List<VendiTask> {
        return tasks.filter { !TaskStateHelpers.isFinal(it.status) }
    }

    fun remainingTaskCount(execution: ActiveTaskExecution): Int {
        return remainingTaskCount(execution.displayTasks)
    }

    fun remainingTaskCount(tasks: List<VendiTask>): Int {
        return remainingTasks(tasks).size
    }

    fun cancelRemainingTitle(execution: ActiveTaskExecution): String {
        return cancelRemainingTitle(execution.displayTasks)
    }

    fun cancelRemainingTitle(tasks: List<VendiTask>): String {
        val count = remainingTaskCount(tasks)
        return if (count == 0) {
            "All tasks are done"
        } else {
            "Cancel $count remaining ${if (count == 1) "task" else "tasks"}"
        }
    }
}
