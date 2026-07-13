package com.vendistri.operations.features.work

import com.vendistri.operations.features.tasks.TaskStateHelpers
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlin.math.max

data class TaskExecutionMetricTotals(
    val durationMinutes: Double,
    val distanceMiles: Double
)

object TaskExecutionMetrics {
    fun totals(
        execution: ActiveTaskExecution,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): TaskExecutionMetricTotals {
        return aggregateMetrics(
            tasks = execution.displayTasks,
            execution = execution,
            nowEpochMillis = nowEpochMillis
        )
    }

    fun aggregateMetrics(
        tasks: List<VendiTask>,
        execution: ActiveTaskExecution? = null,
        displayStatus: (VendiTask) -> TaskStatus = { it.status },
        nowEpochMillis: Long = System.currentTimeMillis()
    ): TaskExecutionMetricTotals {
        return metricTaskGroupsByMachine(tasks).fold(TaskExecutionMetricTotals(0.0, 0.0)) { totals, groupTasks ->
            val serviceTask = groupTasks.firstOrNull { it.type == TaskType.MachineService }
            val metrics = machineMetrics(
                tasks = groupTasks,
                serviceTask = serviceTask,
                execution = execution,
                displayStatus = displayStatus,
                nowEpochMillis = nowEpochMillis
            )
            totals.copy(
                durationMinutes = totals.durationMinutes + metrics.durationMinutes,
                distanceMiles = totals.distanceMiles + metrics.distanceMiles
            )
        }
    }

    fun distanceToSendForTask(
        task: VendiTask,
        status: TaskStatus,
        execution: ActiveTaskExecution
    ): Double? {
        if (!TaskStateHelpers.isFinal(status)) return null
        if (!isLiveDistanceOwner(task, execution)) return null
        val baseline = execution.taskStartDistanceMilesByTaskId[task.id] ?: return null
        return max(0.0, execution.distanceMiles - baseline)
    }

    fun taskMetrics(
        task: VendiTask,
        displayStatus: TaskStatus,
        execution: ActiveTaskExecution?,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): TaskExecutionMetricTotals {
        val usesWritten = TaskStateHelpers.isFinal(displayStatus)
        val usesLive = !usesWritten && isLiveOwner(task, execution)
        val canUseLiveBridge = usesWritten && isLiveOwner(task, execution)
        val writtenDurationMinutes = writtenDurationMinutes(task)
        val writtenDistanceMiles = max(0.0, task.distance ?: 0.0)
        val durationMinutes = when {
            usesWritten && writtenDurationMinutes > 0.0 -> writtenDurationMinutes
            canUseLiveBridge -> liveDurationMinutes(task, nowEpochMillis)
            usesWritten -> writtenDurationMinutes
            usesLive -> liveDurationMinutes(task, nowEpochMillis)
            else -> writtenDurationMinutes
        }
        val distanceMiles = when {
            usesWritten && writtenDistanceMiles > 0.0 -> writtenDistanceMiles
            canUseLiveBridge && isLiveDistanceOwner(task, execution) -> liveDistanceMiles(task, execution)
            usesWritten -> writtenDistanceMiles
            usesLive && isLiveDistanceOwner(task, execution) -> liveDistanceMiles(task, execution)
            else -> writtenDistanceMiles
        }
        return TaskExecutionMetricTotals(durationMinutes, distanceMiles)
    }

    fun machineMetrics(
        tasks: List<VendiTask>,
        serviceTask: VendiTask?,
        execution: ActiveTaskExecution?,
        displayStatus: (VendiTask) -> TaskStatus = { it.status },
        nowEpochMillis: Long = System.currentTimeMillis()
    ): TaskExecutionMetricTotals {
        val childTasks = tasks.filter { it.id != serviceTask?.id }
        val standaloneTasks = tasksOutsideServiceBundle(
            childTasks = childTasks,
            serviceTask = serviceTask
        ).filterNot { task ->
            isCompletedPickupContextTask(task, execution, displayStatus(task))
        }
        val standaloneTotals = standaloneTasks.fold(TaskExecutionMetricTotals(0.0, 0.0)) { totals, task ->
            val metrics = taskMetrics(task, displayStatus(task), execution, nowEpochMillis)
            totals.copy(
                durationMinutes = totals.durationMinutes + metrics.durationMinutes,
                distanceMiles = totals.distanceMiles + metrics.distanceMiles
            )
        }
        val serviceMetrics = serviceTask?.let {
            taskMetrics(it, displayStatus(it), execution, nowEpochMillis)
        }
        return TaskExecutionMetricTotals(
            durationMinutes = (serviceMetrics?.durationMinutes ?: 0.0) + standaloneTotals.durationMinutes,
            distanceMiles = (serviceMetrics?.distanceMiles ?: 0.0) + standaloneTotals.distanceMiles
        )
    }

    fun isLiveOwner(task: VendiTask, execution: ActiveTaskExecution?): Boolean {
        if (task.startedAt == null || execution == null) return false
        return task.id == execution.wrapperTaskId || task.id == execution.currentTaskId
    }

    fun isLiveDistanceOwner(task: VendiTask, execution: ActiveTaskExecution?): Boolean {
        if (task.startedAt == null || execution == null) return false
        val wrapperTaskId = execution.wrapperTaskId
        return if (wrapperTaskId != null) task.id == wrapperTaskId else task.id == execution.currentTaskId
    }

    private fun liveDistanceMiles(task: VendiTask, execution: ActiveTaskExecution?): Double {
        val safeExecution = execution ?: return max(0.0, task.distance ?: 0.0)
        val baseline = safeExecution.taskStartDistanceMilesByTaskId[task.id]
            ?: return max(0.0, task.distance ?: 0.0)
        return max(0.0, safeExecution.distanceMiles - baseline)
    }

    private fun writtenDurationMinutes(task: VendiTask): Double {
        val duration = task.duration
        if (duration != null && duration > 0.0) return duration / 60.0
        val startedAtMillis = task.startedAt?.epochMillisOrNull()
        val doneAtMillis = task.doneAt?.epochMillisOrNull()
        if (startedAtMillis != null && doneAtMillis != null) {
            return max(0.0, (doneAtMillis - startedAtMillis) / 60_000.0)
        }
        return 0.0
    }

    private fun liveDurationMinutes(task: VendiTask, nowEpochMillis: Long): Double {
        val startedAtMillis = task.startedAt?.epochMillisOrNull() ?: return 0.0
        return max(0.0, (nowEpochMillis - startedAtMillis) / 60_000.0)
    }

    private fun metricTaskGroupsByMachine(tasks: List<VendiTask>): List<List<VendiTask>> {
        return uniqueTasksById(tasks)
            .groupBy { it.machine ?: it.machineName ?: it.id }
            .values
            .map { it.toList() }
    }

    private fun uniqueTasksById(tasks: List<VendiTask>): List<VendiTask> {
        val seen = mutableSetOf<String>()
        return tasks.filter { seen.add(it.id) }
    }

    private fun tasksOutsideServiceBundle(
        childTasks: List<VendiTask>,
        serviceTask: VendiTask?
    ): List<VendiTask> {
        if (serviceTask == null) return childTasks
        return childTasks.filter { it.serviceTaskId != serviceTask.id }
    }

    private fun isCompletedPickupContextTask(
        task: VendiTask,
        execution: ActiveTaskExecution?,
        displayStatus: TaskStatus
    ): Boolean {
        if (execution == null) return false
        return task.type == TaskType.MachinePickupInventory &&
            TaskStateHelpers.isFinal(displayStatus) &&
            execution.displayTasks.any { it.type != TaskType.MachinePickupInventory }
    }
}

private fun String.epochMillisOrNull(): Long? {
    return try {
        Instant.parse(this).toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}
