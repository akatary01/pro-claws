package com.vendistri.operations.features.tasks.actions

import com.vendistri.operations.features.tasks.TaskStatus
import com.vendistri.operations.features.tasks.TaskType
import com.vendistri.operations.features.tasks.VendiTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskActionStateTest {
    @Test
    fun reassignDirtyStateOnlyIncludesChangedTasks() {
        val taskOne = task(id = "task-1", assignee = "operator-1")
        val taskTwo = task(id = "task-2", assignee = "operator-1")
        val state = TaskActionState(
            activeAction = TaskActionKind.Reassign,
            tasks = listOf(taskOne, taskTwo),
            assigneeByTaskId = mapOf("task-1" to "operator-1", "task-2" to "operator-2"),
            initialAssigneeByTaskId = mapOf("task-1" to "operator-1", "task-2" to "operator-1")
        )

        assertTrue(state.hasReassignChanges)
        assertEquals(listOf("task-2"), state.reassignChangedTasks.map { it.id })
    }

    @Test
    fun reassignDirtyStateIgnoresUnchangedAndCompletedTasks() {
        val unchanged = task(id = "task-1", assignee = "operator-1")
        val completed = task(id = "task-2", status = TaskStatus.Done, assignee = "operator-1")
        val state = TaskActionState(
            activeAction = TaskActionKind.Reassign,
            tasks = listOf(unchanged, completed),
            assigneeByTaskId = mapOf("task-1" to "operator-1", "task-2" to "operator-2"),
            initialAssigneeByTaskId = mapOf("task-1" to "operator-1", "task-2" to "operator-1")
        )

        assertFalse(state.hasReassignChanges)
        assertTrue(state.reassignChangedTasks.isEmpty())
    }

    private fun task(
        id: String,
        status: TaskStatus = TaskStatus.Pending,
        assignee: String?
    ): VendiTask {
        return VendiTask(
            id = id,
            type = TaskType.MachineClean,
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
            serviceTaskId = null,
            refillTaskId = null,
            refillTaskIds = emptyList(),
            pickupLines = emptyList()
        )
    }
}
