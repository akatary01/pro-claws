package com.vendistri.operations.features.work

import com.vendistri.operations.features.tasks.CollectionInputMode
import com.vendistri.operations.features.tasks.TaskPickupLine
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteStartScopeResolverTest {
    @Test
    fun decisionRequiresChoiceWhenStopHasAssignedAndClaimableWork() {
        val assigned = task(id = "assigned", machine = "machine-1", assignee = "user-1")
        val unassigned = task(
            id = "unassigned",
            machine = "machine-2",
            assignee = null,
            status = TaskStatus.Unassigned
        )

        val decision = RouteStartScopeResolver.decision(
            stop = stop(assigned, unassigned),
            selectedTask = assigned,
            allTasks = listOf(assigned, unassigned),
            currentUserId = "user-1"
        )

        requireNotNull(decision)
        assertTrue(decision.requiresChoice)
        assertTrue(decision.requiresConfirmation)
        assertEquals(RouteStartScopeChoice.SelectedMachine, decision.defaultChoice)
        assertEquals(setOf("assigned"), decision.selectedMachineOption.taskIds)
        assertEquals(setOf("assigned", "unassigned"), decision.fullStopOption.taskIds)
        assertEquals(setOf("unassigned"), decision.fullStopOption.claimTaskIds)
    }

    @Test
    fun decisionDefaultsToFullStopForOnlyAssignedWork() {
        val assigned = task(id = "assigned", assignee = "user-1")

        val decision = RouteStartScopeResolver.decision(
            stop = stop(assigned),
            selectedTask = assigned,
            allTasks = listOf(assigned),
            currentUserId = "user-1"
        )

        requireNotNull(decision)
        assertFalse(decision.requiresChoice)
        assertFalse(decision.requiresConfirmation)
        assertEquals(RouteStartScopeChoice.FullStop, decision.defaultChoice)
    }

    private fun stop(vararg tasks: VendiTask): GoStopPlan {
        return GoStopPlan(
            id = "location-1",
            targetLocationId = "location-1",
            title = "Downtown Office",
            addressStreetLine = null,
            addressCityStateZipLine = null,
            tasks = tasks.toList(),
            nodes = emptyList(),
            machineGroups = emptyList(),
            gross = 0.0,
            refunds = 0.0,
            commission = 0.0,
            net = 0.0
        )
    }

    private fun task(
        id: String,
        machine: String = "machine-1",
        assignee: String? = "user-1",
        status: TaskStatus = TaskStatus.Pending
    ): VendiTask {
        return VendiTask(
            id = id,
            type = TaskType.MachineRefill,
            status = status,
            isPublic = false,
            assignee = assignee,
            assigneeName = "Operator",
            assigneeEmail = "operator@vendistri.com",
            machine = machine,
            machineName = machine,
            collectionInputMode = CollectionInputMode.Dollars,
            creditsPerDollar = null,
            location = "location-1",
            locationName = "Downtown Office",
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
            refillTaskId = null,
            refillTaskIds = emptyList(),
            pickupLines = emptyList<TaskPickupLine>()
        )
    }
}
