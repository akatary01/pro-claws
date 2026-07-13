package com.vendistri.operations.features.work

import com.vendistri.operations.features.tasks.CollectionInputMode
import com.vendistri.operations.features.tasks.TaskPickupLine
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskExecutionMetricsTest {
    @Test
    fun durationStoredAsSecondsFormatsAsMinutes() {
        val task = task(id = "refill", duration = 5_400.0, status = TaskStatus.Done)

        val metrics = TaskExecutionMetrics.taskMetrics(task, task.status, execution = null)

        assertEquals(90.0, metrics.durationMinutes, 0.001)
    }

    @Test
    fun serviceWrapperOwnsDistanceForBundleChildren() {
        val service = task(id = "service", type = TaskType.MachineService, duration = 600.0, distance = 1.2)
        val refill = task(id = "refill", type = TaskType.MachineRefill, serviceTaskId = "service", duration = 300.0, distance = 0.8)

        val metrics = TaskExecutionMetrics.aggregateMetrics(listOf(service, refill))

        assertEquals(10.0, metrics.durationMinutes, 0.001)
        assertEquals(1.2, metrics.distanceMiles, 0.001)
    }

    @Test
    fun pickupTaskMetricsMatchCompletedTaskValues() {
        val pickup = task(
            id = "pickup",
            type = TaskType.MachinePickupInventory,
            status = TaskStatus.Done,
            duration = 1_200.0,
            distance = 0.4
        )

        val metrics = TaskExecutionMetrics.taskMetrics(pickup, pickup.status, execution = null)

        assertEquals(20.0, metrics.durationMinutes, 0.001)
        assertEquals(0.4, metrics.distanceMiles, 0.001)
    }

    @Test
    fun totalsDoNotDoubleCountServiceBundleChildren() {
        val service = task(id = "service", type = TaskType.MachineService, duration = 600.0, distance = 1.0)
        val refill = task(id = "refill", type = TaskType.MachineRefill, serviceTaskId = "service", duration = 600.0, distance = 1.0)
        val collection = task(id = "collection", type = TaskType.MachineCollection, serviceTaskId = "service", duration = 600.0, distance = 1.0)

        val metrics = TaskExecutionMetrics.aggregateMetrics(listOf(service, refill, collection))

        assertEquals(10.0, metrics.durationMinutes, 0.001)
        assertEquals(1.0, metrics.distanceMiles, 0.001)
    }

    @Test
    fun finalStatusWithoutWrittenDurationKeepsLiveDurationInsteadOfFlashingZero() {
        val task = task(
            id = "clean",
            status = TaskStatus.Done,
            startedAt = "2026-07-03T11:00:00Z",
            doneAt = null,
            duration = null
        )
        val execution = execution(currentTaskId = "clean", tasks = listOf(task))

        val metrics = TaskExecutionMetrics.taskMetrics(
            task = task,
            displayStatus = TaskStatus.Done,
            execution = execution,
            nowEpochMillis = java.time.Instant.parse("2026-07-03T11:05:00Z").toEpochMilli()
        )

        assertEquals(5.0, metrics.durationMinutes, 0.001)
    }

    @Test
    fun finalStatusWithoutWrittenDistanceKeepsLiveDistanceInsteadOfFlashingZero() {
        val task = task(
            id = "clean",
            status = TaskStatus.Done,
            startedAt = "2026-07-03T11:00:00Z",
            doneAt = null,
            distance = null
        )
        val execution = execution(
            currentTaskId = "clean",
            tasks = listOf(task),
            distanceMiles = 1.9,
            baselines = mapOf("clean" to 0.2)
        )

        val metrics = TaskExecutionMetrics.taskMetrics(
            task = task,
            displayStatus = TaskStatus.Done,
            execution = execution,
            nowEpochMillis = java.time.Instant.parse("2026-07-03T11:05:00Z").toEpochMilli()
        )

        assertEquals(1.7, metrics.distanceMiles, 0.001)
    }

    private fun task(
        id: String,
        type: TaskType = TaskType.MachineRefill,
        status: TaskStatus = TaskStatus.Done,
        serviceTaskId: String? = null,
        duration: Double? = null,
        distance: Double? = null,
        startedAt: String? = "2026-07-03T11:00:00Z",
        doneAt: String? = "2026-07-03T11:10:00Z"
    ): VendiTask {
        return VendiTask(
            id = id,
            type = type,
            status = status,
            isPublic = false,
            assignee = "user-1",
            assigneeName = "Operator",
            assigneeEmail = "operator@vendistri.com",
            machine = "machine-1",
            machineName = "Machine",
            collectionInputMode = CollectionInputMode.Dollars,
            creditsPerDollar = null,
            location = "location-1",
            locationName = "Location",
            locationAddress = null,
            scheduledFor = "2026-07-03",
            createdAt = "2026-07-03T10:00:00Z",
            startedAt = startedAt,
            doneAt = doneAt,
            isLive = false,
            duration = duration,
            notes = null,
            distance = distance,
            gross = null,
            grossCash = null,
            grossCard = null,
            refunds = null,
            commission = null,
            net = null,
            serviceTaskId = serviceTaskId,
            refillTaskId = null,
            refillTaskIds = emptyList(),
            pickupLines = emptyList<TaskPickupLine>()
        )
    }

    private fun execution(
        currentTaskId: String,
        tasks: List<VendiTask>,
        distanceMiles: Double = 0.0,
        baselines: Map<String, Double> = emptyMap()
    ): ActiveTaskExecution {
        return ActiveTaskExecution(
            stopId = "stop-1",
            title = "Location",
            locationId = "location-1",
            destinationKind = WorkDestinationKind.Location,
            taskIds = tasks.map { it.id },
            wrapperTaskId = null,
            displayTasks = tasks,
            tasks = tasks.map { task ->
                ExecutionTaskItem(
                    id = task.id,
                    type = task.type,
                    status = task.status,
                    machineId = task.machine,
                    machineName = task.machineName,
                    startedAt = task.startedAt,
                    doneAt = task.doneAt,
                    isWrapper = false
                )
            },
            machineGroups = TaskExecutionResolver.stableMachineGroups(
                groups = com.vendistri.operations.features.tasks.TaskGroupingHelpers.groupByMachine(tasks),
                previousGroups = emptyList()
            ),
            currentTaskId = currentTaskId,
            currentTaskIndex = tasks.indexOfFirst { it.id == currentTaskId }.coerceAtLeast(0),
            totalTaskCount = tasks.size,
            taskStartDistanceMilesByTaskId = baselines,
            distanceMiles = distanceMiles,
            gross = tasks.sumOf { it.gross ?: 0.0 },
            refunds = tasks.sumOf { it.refunds ?: 0.0 },
            commission = tasks.sumOf { it.commission ?: 0.0 },
            net = tasks.sumOf { it.net ?: 0.0 }
        )
    }
}
