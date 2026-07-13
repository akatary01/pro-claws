package com.vendistri.operations.features.work

import com.vendistri.operations.features.tasks.CollectionInputMode
import com.vendistri.operations.features.tasks.TaskMachineGroup
import com.vendistri.operations.features.tasks.TaskPickupLine
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskExecutionResolverTest {
    @Test
    fun currentExecutableTaskPrefersStartedTaskInActiveMachine() {
        val tasks = listOf(
            task(id = "collection", type = TaskType.MachineCollection, startedAt = null),
            task(id = "refill", type = TaskType.MachineRefill, startedAt = "2026-07-03T11:00:00Z")
        )

        val current = TaskExecutionResolver.currentExecutableTask(tasks)

        assertEquals("refill", current?.id)
    }

    @Test
    fun currentExecutableTaskUsesExecutionPriorityFallback() {
        val tasks = listOf(
            task(id = "collection", type = TaskType.MachineCollection),
            task(id = "refill", type = TaskType.MachineRefill)
        )

        val current = TaskExecutionResolver.currentExecutableTask(tasks)

        assertEquals("refill", current?.id)
    }

    @Test
    fun hydratedTasksKeepsStopOrderWithLatestStatuses() {
        val stopTasks = listOf(
            task(id = "a", status = TaskStatus.Pending),
            task(id = "b", status = TaskStatus.Pending)
        )
        val allTasks = listOf(
            task(id = "b", status = TaskStatus.Done),
            task(id = "a", status = TaskStatus.Pending)
        )

        val hydrated = TaskExecutionResolver.hydratedTasks(stopTasks, allTasks)

        assertEquals(listOf("a", "b"), hydrated.map { it.id })
        assertEquals(TaskStatus.Done, hydrated[1].status)
    }

    @Test
    fun currentExecutableTaskSkipsServiceWrapperUntilChildTasksAreDone() {
        val tasks = listOf(
            task(id = "service", type = TaskType.MachineService),
            task(id = "refill", type = TaskType.MachineRefill, serviceTaskId = "service")
        )

        val current = TaskExecutionResolver.currentExecutableTask(tasks)
        val startTask = TaskExecutionResolver.startTask(tasks)

        assertEquals("refill", current?.id)
        assertEquals("service", startTask?.id)
    }

    @Test
    fun linkedPickupTasksMatchRefillLinks() {
        val refill = task(id = "refill", type = TaskType.MachineRefill)
        val pickup = task(
            id = "pickup",
            type = TaskType.MachinePickupInventory,
            refillTaskIds = listOf("refill")
        )

        val linked = TaskExecutionResolver.linkedPickupTasks(
            tasks = listOf(refill),
            allTasks = listOf(refill, pickup)
        )

        assertEquals(listOf("pickup"), linked.map { it.id })
    }

    @Test
    fun orderedMachineGroupsCanPromotePreferredMachine() {
        val machineOne = task(id = "machine-1-refill", machine = "machine-1", machineName = "Arcadia")
        val machineTwo = task(id = "machine-2-refill", machine = "machine-2", machineName = "City Tire")

        val groups = TaskExecutionResolver.orderedMachineGroups(
            tasks = listOf(machineOne, machineTwo),
            preferredMachineId = "machine-2"
        )

        assertEquals(listOf("machine-2", "machine-1"), groups.map { it.id })
    }

    @Test
    fun stableMachineGroupsPreservesPreviousGroupAndTaskOrder() {
        val machineOneRefill = task(id = "machine-1-refill", machine = "machine-1")
        val machineOneCollection = task(id = "machine-1-collection", type = TaskType.MachineCollection, machine = "machine-1")
        val machineTwoRefill = task(id = "machine-2-refill", machine = "machine-2")
        val previousGroups = listOf(
            TaskMachineGroup(
                id = "machine-2",
                name = "Machine",
                tasks = listOf(machineTwoRefill),
                durationMinutes = 0.0,
                distanceMiles = 0.0
            ),
            TaskMachineGroup(
                id = "machine-1",
                name = "Machine",
                tasks = listOf(machineOneCollection, machineOneRefill),
                durationMinutes = 0.0,
                distanceMiles = 0.0
            )
        )
        val nextGroups = TaskExecutionResolver.orderedMachineGroups(
            listOf(machineOneRefill, machineOneCollection, machineTwoRefill)
        )

        val stable = TaskExecutionResolver.stableMachineGroups(nextGroups, previousGroups)

        assertEquals(listOf("machine-2", "machine-1"), stable.map { it.id })
        assertEquals(listOf("machine-1-collection", "machine-1-refill"), stable.last().tasks.map { it.id })
    }

    private fun task(
        id: String,
        type: TaskType = TaskType.MachineRefill,
        status: TaskStatus = TaskStatus.Pending,
        startedAt: String? = null,
        serviceTaskId: String? = null,
        refillTaskIds: List<String> = emptyList(),
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
            duration = null,
            notes = null,
            distance = null,
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
