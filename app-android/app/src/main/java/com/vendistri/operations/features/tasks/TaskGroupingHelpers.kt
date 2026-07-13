package com.vendistri.operations.features.tasks

import com.vendistri.operations.utils.AddressFormatter

data class TaskLocationGroup(
    val id: String,
    val name: String,
    val addressStreetLine: String?,
    val addressCityStateZipLine: String?,
    val tasks: List<VendiTask>,
    val machineGroups: List<TaskMachineGroup>,
    val doneCount: Int,
    val cancelledCount: Int,
    val totalCount: Int,
    val gross: Double,
    val commission: Double,
    val net: Double,
    val durationMinutes: Double,
    val distanceMiles: Double
)

data class TaskMachineGroup(
    val id: String,
    val name: String,
    val tasks: List<VendiTask>,
    val durationMinutes: Double,
    val distanceMiles: Double
)

object TaskGroupingHelpers {
    private data class TaskDisplayEntry(
        val task: VendiTask,
        val locationId: String,
        val locationName: String,
        val addressStreetLine: String?,
        val addressCityStateZipLine: String?,
        val machineId: String,
        val machineName: String
    )

    fun groupByLocation(tasks: List<VendiTask>, lookupTasks: List<VendiTask> = tasks): List<TaskLocationGroup> {
        val entries = displayEntries(tasks = tasks, lookupTasks = lookupTasks)
        return entries
            .groupBy { it.locationId }
            .mapNotNull { (locationId, groupEntries) ->
                val first = groupEntries.firstOrNull() ?: return@mapNotNull null
                val groupTasks = uniqueTasksById(groupEntries.map { it.task })
                val machineGroups = groupByMachineEntries(groupEntries)
                val financials = TaskFinancialHelpers.sumTaskFinancials(groupTasks)
                TaskLocationGroup(
                    id = locationId,
                    name = first.locationName,
                    addressStreetLine = first.addressStreetLine,
                    addressCityStateZipLine = first.addressCityStateZipLine,
                    tasks = groupTasks,
                    machineGroups = machineGroups,
                    doneCount = groupTasks.count { it.status == TaskStatus.Done },
                    cancelledCount = groupTasks.count { it.status == TaskStatus.Cancelled || it.status == TaskStatus.Error },
                    totalCount = groupTasks.size,
                    gross = financials.gross,
                    commission = financials.commission,
                    net = financials.net,
                    durationMinutes = totalDurationMinutes(groupTasks),
                    distanceMiles = totalDistanceMiles(groupTasks)
                )
            }
            .sortedWith(compareBy<TaskLocationGroup> { locationStatusRank(it.tasks) }.thenBy { it.name.lowercase() })
    }

    fun groupByMachine(tasks: List<VendiTask>, lookupTasks: List<VendiTask> = tasks): List<TaskMachineGroup> {
        return groupByMachineEntries(displayEntries(tasks = tasks, lookupTasks = lookupTasks))
    }

    fun uniqueTasksById(tasks: List<VendiTask>): List<VendiTask> {
        val seen = mutableSetOf<String>()
        return tasks.filter { seen.add(it.id) }
    }

    fun statusDisplayComparator(
        status: (VendiTask) -> TaskStatus = { it.status }
    ): Comparator<VendiTask> {
        return compareBy<VendiTask> { TaskStatusPresentation.sortRank(status(it)) }
            .thenBy { taskTypeSortRank(it.type) }
            .thenBy { taskTypeLabel(it.type).lowercase() }
            .thenBy { it.id }
    }

    private fun groupByMachineEntries(entries: List<TaskDisplayEntry>): List<TaskMachineGroup> {
        return entries
            .groupBy { it.machineId }
            .mapNotNull { (machineId, groupEntries) ->
                val first = groupEntries.firstOrNull() ?: return@mapNotNull null
                val tasks = uniqueTasksById(groupEntries.map { it.task }).sortedWith(taskSort)
                TaskMachineGroup(
                    id = machineId,
                    name = first.machineName,
                    tasks = tasks,
                    durationMinutes = machineDurationMinutes(tasks),
                    distanceMiles = machineDistanceMiles(tasks)
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    private fun displayEntries(tasks: List<VendiTask>, lookupTasks: List<VendiTask>): List<TaskDisplayEntry> {
        val refillTasksById = lookupTasks
            .filter { it.type == TaskType.MachineRefill }
            .associateBy { it.id }

        return tasks.flatMap { task ->
            if (task.type != TaskType.MachinePickupInventory) {
                return@flatMap listOf(displayEntry(task))
            }

            val linkedRefillTasks = linkedRefillTaskIds(task).mapNotNull(refillTasksById::get)
            if (linkedRefillTasks.isEmpty()) {
                listOf(displayEntry(task))
            } else {
                linkedRefillTasks
                    .distinctBy { "${it.location ?: "unknown"}|${it.machine ?: "unknown"}|${task.id}" }
                    .map { refillTask -> displayEntry(task, locationTask = refillTask, machineTask = refillTask) }
            }
        }
    }

    private fun linkedRefillTaskIds(task: VendiTask): List<String> {
        return (task.refillTaskIds + listOfNotNull(task.refillTaskId) + task.pickupLines.mapNotNull { it.refillTaskId })
            .distinct()
    }

    private fun displayEntry(
        task: VendiTask,
        locationTask: VendiTask? = null,
        machineTask: VendiTask? = null
    ): TaskDisplayEntry {
        return TaskDisplayEntry(
            task = task,
            locationId = locationTask?.location ?: task.location ?: "unknown",
            locationName = locationTask?.locationName ?: task.locationName ?: "Location",
            addressStreetLine = locationTask?.locationAddress?.street ?: task.locationAddress?.street,
            addressCityStateZipLine = AddressFormatter.cityStateZipLine(locationTask?.locationAddress ?: task.locationAddress),
            machineId = machineTask?.machine ?: task.machine ?: "unknown",
            machineName = machineTask?.machineName ?: task.machineName ?: "Machine"
        )
    }

    private val taskSort = compareBy<VendiTask>({ TaskStatusPresentation.sortRank(it.status) }, { taskTypeSortRank(it.type) }, { it.displayTitle }, { it.id })

    private fun locationStatusRank(tasks: List<VendiTask>): Int {
        return tasks.minOfOrNull { TaskStatusPresentation.sortRank(it.status) }
            ?: TaskStatusPresentation.sortRank(TaskStatus.Pending)
    }

    fun totalDistanceMiles(tasks: List<VendiTask>): Double {
        return metricTaskGroupsByMachine(tasks).sumOf(::machineDistanceMiles)
    }

    fun totalDurationMinutes(tasks: List<VendiTask>): Double {
        return metricTaskGroupsByMachine(tasks).sumOf(::machineDurationMinutes)
    }

    private fun metricTaskGroupsByMachine(tasks: List<VendiTask>): List<List<VendiTask>> {
        return uniqueTasksById(tasks)
            .groupBy { it.machine ?: it.machineName ?: it.id }
            .values
            .map { it.toList() }
    }

    private fun machineDistanceMiles(tasks: List<VendiTask>): Double {
        val uniqueTasks = uniqueTasksById(tasks)
        val serviceTask = uniqueTasks
            .filter { it.type == TaskType.MachineService && TaskStateHelpers.isFinal(it.status) }
            .maxWithOrNull(
                compareBy<VendiTask> {
                    if (it.status == TaskStatus.Done) 1 else 0
                }.thenBy { (it.distance ?: 0.0).coerceAtLeast(0.0) }
                    .thenBy { it.doneAt.orEmpty() }
            )

        if (serviceTask != null) {
            val standaloneDistance = uniqueTasks
                .filter { it.id != serviceTask.id && it.type != TaskType.MachineService && it.serviceTaskId != serviceTask.id }
                .sumOf { (it.distance ?: 0.0).coerceAtLeast(0.0) }
            return (serviceTask.distance ?: 0.0).coerceAtLeast(0.0) + standaloneDistance
        }

        return uniqueTasks
            .filter { it.type != TaskType.MachineService }
            .sumOf { (it.distance ?: 0.0).coerceAtLeast(0.0) }
    }

    private fun machineDurationMinutes(tasks: List<VendiTask>): Double {
        val uniqueTasks = uniqueTasksById(tasks)
        val serviceTask = uniqueTasks
            .filter { it.type == TaskType.MachineService }
            .maxWithOrNull(
                compareBy<VendiTask> { (it.duration ?: 0.0).coerceAtLeast(0.0) }
                    .thenBy { it.doneAt.orEmpty() }
                    .thenBy { it.startedAt.orEmpty() }
            )

        if (serviceTask != null) {
            val standaloneDuration = uniqueTasks
                .filter { it.id != serviceTask.id && it.type != TaskType.MachineService && it.serviceTaskId != serviceTask.id }
                .sumOf(::taskDurationMinutes)
            return taskDurationMinutes(serviceTask) + standaloneDuration
        }

        return uniqueTasks
            .filter { it.type != TaskType.MachineService }
            .sumOf(::taskDurationMinutes)
    }

    private fun taskDurationMinutes(task: VendiTask): Double {
        return (task.duration ?: 0.0).coerceAtLeast(0.0) / 60.0
    }
}
