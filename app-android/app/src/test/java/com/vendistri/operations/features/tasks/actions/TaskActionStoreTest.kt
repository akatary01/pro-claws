package com.vendistri.operations.features.tasks.actions

import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.TasksStore
import com.vendistri.operations.features.tasks.VendiTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskActionStoreTest {
    @Test
    fun rescheduleStartsWithNoSelectedTasks() {
        val store = TaskActionStore(TasksStore())

        store.present(TaskActionKind.Reschedule, listOf(task(id = "service", type = TaskType.MachineService)))

        assertTrue(store.state.value.selectedTaskIds.isEmpty())
    }

    @Test
    fun cancelStartsWithActionableTasksSelected() {
        val store = TaskActionStore(TasksStore())

        store.present(TaskActionKind.Cancel, listOf(task(id = "service", type = TaskType.MachineService)))

        assertEquals(setOf("service"), store.state.value.selectedTaskIds)
    }

    @Test
    fun reassignSelectionUpdatesWholeServiceBundle() {
        val service = task(id = "service", type = TaskType.MachineService, assignee = "operator-1")
        val refill = task(
            id = "refill",
            type = TaskType.MachineRefill,
            assignee = "operator-1",
            serviceTaskId = "service"
        )
        val store = TaskActionStore(TasksStore())
        store.present(TaskActionKind.Reassign, listOf(service, refill))

        store.selectTaskAssignee("refill", "operator-2")

        assertEquals("operator-2", store.state.value.assigneeByTaskId["service"])
        assertEquals("operator-2", store.state.value.assigneeByTaskId["refill"])
        assertEquals(setOf("service", "refill"), store.state.value.reassignChangedTasks.map { it.id }.toSet())
    }

    private fun task(
        id: String,
        type: TaskType,
        status: TaskStatus = TaskStatus.Pending,
        assignee: String? = "operator-1",
        serviceTaskId: String? = null
    ): VendiTask {
        return VendiTask(
            id = id,
            type = type,
            status = status,
            isPublic = false,
            assignee = assignee,
            assigneeName = null,
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
            serviceTaskId = serviceTaskId,
            refillTaskId = null,
            refillTaskIds = emptyList(),
            pickupLines = emptyList()
        )
    }
}
