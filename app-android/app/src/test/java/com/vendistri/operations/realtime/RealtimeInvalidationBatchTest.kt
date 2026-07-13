package com.vendistri.operations.realtime

import com.vendistri.operations.features.notifications.RealtimeEventPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeInvalidationBatchTest {
    @Test
    fun fromBuildsTargetedTaskInvalidation() {
        val batch = RealtimeInvalidationBatch.from(
            listOf(
                event(type = "task.changed", taskId = "task-1", machineId = "machine-1", locationId = "location-1")
            )
        )

        assertTrue(batch.shouldReloadTasks)
        assertFalse(batch.requiresFullTaskReload)
        assertEquals(setOf("task-1"), batch.changedTaskIds)
        assertEquals(setOf("machine-1"), batch.changedMachineIds)
        assertEquals(setOf("location-1"), batch.changedLocationIds)
    }

    @Test
    fun fromSeparatesDeletedTaskIds() {
        val batch = RealtimeInvalidationBatch.from(
            listOf(
                event(type = "task.changed", taskId = "task-1"),
                event(type = "task.changed", taskId = "task-1", reason = "delete")
            )
        )

        assertTrue(batch.shouldReloadTasks)
        assertEquals(emptySet<String>(), batch.changedTaskIds)
        assertEquals(setOf("task-1"), batch.deletedTaskIds)
    }

    @Test
    fun fromRequiresFullReloadForUnknownEvents() {
        val batch = RealtimeInvalidationBatch.from(listOf(event(type = "unknown.changed")))

        assertTrue(batch.shouldReloadTasks)
        assertTrue(batch.requiresFullTaskReload)
    }

    @Test
    fun fromReloadsAuthTasksAndLocationsForOrganizationEvents() {
        val batch = RealtimeInvalidationBatch.from(listOf(event(type = "organization.changed")))

        assertTrue(batch.shouldReloadAuth)
        assertTrue(batch.shouldReloadTasks)
        assertTrue(batch.shouldReloadLocations)
    }

    private fun event(
        type: String,
        taskId: String? = null,
        machineId: String? = null,
        locationId: String? = null,
        reason: String? = null
    ): RealtimeEventPayload {
        return RealtimeEventPayload(
            type = type,
            id = null,
            taskId = taskId,
            machineId = machineId,
            locationVisitId = null,
            locationId = locationId,
            reason = reason
        )
    }
}
