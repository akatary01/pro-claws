package com.vendistri.operations.features.work

import com.vendistri.operations.features.tasks.TaskGroupingHelpers
import com.vendistri.operations.features.tasks.TaskMachineGroup
import com.vendistri.operations.features.tasks.TaskBundleHelpers
import com.vendistri.operations.features.tasks.TaskStateHelpers
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask
import com.vendistri.operations.features.tasks.taskTypeLabel

object TaskExecutionResolver {
    fun hydratedTasks(stopTasks: List<VendiTask>, allTasks: List<VendiTask>): List<VendiTask> {
        val tasksById = TaskGroupingHelpers.uniqueTasksById(allTasks).associateBy { it.id }
        val seen = mutableSetOf<String>()
        return stopTasks.mapNotNull { task ->
            val hydrated = tasksById[task.id] ?: task
            if (seen.add(hydrated.id)) hydrated else null
        }
    }

    fun linkedPickupTasks(tasks: List<VendiTask>, allTasks: List<VendiTask>): List<VendiTask> {
        val refillTaskIds = linkedRefillTaskIds(tasks)
        if (refillTaskIds.isEmpty()) return emptyList()
        return allTasks.filter { task ->
            task.type == TaskType.MachinePickupInventory &&
                linkedRefillTaskIds(task).intersect(refillTaskIds).isNotEmpty()
        }
    }

    fun completedPickupTasks(
        linkedToTasks: List<VendiTask>,
        candidates: List<VendiTask>,
        status: (VendiTask) -> TaskStatus = { it.status }
    ): List<VendiTask> {
        return completedPickupTasks(
            linkedToRefillTaskIds = linkedRefillTaskIds(linkedToTasks),
            candidates = candidates,
            status = status
        )
    }

    fun completedPickupTasks(
        linkedToRefillTaskIds: Set<String>,
        candidates: List<VendiTask>,
        status: (VendiTask) -> TaskStatus = { it.status }
    ): List<VendiTask> {
        if (linkedToRefillTaskIds.isEmpty()) return emptyList()
        val seen = mutableSetOf<String>()
        return candidates.mapNotNull { task ->
            task.takeIf {
                seen.add(it.id) &&
                    it.type == TaskType.MachinePickupInventory &&
                    TaskStateHelpers.isFinal(status(it)) &&
                    linkedRefillTaskIds(it).intersect(linkedToRefillTaskIds).isNotEmpty()
            }
        }
    }

    fun linkedRefillTaskIds(tasks: List<VendiTask>): Set<String> {
        return tasks.flatMap { task ->
            if (task.type == TaskType.MachineRefill) {
                listOf(task.id)
            } else {
                linkedRefillTaskIds(task).toList()
            }
        }.toSet()
    }

    fun linkedRefillTaskIds(task: VendiTask): Set<String> {
        return (task.refillTaskIds + listOfNotNull(task.refillTaskId) + task.pickupLines.mapNotNull { it.refillTaskId })
            .toSet()
    }

    fun orderedDisplayTasks(
        tasks: List<VendiTask>,
        status: (VendiTask) -> TaskStatus = { it.status }
    ): List<VendiTask> {
        return orderedMachineGroups(tasks, status).flatMap { it.tasks }
    }

    fun orderedMachineGroups(
        tasks: List<VendiTask>,
        status: (VendiTask) -> TaskStatus = { it.status },
        preferredMachineId: String? = null
    ): List<TaskMachineGroup> {
        val groups = orderedMachineBundles(tasks).map { bundle ->
            val orderedTasks = orderedTasksWithinMachine(bundle.tasks, status)
            TaskMachineGroup(
                id = bundle.machineId,
                name = bundle.machineName,
                tasks = orderedTasks,
                durationMinutes = orderedTasks.sumOf { (it.duration ?: 0.0).coerceAtLeast(0.0) / 60.0 },
                distanceMiles = orderedTasks.sumOf { (it.distance ?: 0.0).coerceAtLeast(0.0) }
            )
        }
        if (preferredMachineId.isNullOrBlank()) return groups
        val preferredIndex = groups.indexOfFirst { it.id == preferredMachineId }
        if (preferredIndex <= 0) return groups
        val preferred = groups[preferredIndex]
        return listOf(preferred) + groups.filterIndexed { index, _ -> index != preferredIndex }
    }

    fun stableMachineGroups(
        groups: List<TaskMachineGroup>,
        previousGroups: List<TaskMachineGroup>
    ): List<TaskMachineGroup> {
        if (previousGroups.isEmpty()) return groups
        val groupOrder = previousGroups.mapIndexed { index, group -> group.id to index }.toMap()
        val taskOrdersByGroupId = previousGroups.associate { group ->
            group.id to group.tasks.mapIndexed { index, task -> task.id to index }.toMap()
        }
        return groups.mapIndexed { index, group -> index to group }
            .sortedWith(
                compareBy<Pair<Int, TaskMachineGroup>> { (_, group) ->
                    groupOrder[group.id] ?: Int.MAX_VALUE
                }.thenBy { (index, _) -> index }
            )
            .map { (_, group) ->
                val taskOrder = taskOrdersByGroupId[group.id] ?: return@map group
                group.copy(
                    tasks = group.tasks.mapIndexed { index, task -> index to task }
                        .sortedWith(
                            compareBy<Pair<Int, VendiTask>> { (_, task) -> taskOrder[task.id] ?: Int.MAX_VALUE }
                                .thenBy { (index, _) -> index }
                        )
                        .map { (_, task) -> task }
                )
            }
    }

    fun orderedExecutableTasks(
        tasks: List<VendiTask>,
        status: (VendiTask) -> TaskStatus = { it.status },
        preferredMachineId: String? = null
    ): List<VendiTask> {
        val bundles = orderedMachineBundles(tasks)
        val preferredBundle = preferredMachineId?.let { machineId ->
            bundles.firstOrNull { bundle ->
                bundle.machineId == machineId &&
                    bundle.tasks.any { !TaskStateHelpers.isFinal(status(it)) }
            }
        }
        val activeBundle = preferredBundle ?: bundles.firstOrNull { bundle ->
            bundle.tasks.any { !TaskStateHelpers.isFinal(status(it)) }
        } ?: return emptyList()

        return activeBundle.tasks
            .filter { !TaskStateHelpers.isFinal(status(it)) }
            .filter { task ->
                if (task.type != TaskType.MachineService) return@filter true
                !activeBundle.tasks.any { candidate ->
                    candidate.id != task.id &&
                        candidate.type != TaskType.MachineService &&
                        candidate.serviceTaskId == task.id &&
                        !TaskStateHelpers.isFinal(status(candidate))
                }
            }
            .sortedWith(
                compareBy<VendiTask> { executionPriorityRank(it.type) }
                    .thenBy { taskTypeLabel(it.type).lowercase() }
                    .thenBy { it.id }
            )
    }

    fun currentExecutableTask(
        tasks: List<VendiTask>,
        status: (VendiTask) -> TaskStatus = { it.status },
        preferredTaskId: String? = null,
        preferredMachineId: String? = null
    ): VendiTask? {
        val preferredMachine = preferredMachineId
            ?: preferredTaskId?.let { taskId -> tasks.firstOrNull { it.id == taskId }?.machine }
        val activeTasks = orderedExecutableTasks(
            tasks = tasks,
            status = status,
            preferredMachineId = preferredMachine
        )
        if (preferredTaskId != null) {
            activeTasks.firstOrNull { it.id == preferredTaskId }?.let { return it }
        }
        return activeTasks
            .filter { it.type != TaskType.MachineService }
            .filter { it.startedAt != null }
            .sortedByDescending { it.startedAt ?: it.createdAt.orEmpty() }
            .firstOrNull()
            ?: activeTasks.firstOrNull()
    }

    fun wrapperTask(
        task: VendiTask?,
        tasks: List<VendiTask>,
        status: (VendiTask) -> TaskStatus = { it.status }
    ): VendiTask? {
        if (task == null) return null
        if (task.type == TaskType.MachineService) return task
        val serviceTaskId = task.serviceTaskId ?: return null
        return tasks.firstOrNull { candidate ->
            candidate.type == TaskType.MachineService &&
                candidate.id == serviceTaskId &&
                !TaskStateHelpers.isFinal(status(candidate))
        }
    }

    fun startTask(
        tasks: List<VendiTask>,
        status: (VendiTask) -> TaskStatus = { it.status },
        preferredTaskId: String? = null,
        preferredMachineId: String? = null
    ): VendiTask? {
        val current = currentExecutableTask(
            tasks = tasks,
            status = status,
            preferredTaskId = preferredTaskId,
            preferredMachineId = preferredMachineId
        )
        return wrapperTask(task = current, tasks = tasks, status = status) ?: current
    }

    fun progress(
        tasks: List<VendiTask>,
        currentTaskId: String?,
        status: (VendiTask) -> TaskStatus = { it.status }
    ): TaskExecutionProgress {
        val ordered = orderedDisplayTasks(tasks, status)
        if (ordered.isEmpty()) return TaskExecutionProgress(current = 0, total = 0)
        val currentIndex = currentTaskId?.let { taskId ->
            ordered.indexOfFirst { it.id == taskId }.takeIf { it >= 0 }
        }
        val completedCount = ordered.count { TaskStateHelpers.isFinal(status(it)) }
        return TaskExecutionProgress(
            current = currentIndex ?: completedCount.coerceAtMost(ordered.size),
            total = ordered.size
        )
    }

    private fun orderedMachineBundles(tasks: List<VendiTask>): List<MachineBundle> {
        val encounteredIds = tasks.fold(mutableListOf<String>()) { ids, task ->
            val machineId = task.machine ?: task.id
            if (!ids.contains(machineId)) ids.add(machineId)
            ids
        }
        val grouped = tasks.groupBy { it.machine ?: it.id }
        return encounteredIds.mapNotNull { machineId ->
            val bundleTasks = grouped[machineId].orEmpty()
            val first = bundleTasks.firstOrNull() ?: return@mapNotNull null
            MachineBundle(
                machineId = machineId,
                machineName = first.machineName ?: "Machine",
                tasks = bundleTasks
            )
        }
    }

    private fun orderedTasksWithinMachine(
        tasks: List<VendiTask>,
        status: (VendiTask) -> TaskStatus
    ): List<VendiTask> {
        return tasks.sortedWith(
            compareBy<VendiTask> { displayRank(it, status(it)) }
                .thenBy { taskTypeLabel(it.type).lowercase() }
                .thenBy { it.id }
        )
    }

    private fun displayRank(task: VendiTask, status: TaskStatus): Int {
        return if (task.type == TaskType.MachineService) 0 else 10 + executionPriorityRank(task.type)
    }

    private fun executionPriorityRank(type: TaskType): Int {
        return when (type) {
            TaskType.MachinePickupInventory -> 0
            TaskType.MachineRefill -> 1
            TaskType.MachineCollection -> 2
            TaskType.MachineClean -> 3
            TaskType.MachineRepair -> 4
            TaskType.MachineRefund -> 5
            TaskType.MachineInstall -> 6
            TaskType.MachineRemove -> 7
            TaskType.MachineService -> 8
            TaskType.Default, TaskType.Other -> 9
        }
    }

    private data class MachineBundle(
        val machineId: String,
        val machineName: String,
        val tasks: List<VendiTask>
    )
}

data class TaskExecutionProgress(
    val current: Int,
    val total: Int
)
