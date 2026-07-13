package com.vendistri.operations.features.tasks

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedTaskNotesTest {
    @Test
    fun normalizedValueTrimsBlankNotesToNull() {
        assertEquals(null, SharedTaskNotes.normalizedValue("   \n "))
        assertEquals("Bring keys", SharedTaskNotes.normalizedValue("  Bring keys  "))
    }

    @Test
    fun seedDeduplicatesTrimmedLinesAcrossTasks() {
        val notes = SharedTaskNotes.seed(
            listOf(
                task(id = "task-1", notes = " Bring keys\nCheck coin mech "),
                task(id = "task-2", notes = "Check coin mech\nRestock labels"),
                task(id = "task-3", notes = null)
            )
        )

        assertEquals("Bring keys\nCheck coin mech\nRestock labels", notes)
    }

    private fun task(id: String, notes: String?): VendiTask {
        return VendiTask(
            id = id,
            type = TaskType.MachineRefill,
            status = TaskStatus.Pending,
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
            scheduledFor = "2026-07-06",
            createdAt = "2026-07-06T10:00:00Z",
            startedAt = null,
            doneAt = null,
            isLive = false,
            duration = null,
            notes = notes,
            distance = null,
            gross = null,
            grossCash = null,
            grossCard = null,
            refunds = null,
            commission = null,
            net = null,
            refillTaskId = null,
            refillTaskIds = emptyList(),
            pickupLines = emptyList()
        )
    }
}
