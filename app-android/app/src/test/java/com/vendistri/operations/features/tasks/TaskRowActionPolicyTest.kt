package com.vendistri.operations.features.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRowActionPolicyTest {
    @Test
    fun canClaimOnlyForActionableUnassignedTasks() {
        assertTrue(TaskRowActionPolicy.canClaim(task(status = TaskStatus.Unassigned, assignee = null)))
        assertTrue(TaskRowActionPolicy.canClaim(task(status = TaskStatus.Pending, assignee = null)))
        assertFalse(TaskRowActionPolicy.canClaim(task(status = TaskStatus.Pending, assignee = "operator-1")))
        assertFalse(TaskRowActionPolicy.canClaim(task(status = TaskStatus.Done, assignee = null)))
    }

    @Test
    fun simpleDoneActionExcludesInventoryCompletionTasks() {
        assertTrue(TaskRowActionPolicy.canUseSimpleDoneAction(task(type = TaskType.MachineClean)))
        assertFalse(TaskRowActionPolicy.canUseSimpleDoneAction(task(type = TaskType.MachineRefill)))
        assertFalse(TaskRowActionPolicy.canUseSimpleDoneAction(task(type = TaskType.MachinePickupInventory)))
        assertFalse(TaskRowActionPolicy.canUseSimpleDoneAction(task(status = TaskStatus.Unassigned, assignee = null)))
    }

    @Test
    fun assigneeSummaryCollapsesSingleAssigneeAndShowsMixed() {
        assertEquals(
            "Assignee: Alex",
            TaskRowActionPolicy.assigneeSummary(
                listOf(
                    task(id = "task-1", assignee = "operator-1", assigneeName = "Alex"),
                    task(id = "task-2", assignee = "operator-1", assigneeName = "Alex")
                )
            )
        )
        assertEquals(
            "Mixed assignees",
            TaskRowActionPolicy.assigneeSummary(
                listOf(
                    task(id = "task-1", assignee = "operator-1", assigneeName = "Alex"),
                    task(id = "task-2", assignee = "operator-2", assigneeName = "Sam")
                )
            )
        )
    }

    private fun task(
        id: String = "task-1",
        type: TaskType = TaskType.MachineClean,
        status: TaskStatus = TaskStatus.Pending,
        assignee: String? = "operator-1",
        assigneeName: String? = null
    ): VendiTask {
        return VendiTask(
            id = id,
            type = type,
            status = status,
            isPublic = false,
            assignee = assignee,
            assigneeName = assigneeName,
            assigneeEmail = null,
            machine = "machine-1",
            machineName = "Machine",
            collectionInputMode = null,
            creditsPerDollar = null,
            location = "location-1",
            locationName = "Location",
            locationAddress = null,
            scheduledFor = "2026-07-03",
            createdAt = null,
            startedAt = null,
            doneAt = null,
            isLive = null,
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
            pickupLines = emptyList(),
            inventoryCompletion = null,
            inventorySourceMode = null,
            inventorySourceWarehouseId = null,
            inventorySourceWarehouseName = null,
            warehouseId = null,
            warehouseName = null,
            warehouseAddress = null
        )
    }
}
