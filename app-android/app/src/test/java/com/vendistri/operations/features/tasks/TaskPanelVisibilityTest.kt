package com.vendistri.operations.features.tasks

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskPanelVisibilityTest {
    @Test
    fun finalTasksForWeekIsStableForEveryDayInSameWeek() {
        val tasks = listOf(
            task(id = "monday", scheduledFor = "2026-06-29", gross = 100.0),
            task(id = "wednesday", scheduledFor = "2026-07-01", gross = 200.0),
            task(id = "sunday", scheduledFor = "2026-07-05", gross = 300.0),
            task(id = "next-week", scheduledFor = "2026-07-06", gross = 400.0)
        )

        val mondayWeekIds = TaskPanelVisibility.finalTasksForWeek(tasks, java.time.LocalDate.parse("2026-06-29")).map { it.id }
        val wednesdayWeekIds = TaskPanelVisibility.finalTasksForWeek(tasks, java.time.LocalDate.parse("2026-07-01")).map { it.id }
        val sundayWeekIds = TaskPanelVisibility.finalTasksForWeek(tasks, java.time.LocalDate.parse("2026-07-05")).map { it.id }

        assertEquals(listOf("monday", "wednesday", "sunday"), mondayWeekIds)
        assertEquals(mondayWeekIds, wednesdayWeekIds)
        assertEquals(mondayWeekIds, sundayWeekIds)
    }

    @Test
    fun finalTasksUseScheduledDateNotDoneAtDate() {
        val task = task(
            id = "late-finish",
            scheduledFor = "2026-07-05",
            doneAt = "2026-07-06T01:12:00Z"
        )

        assertEquals(listOf("late-finish"), TaskPanelVisibility.finalTasksForDate(listOf(task), java.time.LocalDate.parse("2026-07-05")).map { it.id })
        assertEquals(emptyList<String>(), TaskPanelVisibility.finalTasksForDate(listOf(task), java.time.LocalDate.parse("2026-07-06")).map { it.id })
        assertEquals(listOf("late-finish"), TaskPanelVisibility.finalTasksForWeek(listOf(task), java.time.LocalDate.parse("2026-06-29")).map { it.id })
        assertEquals(emptyList<String>(), TaskPanelVisibility.finalTasksForWeek(listOf(task), java.time.LocalDate.parse("2026-07-06")).map { it.id })
    }

    private fun task(
        id: String,
        scheduledFor: String,
        doneAt: String? = "${scheduledFor}T12:00:00Z",
        gross: Double = 10.0
    ): VendiTask {
        return VendiTask(
            id = id,
            type = TaskType.MachineCollection,
            status = TaskStatus.Done,
            isPublic = false,
            assignee = null,
            assigneeName = null,
            assigneeEmail = null,
            machine = "machine-$id",
            machineName = "Machine $id",
            collectionInputMode = null,
            creditsPerDollar = null,
            location = "location-$id",
            locationName = "Location $id",
            locationAddress = null,
            scheduledFor = scheduledFor,
            createdAt = null,
            startedAt = null,
            doneAt = doneAt,
            isLive = false,
            duration = 0.0,
            notes = null,
            distance = 0.0,
            gross = gross,
            grossCash = gross,
            grossCard = 0.0,
            refunds = 0.0,
            commission = 0.0,
            net = gross,
            refillTaskId = null,
            refillTaskIds = emptyList(),
            pickupLines = emptyList()
        )
    }
}
