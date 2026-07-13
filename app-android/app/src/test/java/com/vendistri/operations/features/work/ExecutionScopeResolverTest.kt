package com.vendistri.operations.features.work

import com.vendistri.operations.features.tasks.CollectionInputMode
import com.vendistri.operations.features.tasks.TaskMachineGroup
import com.vendistri.operations.features.tasks.TaskPickupLine
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionScopeResolverTest {
    @Test
    fun keepsWarehousePickupCompletionInCurrentVisit() {
        val pickup = task(
            id = "pickup",
            type = TaskType.MachinePickupInventory,
            status = TaskStatus.Done,
            refillTaskIds = listOf("refill")
        )
        val execution = execution(listOf(pickup), WorkDestinationKind.Warehouse)

        val scope = ExecutionScopeResolver.resolve(execution, allTasks = listOf(pickup))

        assertEquals(listOf("pickup"), scope.completedPickupTasks.map { it.id })
        assertEquals(setOf("refill"), scope.completedPickupRefillTaskIds)
    }

    @Test
    fun excludesCompletedPickupTasksFromPreviousWork() {
        val refill = task(id = "refill", type = TaskType.MachineRefill, status = TaskStatus.Pending)
        val pickup = task(
            id = "pickup",
            type = TaskType.MachinePickupInventory,
            status = TaskStatus.Done,
            refillTaskIds = listOf("refill")
        )
        val previous = task(id = "previous", status = TaskStatus.Done)
        val execution = execution(listOf(refill), WorkDestinationKind.Location)

        val scope = ExecutionScopeResolver.resolve(
            execution = execution,
            allTasks = listOf(refill, pickup, previous)
        )

        assertEquals(listOf("pickup"), scope.completedPickupTasks.map { it.id })
        assertEquals(listOf("previous"), scope.previousWorkCandidates.map { it.id })
    }

    @Test
    fun attachesCompletedPickupToLinkedMachineSection() {
        val refill = task(id = "refill", type = TaskType.MachineRefill, status = TaskStatus.Pending)
        val pickup = task(
            id = "pickup",
            type = TaskType.MachinePickupInventory,
            status = TaskStatus.Done,
            refillTaskIds = listOf("refill")
        )
        val execution = execution(listOf(refill), WorkDestinationKind.Location)

        val scope = ExecutionScopeResolver.resolve(
            execution = execution,
            allTasks = listOf(refill, pickup)
        )

        assertEquals(setOf("refill", "pickup"), scope.machineSections.first().childCards.map { it.task.id }.toSet())
    }

    @Test
    fun includesServiceBundleTasksInMachineSections() {
        val service = task(id = "service", type = TaskType.MachineService)
        val refill = task(id = "refill", type = TaskType.MachineRefill, serviceTaskId = "service")
        val execution = execution(listOf(service, refill), WorkDestinationKind.Location, currentTaskId = "refill")

        val scope = ExecutionScopeResolver.resolve(execution, allTasks = listOf(service, refill))

        assertEquals(1, scope.machineSections.size)
        assertEquals("service", scope.machineSections.first().serviceTask?.id)
        assertEquals(listOf("refill"), scope.machineSections.first().childCards.map { it.task.id })
    }

    @Test
    fun preservesExecutionMachineGroupOrder() {
        val machineOne = task(id = "machine-1-refill", type = TaskType.MachineRefill, machine = "machine-1", machineName = "Arcadia")
        val machineTwo = task(id = "machine-2-refill", type = TaskType.MachineRefill, machine = "machine-2", machineName = "City Tire")
        val machineThree = task(id = "machine-3-refill", type = TaskType.MachineRefill, machine = "machine-3", machineName = "Zebra")
        val execution = execution(
            listOf(machineOne, machineTwo, machineThree),
            WorkDestinationKind.Location,
            currentTaskId = "machine-2-refill"
        ).copy(
            machineGroups = TaskExecutionResolver.orderedMachineGroups(
                listOf(machineTwo, machineOne, machineThree)
            )
        )

        val scope = ExecutionScopeResolver.resolve(
            execution = execution,
            allTasks = listOf(machineOne, machineTwo, machineThree)
        )

        assertEquals(listOf("machine-2", "machine-1", "machine-3"), scope.machineSections.map { it.id })
    }

    @Test
    fun avoidsDoubleCountingPickupTaskIds() {
        val pickup = task(
            id = "pickup",
            type = TaskType.MachinePickupInventory,
            status = TaskStatus.Done,
            duration = 600.0,
            distance = 0.5,
            refillTaskIds = listOf("refill")
        )
        val execution = execution(listOf(pickup), WorkDestinationKind.Warehouse)

        val scope = ExecutionScopeResolver.resolve(execution, allTasks = listOf(pickup))

        assertEquals(10.0, scope.totalMetrics.durationMinutes, 0.001)
        assertEquals(0.5, scope.totalMetrics.distanceMiles, 0.001)
    }

    @Test
    fun computesAllTasksAreFinal() {
        val done = task(id = "done", status = TaskStatus.Done)
        val pending = task(id = "pending", status = TaskStatus.Pending)

        assertTrue(
            ExecutionScopeResolver.resolve(
                execution(listOf(done), WorkDestinationKind.Location),
                allTasks = listOf(done)
            ).allTasksAreFinal
        )
        assertFalse(
            ExecutionScopeResolver.resolve(
                execution(listOf(done, pending), WorkDestinationKind.Location),
                allTasks = listOf(done, pending)
            ).allTasksAreFinal
        )
    }

    private fun execution(
        tasks: List<VendiTask>,
        destinationKind: WorkDestinationKind,
        currentTaskId: String? = tasks.firstOrNull { it.status == TaskStatus.Pending }?.id
    ): ActiveTaskExecution {
        val machineGroups = TaskExecutionResolver.orderedMachineGroups(tasks)
        return ActiveTaskExecution(
            stopId = "stop-1",
            title = "Stop",
            locationId = tasks.firstOrNull()?.location,
            destinationKind = destinationKind,
            taskIds = tasks.map { it.id },
            wrapperTaskId = null,
            displayTasks = tasks,
            tasks = tasks.map {
                ExecutionTaskItem(
                    id = it.id,
                    type = it.type,
                    status = it.status,
                    machineId = it.machine,
                    machineName = it.machineName,
                    startedAt = it.startedAt,
                    doneAt = it.doneAt,
                    isWrapper = it.type == TaskType.MachineService
                )
            },
            machineGroups = machineGroups,
            currentTaskId = currentTaskId,
            currentTaskIndex = 0,
            totalTaskCount = tasks.size,
            gross = 0.0,
            refunds = 0.0,
            commission = 0.0,
            net = 0.0
        )
    }

    private fun task(
        id: String,
        type: TaskType = TaskType.MachineRefill,
        status: TaskStatus = TaskStatus.Pending,
        serviceTaskId: String? = null,
        refillTaskIds: List<String> = emptyList(),
        duration: Double? = null,
        distance: Double? = null,
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
            locationName = "Location",
            locationAddress = null,
            scheduledFor = "2026-07-03",
            createdAt = "2026-07-03T10:00:00Z",
            startedAt = "2026-07-03T11:00:00Z",
            doneAt = "2026-07-03T11:10:00Z",
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
            refillTaskIds = refillTaskIds,
            pickupLines = emptyList<TaskPickupLine>()
        )
    }
}
