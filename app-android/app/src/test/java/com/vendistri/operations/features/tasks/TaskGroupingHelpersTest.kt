package com.vendistri.operations.features.tasks

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskGroupingHelpersTest {
    @Test
    fun groupByMachineUsesSharedTaskSortWithinEachMachine() {
        val groups = TaskGroupingHelpers.groupByMachine(
            listOf(
                task(id = "clean", type = TaskType.MachineClean, machine = "machine-2", machineName = "Beta"),
                task(id = "collection", type = TaskType.MachineCollection, machine = "machine-1", machineName = "Alpha"),
                task(id = "service", type = TaskType.MachineService, machine = "machine-1", machineName = "Alpha"),
                task(id = "refill", type = TaskType.MachineRefill, machine = "machine-1", machineName = "Alpha")
            )
        )

        assertEquals(listOf("Alpha", "Beta"), groups.map { it.name })
        assertEquals(listOf("service", "collection", "refill"), groups.first().tasks.map { it.id })
    }

    @Test
    fun groupByLocationSortsUnassignedLocationsBeforePendingLocations() {
        val groups = TaskGroupingHelpers.groupByLocation(
            listOf(
                task(
                    id = "pending",
                    type = TaskType.MachineService,
                    status = TaskStatus.Pending,
                    location = "location-pending",
                    locationName = "Pending Location"
                ),
                task(
                    id = "unassigned",
                    type = TaskType.MachineService,
                    status = TaskStatus.Unassigned,
                    location = "location-unassigned",
                    locationName = "Unassigned Location"
                )
            )
        )

        assertEquals(listOf("Unassigned Location", "Pending Location"), groups.map { it.name })
    }

    @Test
    fun totalDurationTreatsTaskDurationAsSeconds() {
        val minutes = TaskGroupingHelpers.totalDurationMinutes(
            listOf(
                task(
                    id = "done",
                    type = TaskType.MachinePickupInventory,
                    machine = "machine-1",
                    machineName = "Machine",
                    status = TaskStatus.Done,
                    duration = 5_400.0
                )
            )
        )

        assertEquals(90.0, minutes, 0.0001)
    }

    private fun task(
        id: String,
        type: TaskType,
        machine: String,
        machineName: String,
        status: TaskStatus = TaskStatus.Pending,
        location: String = "location-1",
        locationName: String = "Location",
        duration: Double? = null
    ): VendiTask {
        return VendiTask(
            id = id,
            type = type,
            status = status,
            isPublic = false,
            assignee = "operator-1",
            assigneeName = "Operator",
            assigneeEmail = null,
            machine = machine,
            machineName = machineName,
            collectionInputMode = null,
            creditsPerDollar = null,
            location = location,
            locationName = locationName,
            locationAddress = null,
            scheduledFor = "2026-07-03",
            createdAt = null,
            startedAt = null,
            doneAt = null,
            isLive = null,
            duration = duration,
            notes = null,
            distance = null,
            gross = null,
            grossCash = null,
            grossCard = null,
            refunds = null,
            commission = null,
            net = null,
            serviceTaskId = null,
            refillTaskId = null,
            refillTaskIds = emptyList(),
            pickupLines = emptyList()
        )
    }

    private fun task(
        id: String,
        type: TaskType,
        status: TaskStatus,
        location: String,
        locationName: String
    ): VendiTask {
        return task(
            id = id,
            type = type,
            machine = "machine-$id",
            machineName = "Machine $id",
            status = status,
            location = location,
            locationName = locationName
        )
    }
}
