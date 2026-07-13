package com.vendistri.operations.features.work

import com.vendistri.operations.features.map.LocationCoordinate
import com.vendistri.operations.features.tasks.CollectionInputMode
import com.vendistri.operations.features.tasks.TaskMachineGroup
import com.vendistri.operations.features.tasks.TaskPickupLine
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskExecutionDisplayTest {
    @Test
    fun displayMetricsUseWrittenTaskMetricsWhenNoTaskOwnsLiveExecution() {
        val stop = stopPlan(
            tasks = listOf(
                task(
                    id = "task-1",
                    type = TaskType.MachineRefill,
                    status = TaskStatus.Done,
                    duration = 720.0,
                    distance = 0.3,
                    net = 5.0
                ),
                task(
                    id = "task-2",
                    type = TaskType.MachineCollection,
                    status = TaskStatus.Pending,
                    duration = 240.0,
                    distance = 0.2,
                    net = 7.0
                )
            )
        )
        val execution = TaskExecutionPlanner.activeExecution(stop).copy(distanceMiles = 2.4, net = 12.0)

        assertEquals("1/2", TaskExecutionDisplay.progressText(execution))
        assertEquals("16m 0s", TaskExecutionDisplay.timeText(execution))
        assertEquals("0.5 mi", TaskExecutionDisplay.distanceText(execution))
        assertEquals("$12.00", TaskExecutionDisplay.netText(execution))
    }

    @Test
    fun displayMetricsUseLiveDistanceOnlyForDistanceOwner() {
        val stop = stopPlan(
            tasks = listOf(
                task(
                    id = "service",
                    type = TaskType.MachineService,
                    status = TaskStatus.Pending,
                    duration = 0.0,
                    distance = 0.1,
                    net = 0.0,
                    startedAt = "2026-07-03T11:00:00Z"
                ),
                task(
                    id = "refill",
                    type = TaskType.MachineRefill,
                    status = TaskStatus.Pending,
                    duration = 0.0,
                    distance = 0.2,
                    net = 0.0,
                    serviceTaskId = "service",
                    startedAt = "2026-07-03T11:00:00Z"
                )
            )
        )
        val execution = TaskExecutionPlanner.activeExecution(stop).copy(
            wrapperTaskId = "service",
            currentTaskId = "refill",
            distanceMiles = 3.0,
            taskStartDistanceMilesByTaskId = mapOf("service" to 1.0, "refill" to 1.0)
        )

        assertEquals("2.0 mi", TaskExecutionDisplay.distanceText(execution))
    }

    @Test
    fun canFinishVisitAllowsPendingServiceWrapperAfterChildrenAreFinal() {
        val execution = TaskExecutionPlanner.activeExecution(
            stopPlan(
                tasks = listOf(
                    task(
                        id = "service",
                        type = TaskType.MachineService,
                        status = TaskStatus.Pending,
                        duration = 0.0,
                        distance = 0.0,
                        net = 0.0
                    ),
                    task(
                        id = "refill",
                        type = TaskType.MachineRefill,
                        status = TaskStatus.Done,
                        duration = 5.0,
                        distance = 0.2,
                        net = 3.0,
                        serviceTaskId = "service"
                    )
                )
            )
        )

        assertEquals(true, TaskExecutionDisplay.canFinishVisit(execution))
    }

    @Test
    fun canFinishVisitBlocksUnfinishedNonServiceTasks() {
        val execution = TaskExecutionPlanner.activeExecution(
            stopPlan(
                tasks = listOf(
                    task(
                        id = "service",
                        type = TaskType.MachineService,
                        status = TaskStatus.Pending,
                        duration = 0.0,
                        distance = 0.0,
                        net = 0.0
                    ),
                    task(
                        id = "refill",
                        type = TaskType.MachineRefill,
                        status = TaskStatus.Pending,
                        duration = 5.0,
                        distance = 0.2,
                        net = 3.0,
                        serviceTaskId = "service"
                    )
                )
            )
        )

        assertEquals(false, TaskExecutionDisplay.canFinishVisit(execution))
    }

    @Test
    fun canFinishVisitCanUseHydratedTasksWhenExecutionSnapshotIsStale() {
        val staleExecution = TaskExecutionPlanner.activeExecution(
            stopPlan(
                tasks = listOf(
                    task(
                        id = "pickup",
                        type = TaskType.MachinePickupInventory,
                        status = TaskStatus.Pending,
                        duration = 0.0,
                        distance = 0.0,
                        net = 0.0
                    )
                )
            )
        )
        val hydratedTasks = listOf(staleExecution.displayTasks.first().copy(status = TaskStatus.Done))

        assertEquals(false, TaskExecutionDisplay.canFinishVisit(staleExecution))
        assertEquals(true, TaskExecutionDisplay.canFinishVisit(hydratedTasks))
        assertEquals(1, TaskExecutionDisplay.remainingTaskCount(staleExecution))
        assertEquals(0, TaskExecutionDisplay.remainingTaskCount(hydratedTasks))
        assertEquals("All tasks are done", TaskExecutionDisplay.cancelRemainingTitle(hydratedTasks))
    }

    @Test
    fun remainingTasksIncludesPendingServiceWrapperAndChildren() {
        val execution = TaskExecutionPlanner.activeExecution(
            stopPlan(
                tasks = listOf(
                    task(
                        id = "service",
                        type = TaskType.MachineService,
                        status = TaskStatus.Pending,
                        duration = 0.0,
                        distance = 0.0,
                        net = 0.0
                    ),
                    task(
                        id = "refill",
                        type = TaskType.MachineRefill,
                        status = TaskStatus.Pending,
                        duration = 5.0,
                        distance = 0.2,
                        net = 3.0,
                        serviceTaskId = "service"
                    ),
                    task(
                        id = "collection",
                        type = TaskType.MachineCollection,
                        status = TaskStatus.Done,
                        duration = 5.0,
                        distance = 0.2,
                        net = 3.0,
                        serviceTaskId = "service"
                    )
                )
            )
        )

        assertEquals(listOf("service", "refill"), TaskExecutionDisplay.remainingTasks(execution).map { it.id })
        assertEquals(2, TaskExecutionDisplay.remainingTaskCount(execution))
        assertEquals("Cancel 2 remaining tasks", TaskExecutionDisplay.cancelRemainingTitle(execution))
    }

    @Test
    fun cancelRemainingTitleHandlesEmptyRemainingTasks() {
        val execution = TaskExecutionPlanner.activeExecution(
            stopPlan(
                tasks = listOf(
                    task(
                        id = "task-1",
                        type = TaskType.MachineRefill,
                        status = TaskStatus.Done,
                        duration = 5.0,
                        distance = 0.2,
                        net = 3.0
                    )
                )
            )
        )

        assertEquals(0, TaskExecutionDisplay.remainingTaskCount(execution))
        assertEquals("All tasks are done", TaskExecutionDisplay.cancelRemainingTitle(execution))
    }

    @Test
    fun activeExecutionPutsCurrentMachineFirstAndKeepsThatDisplayOrder() {
        val machineOneDone = task(
            id = "machine-1-refill",
            type = TaskType.MachineRefill,
            status = TaskStatus.Done,
            duration = 5.0,
            distance = 0.2,
            net = 3.0,
            machine = "machine-1",
            machineName = "Arcadia"
        )
        val machineTwoPending = task(
            id = "machine-2-collection",
            type = TaskType.MachineCollection,
            status = TaskStatus.Pending,
            duration = 5.0,
            distance = 0.2,
            net = 3.0,
            machine = "machine-2",
            machineName = "City Tire"
        )

        val execution = TaskExecutionPlanner.activeExecution(stopPlan(listOf(machineOneDone, machineTwoPending)))

        assertEquals("machine-2-collection", execution.currentTaskId)
        assertEquals(listOf("machine-2", "machine-1"), execution.machineGroups.map { it.id })
        assertEquals(listOf("machine-2-collection", "machine-1-refill"), execution.displayTasks.map { it.id })
    }

    private fun stopPlan(tasks: List<VendiTask>): GoStopPlan {
        return GoStopPlan(
            id = "location-1",
            targetLocationId = "location-1",
            title = "Downtown Office",
            addressStreetLine = "123 Main St",
            addressCityStateZipLine = "New York, NY 10001",
            tasks = tasks,
            nodes = listOf(
                GoNode(
                    id = "location-location-1",
                    type = GoNodeType.Location,
                    title = "Downtown Office",
                    subtitle = "123 Main St",
                    coordinate = LocationCoordinate(40.7128, -74.0060),
                    locationId = "location-1",
                    taskIds = tasks.map { it.id }
                )
            ),
            machineGroups = listOf(
                TaskMachineGroup(
                    id = "machine-1",
                    name = "Machine",
                    tasks = tasks,
                    durationMinutes = tasks.sumOf { it.duration ?: 0.0 },
                    distanceMiles = tasks.sumOf { it.distance ?: 0.0 }
                )
            ),
            gross = tasks.sumOf { it.gross ?: 0.0 },
            refunds = tasks.sumOf { it.refunds ?: 0.0 },
            commission = tasks.sumOf { it.commission ?: 0.0 },
            net = tasks.sumOf { it.net ?: 0.0 }
        )
    }

    private fun task(
        id: String,
        type: TaskType,
        status: TaskStatus,
        duration: Double,
        distance: Double,
        net: Double,
        serviceTaskId: String? = null,
        startedAt: String? = null,
        machine: String = "machine-1",
        machineName: String = "Machine"
    ): VendiTask {
        return VendiTask(
            id = id,
            type = type,
            status = status,
            isPublic = false,
            assignee = "user-1",
            assigneeName = "Operator",
            assigneeEmail = "operator@vendistri.com",
            machine = machine,
            machineName = machineName,
            collectionInputMode = CollectionInputMode.Dollars,
            creditsPerDollar = null,
            location = "location-1",
            locationName = "Downtown Office",
            locationAddress = null,
            scheduledFor = "2026-07-03",
            createdAt = "2026-07-03T10:00:00Z",
            startedAt = startedAt,
            doneAt = null,
            isLive = false,
            duration = duration,
            notes = null,
            distance = distance,
            gross = net,
            grossCash = null,
            grossCard = null,
            refunds = 0.0,
            commission = 0.0,
            net = net,
            serviceTaskId = serviceTaskId,
            refillTaskId = null,
            refillTaskIds = emptyList(),
            pickupLines = emptyList<TaskPickupLine>()
        )
    }
}
