package com.vendistri.operations.features.work

import com.vendistri.operations.features.tasks.CollectionInputMode
import com.vendistri.operations.features.tasks.TaskPickupLine
import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask
import org.junit.Assert.assertEquals
import org.junit.Test

class PreviousWorkResolverTest {
    @Test
    fun includesSameDayLocationFinalTasksOnly() {
        val current = task(id = "current", status = TaskStatus.Pending)
        val previous = task(id = "previous", status = TaskStatus.Done, scheduledFor = "2026-07-03T18:30:00Z")
        val otherDay = task(id = "other-day", status = TaskStatus.Done, scheduledFor = "2026-07-04")
        val otherLocation = task(id = "other-location", status = TaskStatus.Done, location = "location-2")
        val pending = task(id = "pending", status = TaskStatus.Pending)
        val pickup = task(id = "pickup", type = TaskType.MachinePickupInventory, status = TaskStatus.Done)

        val resolved = PreviousWorkResolver.previousWork(
            currentTasks = listOf(current),
            allTasks = listOf(current, previous, otherDay, otherLocation, pending, pickup)
        )

        assertEquals(listOf("previous"), resolved.map { it.id })
    }

    @Test
    fun excludesActiveAndCompletedPickupTasks() {
        val current = task(id = "current", status = TaskStatus.Done)
        val pickup = task(id = "pickup", type = TaskType.MachinePickupInventory, status = TaskStatus.Done)
        val previous = task(id = "previous", status = TaskStatus.Done)

        val resolved = PreviousWorkResolver.previousWork(
            currentTasks = listOf(current),
            allTasks = listOf(current, pickup, previous),
            completedPickupTasks = listOf(pickup)
        )

        assertEquals(listOf("previous"), resolved.map { it.id })
    }

    @Test
    fun respectsAssigneeRulesForNormalUsers() {
        val current = task(id = "current", assignee = "user-1")
        val mine = task(id = "mine", status = TaskStatus.Done, assignee = "user-1")
        val theirs = task(id = "theirs", status = TaskStatus.Done, assignee = "user-2")

        val normalUser = PreviousWorkResolver.previousWork(
            currentTasks = listOf(current),
            allTasks = listOf(current, mine, theirs),
            scope = PreviousWorkScope(currentUserId = "user-1", canViewAllAssignees = false)
        )
        val owner = PreviousWorkResolver.previousWork(
            currentTasks = listOf(current),
            allTasks = listOf(current, mine, theirs),
            scope = PreviousWorkScope(currentUserId = "user-1", canViewAllAssignees = true)
        )

        assertEquals(listOf("mine"), normalUser.map { it.id })
        assertEquals(listOf("mine", "theirs"), owner.map { it.id })
    }

    private fun task(
        id: String,
        type: TaskType = TaskType.MachineRefill,
        status: TaskStatus = TaskStatus.Pending,
        location: String = "location-1",
        scheduledFor: String = "2026-07-03",
        assignee: String? = "user-1"
    ): VendiTask {
        return VendiTask(
            id = id,
            type = type,
            status = status,
            isPublic = false,
            assignee = assignee,
            assigneeName = "Operator",
            assigneeEmail = "operator@vendistri.com",
            machine = "machine-1",
            machineName = "Machine",
            collectionInputMode = CollectionInputMode.Dollars,
            creditsPerDollar = null,
            location = location,
            locationName = "Location",
            locationAddress = null,
            scheduledFor = scheduledFor,
            createdAt = "2026-07-03T10:00:00Z",
            startedAt = "2026-07-03T11:00:00Z",
            doneAt = "2026-07-03T11:10:00Z",
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
