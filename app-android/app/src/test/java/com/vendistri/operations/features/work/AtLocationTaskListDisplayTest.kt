package com.vendistri.operations.features.work

import com.vendistri.operations.features.tasks.CollectionInputMode
import com.vendistri.operations.features.tasks.TaskPickupLine
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask
import org.junit.Assert.assertEquals
import org.junit.Test

class AtLocationTaskListDisplayTest {
    @Test
    fun normalLocationDisplayKeepsFinalPickupInventoryOutOfMachineSections() {
        val refill = task("refill", TaskType.MachineRefill)
        val completedPickup = task("pickup", TaskType.MachinePickupInventory, TaskStatus.Done)
        val display = atLocationTaskListDisplay(
            machineSections = listOf(section("machine-1", refill, completedPickup)),
            primaryTaskIds = listOf("refill"),
            aggregatePickupInventoryCards = false
        )

        assertEquals(emptyList<String>(), display.aggregatePickupCards.map { it.task.id })
        assertEquals(listOf("refill"), display.machineSections.first().childCards.map { it.task.id })
    }

    @Test
    fun warehousePickupDisplayAggregatesPickupInventoryCards() {
        val refill = task("refill", TaskType.MachineRefill)
        val pickup = task("pickup", TaskType.MachinePickupInventory, TaskStatus.Pending)
        val display = atLocationTaskListDisplay(
            machineSections = listOf(section("machine-1", refill, pickup)),
            primaryTaskIds = listOf("pickup"),
            aggregatePickupInventoryCards = true
        )

        assertEquals(listOf("pickup"), display.aggregatePickupCards.map { it.task.id })
        assertEquals(listOf("refill"), display.machineSections.first().childCards.map { it.task.id })
    }

    private fun section(id: String, vararg tasks: VendiTask): ExecutionScopeMachineSection {
        val cards = tasks.map { task ->
            ExecutionScopeTaskCard(
                task = task,
                displayStatus = task.status,
                state = ExecutionScopeCardState.Locked,
                metrics = ExecutionScopeMetrics.Zero,
                isCurrent = false
            )
        }
        return ExecutionScopeMachineSection(
            id = id,
            name = "Machine",
            serviceTask = null,
            serviceDisplayStatus = null,
            serviceBadgeState = null,
            serviceCompletedChildCount = cards.count { it.displayStatus == TaskStatus.Done },
            serviceTotalChildCount = cards.size,
            serviceMetrics = null,
            childCards = cards,
            machineMetrics = ExecutionScopeMetrics.Zero,
            isActive = false
        )
    }

    private fun task(
        id: String,
        type: TaskType,
        status: TaskStatus = TaskStatus.Pending
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
            startedAt = null,
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
            serviceTaskId = null,
            refillTaskId = null,
            refillTaskIds = emptyList(),
            pickupLines = emptyList<TaskPickupLine>()
        )
    }
}
